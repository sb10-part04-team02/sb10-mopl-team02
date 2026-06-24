package com.team02.mopl.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합 테스트(@SpringBootTest) 베이스 클래스.
 *
 * <p>전체 컨텍스트를 띄우는 테스트는 이 클래스를 상속해 공통 설정(Testcontainers, test 프로파일)을 공유한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {}
