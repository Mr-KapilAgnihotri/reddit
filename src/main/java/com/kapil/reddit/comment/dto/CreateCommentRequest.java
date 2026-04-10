package com.kapil.reddit.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCommentRequest {

    @NotNull(message = "postId is required")
    private Long postId;

    /** Null for root-level comments; non-null for replies. */
    private Long parentCommentId;

    @NotBlank(message = "Comment text must not be blank")
    private String text;
}
