package com.team02.mopl.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
// JPA 어노테이션
// 이 클래스가 직접 테이블과 매핑되지 않고, 자식 클래스들이 이 필드들을 상속받아 자신의 테이블에 포함시킬 수 있게 함
// @MappedSuperclass가 없으면 부모 클래스의 필드가 자식 엔티티 테이블에 반영되지 않음
// 이 클래스 자체로는 EntityManager로 조회하거나 JPQL 쿼리 대상이 될 수 없음
@MappedSuperclass
// JPA의 Entity Event Listener 를 등록하는 어노테이션
// Spring Data JPA가 제공하는 리스너로, 엔티티의 생명주기 이벤트(저장, 수정)를 감지해 자동으로 값을 채워줌
// @CreatedDate, @LastModifiedDate 등이 동작하려면 반드시 이 리스너가 필요함
// 설정 클래스에 @EnableJpaAuditing 이 선언되어 있어야 함
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // 엔티티가 최초 저장(INSERT)될 때 현재 시각을 자동 주입
  // AuditingEntityListener 가 @PrePersist 시점에 값을 채워준다
  @CreatedDate
  // 이 컬럼은 UPDATE 쿼리에서 제외 - 한 번 저장된 이후 절대 변경 불가
  @Column(updatable = false)
  private LocalDateTime createdAt;

  // 엔티티가 저장(INSERT) 또는 수정(UPDATE)될 때마다 현재 시각을 자동 갱신
  // AuditingEntityListener 가 @PrePersist/@PreUpdate 시점에 값을 채워준다
  @LastModifiedDate
  private LocalDateTime updatedAt;
}
