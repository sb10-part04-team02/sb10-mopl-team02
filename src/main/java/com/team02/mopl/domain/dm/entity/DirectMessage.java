package com.team02.mopl.domain.dm.entity;

import static jakarta.persistence.FetchType.LAZY;

import com.team02.mopl.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "direct_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectMessage extends BaseEntity {

  @ManyToOne(fetch = LAZY, optional = false)
  @JoinColumn(name = "conversation_id", columnDefinition = "uuid")
  private Conversation conversation;

  @ManyToOne(fetch = LAZY, optional = false)
  @JoinColumn(name = "sender_id", nullable = false)
  private ConversationMember sender;

  @ManyToOne(fetch = LAZY, optional = false)
  @JoinColumn(name = "receiver_id", nullable = false)
  private ConversationMember receiver;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;
}
