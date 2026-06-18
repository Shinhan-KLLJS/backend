# 1단계: 빌드 환경 (Java 21 및 Gradle 활용)
FROM gradle:9.0 AS builder

# 컨테이너 내부 작업의 기본 디렉토리를 /apps 로 설정
WORKDIR /apps

# 빌드 효율성을 위해 의존성 캐싱 레이어 구성
# 소스 코드 전체를 복사하기 전에, build.gradle만 먼저 복사하고 빌드를 시도함.
# 이렇게 하면 코드가 바뀌더라도 라이브러리가 추가되지 않았다면 다운로드 과정을 생략함 -> 빌드속도 향상
COPY build.gradle settings.gradle ./
RUN gradle build -x test --no-daemon || true

# 소스 코드 복사 및 실제 빌드 수행
COPY src ./src
RUN gradle clean bootJar -x test --no-daemon


# 2단계: 실행 환경 (최소한의 가벼운 JRE 이미지 사용)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /apps

# 빌드 단계에서 생성된 jar 파일만 가볍게 복사
COPY --from=builder /apps/build/libs/*-SNAPSHOT.jar app.jar

# 포트 지정
EXPOSE 8080

# 애플리케이션 실행 명령어
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]