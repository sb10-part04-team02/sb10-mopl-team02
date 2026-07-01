package com.team02.mopl.support;

import com.team02.mopl.global.config.JpaAuditingConfig;
import com.team02.mopl.global.config.QueryDslConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 리포지토리 슬라이스 테스트(@DataJpaTest) 베이스 클래스.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)}로 임베디드 DB 대체를 끄고 Testcontainers를 사용한다.
 * {@code @DataJpaTest}는 {@code @Configuration}을 스캔하지 않으므로 auditing 동작을 위해 {@link
 * JpaAuditingConfig}를, QueryDSL 커스텀 리포지토리의 {@code JPAQueryFactory} 빈을 위해 {@link QueryDslConfig}를 함께
 * Import한다. 기본 트랜잭션 롤백이 적용되므로 자식 테스트에 별도 {@code @Transactional}은 불필요하다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, QueryDslConfig.class})
public abstract class RepositoryTestSupport {}
