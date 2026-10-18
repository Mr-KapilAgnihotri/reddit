package com.kapil.reddit.post.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Published to the {@code post-created} topic; consumed by the Python ML service. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostCreatedEvent {
    private Long postId;
    private String text;
    private String type; // POST or COMMENT
    private String title;

    /** Backwards-compatible constructor (no title). */
    public PostCreatedEvent(Long postId, String text, String type) {
        this(postId, text, type, null);
    }
}
