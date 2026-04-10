package com.kapil.reddit.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis cache configuration.
 *
 * Serialization strategy:
 *   - JSON (not Java serialization) via GenericJackson2JsonRedisSerializer
 *   - ObjectMapper configured with:
 *       • JavaTimeModule         → correct Instant serialization (ISO-8601 strings)
 *       • NON_FINAL DefaultTyping → embeds @class type info so generics round-trip safely
 *       • SpringDataModule       → custom PageImpl deserializer (replaces broken PageImplMixin)
 *
 * WHY a custom PageImpl deserializer instead of a Jackson Mixin?
 *   Jackson's @JsonCreator mixin with @JsonProperty("pageable") Pageable ... forces
 *   Jackson to deserialize PageRequest from JSON. PageRequest has no @JsonCreator and
 *   no public no-arg constructor, so deserialization throws — even though serialization
 *   (write to Redis) works fine. Result: every cache READ returns 500.
 *
 *   The fix: a StdDeserializer<PageImpl> that reconstructs PageImpl directly from the
 *   primitive fields (content, pageNumber, pageSize, totalElements) without requiring
 *   PageRequest/Pageable to be Jackson-deserializable.
 *
 * Per-cache TTLs (overrides application.properties default):
 *   globalFeed → 5  min   homeFeed → 5 min
 *   postDetail → 10 min   comments → 2 min
 *
 * CRITICAL — this class must NOT expose any ObjectMapper as a @Bean.
 *   Spring Boot's JacksonAutoConfiguration is @ConditionalOnMissingBean(ObjectMapper.class).
 *   Exposing one causes Spring Boot to back off, making the Redis mapper (with NON_FINAL
 *   type info) the MVC HTTP serializer → @class leaks into all API responses.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    // ── Per-cache TTLs ────────────────────────────────────────────────────────
    private static final Duration GLOBAL_FEED_TTL = Duration.ofMinutes(5);
    private static final Duration HOME_FEED_TTL   = Duration.ofMinutes(5);
    private static final Duration POST_DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration COMMENTS_TTL    = Duration.ofMinutes(2);

    /**
     * Builds the Redis-only ObjectMapper.
     *
     * Must remain a private factory method — NOT a @Bean.
     * See class-level Javadoc for the critical reason.
     */
    private ObjectMapper buildRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Instant → ISO-8601 string (not epoch-millis array)
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Embed @class into every non-final type so Page<T>, List<T>, etc.
        // can be fully round-tripped through Redis as JSON.
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // Register the custom PageImpl deserializer.
        // This is the correct replacement for the broken PageImplMixin approach.
        mapper.registerModule(springDataModule());

        return mapper;
    }

    /**
     * Jackson module that teaches the deserializer how to reconstruct {@code PageImpl<T>}
     * from the JSON cached by Redis.
     *
     * <p>The mixin approach ({@code @JsonCreator} with a {@code Pageable} parameter) fails
     * at runtime because {@code PageRequest} (the concrete Pageable) has no public
     * no-arg constructor and no {@code @JsonCreator} — Jackson cannot instantiate it.
     * The result is a 500 on every cache READ even though the cache WRITE succeeded.
     *
     * <p>This deserializer instead reads the primitive scalar fields directly:
     * <ul>
     *   <li>{@code content}      — via the typed-array wrapper produced by NON_FINAL typing</li>
     *   <li>{@code pageNumber}   — from the nested {@code pageable} object</li>
     *   <li>{@code pageSize}     — from the nested {@code pageable} object</li>
     *   <li>{@code totalElements}— top-level long field</li>
     * </ul>
     * and reconstructs the page with {@code PageRequest.of(pageNumber, pageSize)}.
     */
    private SimpleModule springDataModule() {
        SimpleModule module = new SimpleModule("SpringDataModule");
        module.addDeserializer(PageImpl.class, new PageImplDeserializer());
        return module;
    }

    /**
     * Custom deserializer for {@code PageImpl<?>}.
     *
     * JSON layout written by GenericJackson2JsonRedisSerializer with NON_FINAL typing:
     * <pre>
     * {
     *   "@class": "org.springframework.data.domain.PageImpl",
     *   "content": ["java.util.ArrayList", [
     *       {"@class": "com.kapil.reddit.post.dto.PostResponse", ...},
     *       ...
     *   ]],
     *   "pageable": {
     *       "@class": "org.springframework.data.domain.PageRequest",
     *       "pageNumber": 0,
     *       "pageSize":   20,
     *       ...
     *   },
     *   "totalElements": 5,
     *   "totalPages": 1,
     *   ...
     * }
     * </pre>
     *
     * The content items carry their own {@code @class} so {@code mapper.treeToValue(item, Object.class)}
     * correctly reconstructs {@code PostResponse}, {@code CommentResponse}, etc. using
     * their Lombok {@code @Jacksonized} builders.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class PageImplDeserializer extends StdDeserializer<PageImpl> {

        PageImplDeserializer() {
            super(PageImpl.class);
        }

        @Override
        public PageImpl deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            ObjectMapper mapper = (ObjectMapper) p.getCodec();
            JsonNode root = mapper.readTree(p);

            // ── 1. content ──────────────────────────────────────────────────────
            // With NON_FINAL typing, List<T> is serialized as:
            //   ["java.util.ArrayList", [ {item1}, {item2}, ... ]]
            // We strip the wrapper array to get the actual items.
            List<Object> content = new ArrayList<>();
            JsonNode contentNode = root.get("content");
            if (contentNode != null && !contentNode.isNull()) {
                JsonNode items = unwrapTypedArray(contentNode);
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        // Each item has @class so treeToValue resolves the real type
                        content.add(mapper.treeToValue(item, Object.class));
                    }
                }
            }

            // ── 2. pageNumber + pageSize ────────────────────────────────────────
            // pageable is an object: { "@class": "...PageRequest", "pageNumber":0, "pageSize":20 }
            int pageNumber = 0;
            int pageSize   = 20;
            JsonNode pageableNode = root.get("pageable");
            if (pageableNode != null && pageableNode.isObject()) {
                if (pageableNode.has("pageNumber")) pageNumber = pageableNode.get("pageNumber").asInt();
                if (pageableNode.has("pageSize"))   pageSize   = pageableNode.get("pageSize").asInt();
            } else {
                // Fallback: Spring serialises number/size at top-level in some versions
                if (root.has("number")) pageNumber = root.get("number").asInt();
                if (root.has("size"))   pageSize   = root.get("size").asInt();
            }

            // ── 3. totalElements ────────────────────────────────────────────────
            long totalElements = 0L;
            JsonNode totalNode = root.get("totalElements");
            if (totalNode != null && !totalNode.isNull()) {
                totalElements = totalNode.asLong();
            }

            return new PageImpl<>(content, PageRequest.of(pageNumber, pageSize), totalElements);
        }

        /**
         * Strips the Jackson NON_FINAL typed-array wrapper from collection nodes.
         * <p>
         * With {@code JsonTypeInfo.As.PROPERTY}, scalar objects get an {@code @class}
         * property inside. But {@code List} (which is an array in JSON) cannot hold
         * an extra property, so Jackson wraps it as a 2-element array:
         * {@code ["java.util.ArrayList", [actual, items]]}
         *
         * @param node the raw {@code JsonNode} for the field
         * @return the inner array node, or the original node if it isn't wrapped
         */
        private JsonNode unwrapTypedArray(JsonNode node) {
            if (node.isArray()
                    && node.size() == 2
                    && node.get(0).isTextual()
                    && node.get(1).isArray()) {
                return node.get(1);   // actual element array
            }
            return node;              // already a plain array (no wrapper)
        }
    }

    // ── Spring bean plumbing ──────────────────────────────────────────────────

    /** JSON serializer for all Redis cache values. */
    @Bean
    public GenericJackson2JsonRedisSerializer redisSerializer() {
        return new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());
    }

    /**
     * Base cache configuration: JSON serialization, null values disabled.
     * Key prefix "reddit:" is applied via application.properties.
     */
    @Bean
    public RedisCacheConfiguration defaultRedisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(redisSerializer())
                );
    }

    /** Per-cache TTL overrides — each cache keeps its own expiry. */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        RedisCacheConfiguration base = defaultRedisCacheConfiguration();
        return builder -> builder
                .withCacheConfiguration("globalFeed", base.entryTtl(GLOBAL_FEED_TTL))
                .withCacheConfiguration("homeFeed",   base.entryTtl(HOME_FEED_TTL))
                .withCacheConfiguration("postDetail", base.entryTtl(POST_DETAIL_TTL))
                .withCacheConfiguration("comments",   base.entryTtl(COMMENTS_TTL));
    }
}
