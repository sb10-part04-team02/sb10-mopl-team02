package com.team02.mopl.domain.dm.entity;

import static jakarta.persistence.FetchType.LAZY;

import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

  @ManyToOne(fetch = LAZY)
  @JoinColumns({
      @JoinColumn(
          name = "conversation_id",
          referencedColumnName = "conversation_id",
          insertable = false,
          updatable = false
      ),
      @JoinColumn(
          name = "sender_id",
          referencedColumnName = "member_id",
          insertable = false,
          updatable = false
      )
  })
  private ConversationMember sender;

  @ManyToOne(fetch = LAZY)
  @JoinColumns({
      @JoinColumn(
          name = "conversation_id",
          referencedColumnName = "conversation_id",
          insertable = false,
          updatable = false
      ),
      @JoinColumn(
          name = "receiver_id",
          referencedColumnName = "member_id",
          insertable = false,
          updatable = false
      )
  })
  private ConversationMember receiver;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

}
