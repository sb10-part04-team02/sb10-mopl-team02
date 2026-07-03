package com.team02.mopl.domain.playlist.repository;

import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.enums.PlaylistSortBy;
import com.team02.mopl.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public interface PlaylistRepositoryCustom {

  // 커서 페이지네이션 조회 (정렬키/방향/커서 유무를 동적으로 처리, 논리 삭제 제외)
  // keyword가 있으면 제목·설명 부분일치, cursor가 null이면 첫 페이지로 동작한다
  List<Playlist> findPlaylistsByCursor(
      String keyword,
      PlaylistSortBy sortBy,
      SortDirection direction,
      Comparable<?> cursor,
      UUID idAfter,
      int limit);

  // keyword 필터를 적용한 활성 플레이리스트 총 개수
  long countActive(String keyword);
}
