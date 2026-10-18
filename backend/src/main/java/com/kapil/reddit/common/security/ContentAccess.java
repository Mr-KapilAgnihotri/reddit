package com.kapil.reddit.common.security;

import com.kapil.reddit.community.domain.Community;
import com.kapil.reddit.community.repository.CommunityModeratorRepository;
import com.kapil.reddit.community.repository.CommunityRepository;
import com.kapil.reddit.user.domain.User;
import com.kapil.reddit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Single source of truth for "who may remove a post or comment".
 *
 * <p>Allowed: the content's author, the owner or a moderator of the community it was posted in,
 * or a global ADMIN. The same rule drives both the DELETE endpoints and the {@code canDelete}
 * flags returned to the UI, so the UI never shows a button the API would reject.
 */
@Component
@RequiredArgsConstructor
public class ContentAccess {

    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final CommunityModeratorRepository communityModeratorRepository;

    /** The permissions of one requesting user, resolved once per request. */
    public record Viewer(Long userId, boolean admin, Set<Long> moderatedCommunityIds) {

        public static final Viewer ANONYMOUS = new Viewer(null, false, Set.of());

        /**
         * @param authorId    author of the post/comment (may be null for deleted authors)
         * @param communityId community the content lives in, or null for profile posts
         */
        public boolean canRemove(Long authorId, Long communityId) {
            if (userId == null) return false;
            if (admin) return true;
            if (authorId != null && authorId.equals(userId)) return true;
            return communityId != null && moderatedCommunityIds.contains(communityId);
        }
    }

    public Viewer viewer(String email) {
        if (email == null) return Viewer.ANONYMOUS;
        return userRepository.findByEmail(email).map(this::viewer).orElse(Viewer.ANONYMOUS);
    }

    public Viewer viewer(User user) {
        boolean admin = user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()));

        Set<Long> moderated = new HashSet<>();
        communityModeratorRepository.findByUserId(user.getId())
                .forEach(m -> moderated.add(m.getId().getCommunityId()));
        // Owners always moderate their community (older rows may lack a moderator entry).
        communityRepository.findByCreatedByIdAndIsDeletedFalse(user.getId())
                .stream().map(Community::getId).forEach(moderated::add);

        return new Viewer(user.getId(), admin, moderated);
    }
}
