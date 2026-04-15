package com.kapil.reddit.post.event;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostCreatedEvent {
    private Long postId;
    private String text;
    private String type; // POST or COMMENT
}
