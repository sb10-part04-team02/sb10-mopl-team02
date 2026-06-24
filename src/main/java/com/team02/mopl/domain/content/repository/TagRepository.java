package com.team02.mopl.domain.content.repository;

import com.team02.mopl.domain.content.entity.Tag;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, UUID> {}
