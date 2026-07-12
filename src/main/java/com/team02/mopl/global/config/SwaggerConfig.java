package com.team02.mopl.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "모두의 플리 API 문서",
            description = "모두의 플리 프로젝트의 Swagger API 문서입니다.",
            version = "1.0"),
    security = {
      @SecurityRequirement(name = "BearerAuth"),
      @SecurityRequirement(name = "CsrfToken")
    })
@SecuritySchemes({
  @SecurityScheme(
      name = "BearerAuth",
      type = SecuritySchemeType.HTTP,
      scheme = "bearer",
      bearerFormat = "JWT",
      description = "JWT 액세스 토큰 (로그인 후 발급)"),
  @SecurityScheme(
      name = "CsrfToken",
      type = SecuritySchemeType.APIKEY,
      in = SecuritySchemeIn.HEADER,
      paramName = "X-XSRF-TOKEN",
      description = "CSRF 토큰 (GET /api/auth/csrf-token 호출 후 XSRF-TOKEN 쿠키 값)")
})
public class SwaggerConfig {}
