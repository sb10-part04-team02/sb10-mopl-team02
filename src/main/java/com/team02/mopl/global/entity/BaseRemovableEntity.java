package com.team02.mopl.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class BaseRemovableEntity extends BaseEntity {

  @Column(name = "deleted_at", nullable = true)
  private Instant deletedAt;

  public void delete() {
    this.deletedAt = Instant.now();
  }

  public void restore() {
    this.deletedAt = null;
  }

  public boolean isDeleted() {
    return this.deletedAt != null;
  }
}
