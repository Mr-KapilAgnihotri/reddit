package com.kapil.reddit.post.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Event consumed from the {@code post-embedding} Kafka topic.
 * Published by the Python ML service after generating a 384-dimensional
 * text embedding for a post using the all-MiniLM-L6-v2 SentenceTransformer model.
 */
@Getter
@Setter
@NoArgsConstructor
public class EmbeddingEvent {
    private Long postId;
    private List<Float> embedding;
}
