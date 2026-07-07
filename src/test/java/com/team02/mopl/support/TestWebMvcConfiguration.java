package com.team02.mopl.support;

import com.team02.mopl.global.util.StringToEnumConverterFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@TestConfiguration
public class TestWebMvcConfiguration implements WebMvcConfigurer {

  @Autowired private StringToEnumConverterFactory stringToEnumConverterFactory;

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverterFactory(stringToEnumConverterFactory);
  }
}
