package com.team02.mopl.domain.dm.entity;

import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.global.entity.BaseMutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "conversation_members")
@Entity
public class ConversationMember extends BaseMutableEntity {

  @Builder
  private ConversationMember(Conversation conversation, User user, Instant lastReadAt) {
    this.conversation = Objects.requireNonNull(conversation);
    this.user = Objects.requireNonNull(user);
    this.lastReadAt = Objects.requireNonNull(lastReadAt);
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", columnDefinition = "uuid")
  private Conversation conversation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", columnDefinition = "uuid")
  private User user;

  @Column(name = "last_read_at", nullable = false)
  private Instant lastReadAt;
}
