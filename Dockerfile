# 런타임 전용 이미지
# 무거운 Gradle 빌드는 CI(러너) 네이티브에서 수행하고, 여기서는 완성된 bootJar만 복사한다.
# 로컬에서 이미지를 빌드하려면 먼저 JAR을 만들어야 한다:
#   ./gradlew clean bootJar && docker build -t mopl-app .
FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app

# 비루트 유저 생성 및 전환
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

# CI에서 미리 빌드한 실행 가능한 bootJar를 복사 (plain jar는 제외)
COPY --chown=appuser:appgroup build/libs/*-SNAPSHOT.jar app.jar

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# TODO: JVM Configuration (프리티어 고려) -> 배포 시 고려하기
# ENV JVM_OPTS="-Xmx256m -Xms128m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC"

ENTRYPOINT ["java", "-jar", "app.jar"]
