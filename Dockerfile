# Build stage는 로컬에서 ./gradlew bootJar로 진행하므로 실행 스테이지만 정의합니다.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# JAR 파일 복사 (build/libs/*.jar)
COPY build/libs/*.jar app.jar

# 실행 환경 설정
ENTRYPOINT ["java", "-jar", "app.jar"]

# 포트 노출
EXPOSE 8080
