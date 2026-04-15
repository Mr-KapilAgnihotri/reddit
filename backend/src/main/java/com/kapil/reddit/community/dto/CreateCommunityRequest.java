package com.kapil.reddit.community.dto;

import lombok.Data;

@Data
public class CreateCommunityRequest {

    private String name;
    private String description;
    private Boolean isPrivate;
}
