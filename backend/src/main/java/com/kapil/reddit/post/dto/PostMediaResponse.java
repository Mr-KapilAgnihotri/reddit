package com.kapil.reddit.post.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class PostMediaResponse {
    private String mediaUrl;

    private String mediaType;

    private String caption;
}
