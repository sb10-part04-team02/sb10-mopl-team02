# ── Build Stage ──────────────────────────────────────────────────────────────
FROM gradle:8-jdk17 AS build

WORKDIR /app

# 의존성 레이어 캐시: 빌드 스크립트만 먼저 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true

# 소스 복사 후 빌드
COPY src ./src
RUN gradle clean build -x test --no-daemon

# ── Runtime Stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app

# 비루트 유저 생성 및 전환
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

COPY --from=build /app/build/libs/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
