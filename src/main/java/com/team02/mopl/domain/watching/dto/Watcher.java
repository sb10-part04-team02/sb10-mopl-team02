package com.team02.mopl.domain.watching.dto;

import com.team02.mopl.domain.user.entity.User;
import java.util.UUID;

public record Watcher(UUID id, String name, String email, String profileImageUrl) {

  public static Watcher from(User user) {
    return new Watcher(user.getId(), user.getName(), user.getEmail(), user.getProfileImageUrl());
  }
}
