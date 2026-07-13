package com.team02.mopl.global.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 로컬 디스크에 저장된 업로드 파일을 baseUrl(예: /files) 경로로 서빙
@Configuration
// 로컬 정적 서빙은 local 스토리지 모드에서만 필요 (s3 모드에서는 CloudFront가 서빙)
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class WebConfig implements WebMvcConfigurer {

  private final String basePath;
  private final String baseUrl;

  public WebConfig(
      @Value("${app.storage.local.base-path:storage}") String basePath,
      @Value("${app.storage.local.base-url:/files}") String baseUrl) {
    this.basePath = basePath;
    this.baseUrl = baseUrl;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    Path dir = Paths.get(basePath).toAbsolutePath().normalize();
    String location = dir.toUri().toString(); // file:///.../storage/
    if (!location.endsWith("/")) {
      location += "/"; // Spring이 리소스 location을 디렉터리로 해석하려면 반드시 /로 끝나야 함
    }
    // 예: /files/** -> file:///.../storage/
    registry.addResourceHandler(baseUrl + "/**").addResourceLocations(location);
  }
}
