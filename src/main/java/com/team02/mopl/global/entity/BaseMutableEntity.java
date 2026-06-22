package com.team02.mopl.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

@Getter
@MappedSuperclass
public abstract class BaseMutableEntity extends BaseEntity {

  @LastModifiedDate
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Column(nullable = true)
  private LocalDateTime deletedAt;

  // 논리 삭제
  public void delete() {
    this.deletedAt = LocalDateTime.now();
  }

  public boolean isDeleted() {
    return this.deletedAt != null;
  }
}
