package com.team02.mopl.domain.content.ingestion.backfill;

import com.team02.mopl.domain.content.ingestion.tmdb.TmdbMediaType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackfillCursorRepository extends JpaRepository<BackfillCursor, TmdbMediaType> {}
