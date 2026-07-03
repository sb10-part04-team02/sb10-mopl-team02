package com.team02.mopl.domain.playlist.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.content.dto.ContentSummary;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlaylistMapperTest {

  private final PlaylistMapper playlistMapper = new PlaylistMapper();

  @Test
  @DisplayName("Playlist 엔티티를 PlaylistDto로 변환하면 기본 필드가 매핑되고 owner는 ownerId 스텁, contents는 빈 리스트가 된다")
  void toDto_mapsFieldsAndStubsOwnerAndContents() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Instant updatedAt = Instant.parse("2026-06-29T00:00:00Z");
    Playlist playlist = new Playlist(ownerId, "내 플리", "설명");
    ReflectionTestUtils.setField(playlist, "id", playlistId);
    ReflectionTestUtils.setField(playlist, "updatedAt", updatedAt);

    PlaylistDto dto = playlistMapper.toDto(playlist, true);

    assertThat(dto.id()).isEqualTo(playlistId);
    assertThat(dto.updatedAt()).isEqualTo(updatedAt);
    assertThat(dto.title()).isEqualTo("내 플리");
    assertThat(dto.description()).isEqualTo("설명");
    assertThat(dto.subscriberCount()).isEqualTo(0L);
    assertThat(dto.subscribedByMe()).isTrue();
    assertThat(dto.owner().userId()).isEqualTo(ownerId);
    assertThat(dto.owner().name()).isNull();
    assertThat(dto.owner().profileImageUrl()).isNull();
    assertThat(dto.contents()).isEmpty();
  }

  @Test
  @DisplayName("조회 전용 toDto는 전달받은 owner·contents·subscribedByMe를 그대로 조립한다")
  void toDto_assemblesGivenOwnerAndContents() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Instant updatedAt = Instant.parse("2026-06-29T00:00:00Z");
    Playlist playlist = new Playlist(ownerId, "내 플리", "설명");
    ReflectionTestUtils.setField(playlist, "id", playlistId);
    ReflectionTestUtils.setField(playlist, "updatedAt", updatedAt);

    UserSummary owner = new UserSummary(ownerId, "홍길동", "http://img/owner");
    ContentSummary content =
        new ContentSummary(
            UUID.randomUUID(),
            ContentType.MOVIE,
            "영화",
            "영화 설명",
            "http://img/movie",
            List.of("액션"),
            4.5,
            10);

    PlaylistDto dto = playlistMapper.toDto(playlist, owner, List.of(content), true);

    assertThat(dto.id()).isEqualTo(playlistId);
    assertThat(dto.owner()).isEqualTo(owner);
    assertThat(dto.contents()).containsExactly(content);
    assertThat(dto.subscribedByMe()).isTrue();
  }

  @Test
  @DisplayName("toUserSummary는 User의 id·이름·프로필 이미지를 매핑한다")
  void toUserSummary_mapsUserFields() {
    User user = new User("홍길동", "hong@test.com", "pw", "http://img/owner", Role.USER, false);
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    UserSummary summary = playlistMapper.toUserSummary(user);

    assertThat(summary.userId()).isEqualTo(userId);
    assertThat(summary.name()).isEqualTo("홍길동");
    assertThat(summary.profileImageUrl()).isEqualTo("http://img/owner");
  }

  @Test
  @DisplayName("toContentSummary는 Content와 태그 목록을 매핑하고, 태그가 null이면 빈 리스트로 조립한다")
  void toContentSummary_mapsContentAndTags() {
    Content content = new Content(ContentType.MOVIE, "영화", "영화 설명", "http://img/movie");
    UUID contentId = UUID.randomUUID();
    ReflectionTestUtils.setField(content, "id", contentId);
    ReflectionTestUtils.setField(content, "averageRating", 4.5);
    ReflectionTestUtils.setField(content, "reviewCount", 10);

    ContentSummary summary =
        playlistMapper.toContentSummary(content, List.of(new Tag(content, "액션")));

    assertThat(summary.id()).isEqualTo(contentId);
    assertThat(summary.type()).isEqualTo(ContentType.MOVIE);
    assertThat(summary.title()).isEqualTo("영화");
    assertThat(summary.tags()).containsExactly("액션");
    assertThat(summary.averageRating()).isEqualTo(4.5);
    assertThat(summary.reviewCount()).isEqualTo(10);

    assertThat(playlistMapper.toContentSummary(content, null).tags()).isEmpty();
  }
}
