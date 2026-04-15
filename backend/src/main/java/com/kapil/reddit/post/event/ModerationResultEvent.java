package com.kapil.reddit.post.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModerationResultEvent {
    private Long postId;
    private String maskedText;
    private boolean isModerated;
}