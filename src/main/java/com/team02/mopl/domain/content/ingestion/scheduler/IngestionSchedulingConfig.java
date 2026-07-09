package com.team02.mopl.domain.content.ingestion.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 수집 스케줄러 활성화 설정. enabled=false(기본값)면 스케줄링 인프라 자체가 뜨지 않아
// 테스트/로컬 컨텍스트에서 @Scheduled가 발화하지 않는다
@Configuration
@EnableScheduling
@EnableConfigurationProperties(IngestionSchedulerProperties.class)
@ConditionalOnProperty(name = "app.ingestion.scheduler.enabled", havingValue = "true")
public class IngestionSchedulingConfig {}
