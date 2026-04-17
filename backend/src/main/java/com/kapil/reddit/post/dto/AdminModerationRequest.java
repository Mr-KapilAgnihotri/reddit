package com.kapil.reddit.post.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for admin moderation override endpoints.
 * Both fields are optional — only non-null values are applied.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminModerationRequest {

    /** Override the ML moderation flag (true = mark unsafe, false = mark safe). */
    private Boolean isModerated;

    /** Optionally supply a replacement display text (manual censoring). */
    private String displayText;
}
