package com.kapil.reddit.community.repository;

import com.kapil.reddit.community.domain.Community;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.*;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    Optional<Community> findByNameAndIsDeletedFalse(String name);

    boolean existsByName(String name);

    List<Community> findByCreatedByIdAndIsDeletedFalse(Long userId);
    List<Community> findByIsDeletedFalse();
    List<Community> findByNameContainingIgnoreCaseAndIsDeletedFalse(String name);
}
