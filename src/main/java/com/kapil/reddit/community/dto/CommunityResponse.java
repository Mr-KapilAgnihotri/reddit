package com.kapil.reddit.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityResponse {

    private Long id;

    private String name;

    private String description;

    private String createdBy;

    private Boolean isPrivate;

    private Instant createdAt;

    private Long memberCount;
}