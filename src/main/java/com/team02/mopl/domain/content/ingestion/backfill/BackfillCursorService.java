package com.team02.mopl.domain.content.ingestion.backfill;

import com.team02.mopl.domain.content.ingestion.tmdb.TmdbMediaType;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// discover backfill 커서의 조회/전진을 담당한다.
// - 배치 스텝은 no-op(ResourcelessTransactionManager)이라 이 서비스의 @Transactional이 커밋 경계를 만든다
//   (ContentUpsertService와 동일한 패턴)
@Service
@RequiredArgsConstructor
public class BackfillCursorService {

  private final BackfillCursorRepository repository;

  // 매체의 현재 커서 상태. 행이 없으면 시작일을 뜻하는 기본값을 돌려준다
  @Transactional(readOnly = true)
  public CursorState read(TmdbMediaType mediaType) {
    return repository
        .findById(mediaType)
        .map(c -> new CursorState(c.getCursorDate(), c.isBackfillComplete()))
        .orElse(new CursorState(null, false));
  }

  // 이번 실행이 내려간 지점으로 커서를 전진시킨다. 행이 없으면 생성한다.
  @Transactional
  public void advance(TmdbMediaType mediaType, LocalDate nextCursorDate, boolean backfillComplete) {
    BackfillCursor cursor =
        repository.findById(mediaType).orElseGet(() -> new BackfillCursor(mediaType));
    cursor.advance(nextCursorDate, backfillComplete);
    repository.save(cursor);
  }

  // 커서 조회 스냅샷. cursorDate가 null이면 아직 시작 전(오늘부터).
  public record CursorState(LocalDate cursorDate, boolean backfillComplete) {}
}
