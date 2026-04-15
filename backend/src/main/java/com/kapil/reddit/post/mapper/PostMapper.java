package com.kapil.reddit.post.mapper;

import com.kapil.reddit.post.domain.Post;
import com.kapil.reddit.post.domain.PostMedia;
import com.kapil.reddit.post.dto.PostMediaResponse;
import com.kapil.reddit.post.dto.PostResponse;

import java.util.List;

public class PostMapper {

    public static PostResponse toResponse(Post post, List<PostMedia> mediaList, Short userVote) {

        List<PostMediaResponse> media = mediaList != null ? mediaList.stream()
                .map(m -> PostMediaResponse.builder()
                        .mediaUrl(m.getMediaUrl())
                        .mediaType(m.getMediaType())
                        .caption(m.getCaption())
                        .build())
                .toList() : List.of();

        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .text(post.getDisplayText())
                .author(post.getAuthor().getUsername())
                .community(
                        post.getCommunity() != null
                                ? post.getCommunity().getName()
                                : null
                )
                .score(post.getScore())
                .upvotes(post.getUpvotes())
                .downvotes(post.getDownvotes())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .media(media)
                .userVote(userVote != null ? userVote : 0)
                .build();
    }
}