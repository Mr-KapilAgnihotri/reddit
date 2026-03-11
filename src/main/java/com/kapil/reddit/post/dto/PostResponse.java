package com.kapil.reddit.post.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class PostResponse {

    private Long id;

    private String title;

    private String text;

    private String author;

    private String community;

    private Long score;

    private Long upvotes;

    private Long downvotes;

    private Long commentCount;

    private Instant createdAt;

    private List<PostMediaResponse> media;
}
