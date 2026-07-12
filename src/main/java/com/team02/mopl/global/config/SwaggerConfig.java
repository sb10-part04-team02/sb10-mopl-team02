package com.team02.mopl.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "모두의 플리 API 문서",
            description = "모두의 플리 프로젝트의 Swagger API 문서입니다.",
            version = "1.0"))
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
public class SwaggerConfig {

  // SecurityConfig의 permitAll 대상과 일치해야 하는 공개 엔드포인트 목록
  private static final Set<String> PUBLIC_GET = Set.of("/api/auth/csrf-token");
  private static final Set<String> PUBLIC_POST =
      Set.of("/api/users", "/api/auth/sign-in", "/api/auth/sign-out", "/api/auth/refresh");

  /**
   * operation별로 보안 요구사항을 분리한다. 공개 엔드포인트는 {@code security: []}, 보호된 조회(GET)는 BearerAuth만, 변경 요청은 하나의
   * requirement object에 BearerAuth와 CsrfToken을 함께 지정한다.
   */
  // 공개 API -> 인증 필요 없음
  // 조회 (GET) -> BearerAuth만
  // 변경 (POST/PATCH/PUT/DELETE -> BearerAuth + CsrfToken
  @Bean
  public OpenApiCustomizer securityRequirementCustomizer() {
    return openApi ->
        openApi
            .getPaths()
            .forEach(
                (path, pathItem) ->
                    pathItem
                        .readOperationsMap()
                        .forEach(
                            (method, operation) -> {
                              boolean isPublic =
                                  (method == PathItem.HttpMethod.GET && PUBLIC_GET.contains(path))
                                      || (method == PathItem.HttpMethod.POST
                                          && PUBLIC_POST.contains(path));
                              if (isPublic) {
                                operation.setSecurity(List.of());
                              } else if (method == PathItem.HttpMethod.GET
                                  || method == PathItem.HttpMethod.HEAD
                                  || method == PathItem.HttpMethod.OPTIONS) {
                                operation.setSecurity(
                                    List.of(new SecurityRequirement().addList("BearerAuth")));
                              } else {
                                operation.setSecurity(
                                    List.of(
                                        new SecurityRequirement()
                                            .addList("BearerAuth")
                                            .addList("CsrfToken")));
                              }
                            }));
  }
}
