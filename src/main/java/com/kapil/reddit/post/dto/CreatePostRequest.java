package com.kapil.reddit.post.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreatePostRequest {

    private Long communityId;

    private String title;

    private String text;

    private List<MediaRequest> media;
}