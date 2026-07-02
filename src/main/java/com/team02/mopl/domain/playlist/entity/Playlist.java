package com.team02.mopl.domain.playlist.entity;

import com.team02.mopl.global.entity.BaseMutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "playlists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Playlist extends BaseMutableEntity {

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(nullable = false, length = 255)
  private String description;

  @Column(name = "subscriber_count", nullable = false)
  private long subscriberCount = 0L;

  @OneToMany(mappedBy = "playlist", fetch = FetchType.LAZY)
  private List<PlaylistContent> playlistContents = new ArrayList<>();

  public Playlist(UUID ownerId, String title, String description) {
    this.ownerId = Objects.requireNonNull(ownerId, "ownerId는 null일 수 없습니다.");
    this.title = Objects.requireNonNull(title, "title은 null일 수 없습니다.");
    this.description = Objects.requireNonNull(description, "description은 null일 수 없습니다.");
  }

  public void update(String title, String description) {
    if (StringUtils.hasText(title)) {
      this.title = title;
    }
    if (StringUtils.hasText(description)) {
      this.description = description;
    }
  }

  public void increaseSubscriberCount() {
    this.subscriberCount++;
  }

  public void decreaseSubscriberCount() {
    if (this.subscriberCount > 0) {
      this.subscriberCount--;
    }
  }
}
