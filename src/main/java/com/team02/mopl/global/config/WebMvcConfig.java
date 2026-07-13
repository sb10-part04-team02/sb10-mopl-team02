package com.team02.mopl.global.config;

import com.team02.mopl.global.util.StringToEnumConverterFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 스토리지 타입과 무관하게 항상 필요한 웹 MVC 설정.
// enum 컨버터는 요청 파라미터의 camelCase 값(예: tvSeries, createdAt)을 enum으로 변환하므로
// local/s3 어느 모드에서도 등록돼야 한다. (정적 파일 서빙은 local 전용이라 WebConfig로 분리)
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Override
  public void addFormatters(FormatterRegistry registry) {
    // Enum Converter 추가
    registry.addConverterFactory(new StringToEnumConverterFactory());
  }
}
