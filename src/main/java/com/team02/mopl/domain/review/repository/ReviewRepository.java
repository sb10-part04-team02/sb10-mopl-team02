package com.team02.mopl.domain.review.repository;

import com.team02.mopl.domain.review.entity.Review;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

  boolean existsByAuthorIdAndContentIdAndDeletedAtIsNull(UUID authorId, UUID contentId);
}
