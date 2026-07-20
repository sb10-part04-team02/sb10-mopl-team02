package com.team02.mopl.domain.content.ingestion.backfill;

import com.team02.mopl.domain.content.ingestion.tmdb.TmdbMediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// discover backfill의 매체별 진행 커서. PK는 매체(MOVIE/TV) 자체
// - cursorDate: 다음 실행의 개봉일 상한(lte). null이면 아직 시작 전(오늘부터)
// - backfillComplete: floor-date까지 내려가 더 수집할 과거가 없는 상태
@Entity
@Table(name = "ingestion_backfill_cursor")
@Getter
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BackfillCursor {

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "media_type", length = 16)
  private TmdbMediaType mediaType;

  @Column(name = "cursor_date")
  private LocalDate cursorDate;

  @Column(nullable = false)
  private boolean backfillComplete;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public BackfillCursor(TmdbMediaType mediaType) {
    this.mediaType = mediaType;
    this.cursorDate = null;
    this.backfillComplete = false;
  }

  // 이번 실행이 내려간 지점으로 커서를 전진시킨다.
  public void advance(LocalDate nextCursorDate, boolean backfillComplete) {
    this.cursorDate = nextCursorDate;
    this.backfillComplete = backfillComplete;
  }
}
