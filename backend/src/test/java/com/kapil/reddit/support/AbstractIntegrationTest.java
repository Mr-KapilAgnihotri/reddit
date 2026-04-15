package com.kapil.reddit.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for all Spring Boot integration tests.
 *
 * <p>Provides:
 * <ul>
 *   <li>A PostgreSQL Testcontainer for a real database.</li>
 *   <li>A Redis Testcontainer for caching.</li>
 *   <li>An in-process EmbeddedKafka broker so Kafka-dependent beans
 *       (KafkaTemplate, @KafkaListener, KafkaConfig) resolve without a
 *       real broker and without Docker networking.</li>
 * </ul>
 *
 * <p>All three infrastructure addresses are wired via {@code @DynamicPropertySource}
 * so subclasses never need to configure anything manually.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(
        partitions = 1,
        topics = {"post-created", "post-moderated"},
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:${spring.embedded.kafka.brokers.property:0}",
                "log.dir=/tmp/kafka-test-logs"
        }
)
public abstract class AbstractIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"));

    @Container
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerInfrastructure(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> String.valueOf(REDIS.getMappedPort(6379)));

        // EmbeddedKafka — the broker address is injected via the
        // spring.embedded.kafka.brokers system property set by @EmbeddedKafka.
        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers", "localhost:9092"));
    }
}
