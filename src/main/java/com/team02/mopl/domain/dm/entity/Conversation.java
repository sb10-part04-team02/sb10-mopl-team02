package com.team02.mopl.domain.dm.entity;

import com.team02.mopl.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Table(name = "conversations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Conversation extends BaseEntity {}
