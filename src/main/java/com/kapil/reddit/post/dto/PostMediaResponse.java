package com.kapil.reddit.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostMediaResponse {
    private String mediaUrl;

    private String mediaType;

    private String caption;
}
