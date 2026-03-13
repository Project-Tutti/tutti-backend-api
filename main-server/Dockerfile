# ══════════════════════════════════════════════════════════════
# Tutti Main Server — Dockerfile (Multi-stage Build)
# ══════════════════════════════════════════════════════════════

# ── Stage 1: Build ──
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Gradle wrapper & 의존성 캐싱 레이어
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 소스 코드 복사 & 빌드
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 보안: non-root 사용자
RUN addgroup -S tutti && adduser -S tutti -G tutti
USER tutti

# JAR 복사
COPY --from=builder /app/build/libs/tutti-main-server.jar app.jar

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
