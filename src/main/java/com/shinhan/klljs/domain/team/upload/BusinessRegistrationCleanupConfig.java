package com.shinhan.klljs.domain.team.upload;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * orphan 정리 배치의 스케줄링을 켠다.
 *
 * <b>왜 별도 설정 클래스인가</b>: 프로젝트의 {@code @EnableScheduling}은 지금까지
 * {@code VisionSqsConfig} 안에만 있었고, 그 클래스는 Vision SQS consumer가 켜져 있을 때만
 * 활성화된다. 그대로 두면 SQS를 끈 환경에서는 스케줄링 인프라 자체가 없어서 이 배치가
 * <b>조용히 돌지 않는다</b>. 정리 배치는 SQS와 아무 상관이 없으므로 자기 조건으로 켠다.
 *
 * ({@code @EnableScheduling}이 두 곳에 있어도 문제 없다 - 스프링이 스케줄링 처리기를 한 번만 등록한다.)
 *
 * 기본값은 꺼짐이다. 테스트나 로컬 기동이 실제 S3 버킷을 훑고 객체를 지우는 일이 없어야 한다.
 * 운영(application-prod.yml)에서만 명시적으로 켠다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.upload.business-registration.cleanup", name = "enabled", havingValue = "true")
public class BusinessRegistrationCleanupConfig {
}
