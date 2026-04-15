package com.kapil.reddit.post.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MediaRequest {

    private String mediaUrl;
    private String mediaType;
    private String caption;
}