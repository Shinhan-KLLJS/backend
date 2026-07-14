package com.shinhan.klljs.domain.campaign.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 캠페인 날짜 상태 보정 스케줄러를 활성화한다.
 *
 * <p>Vision SQS 설정은 큐 URL이 있을 때만 활성화되므로 그 설정의 {@code @EnableScheduling}에
 * 의존하면 SQS를 사용하지 않는 환경에서 캠페인 상태가 멈춘다. 캠페인 스케줄링은 별도 설정으로
 * 항상 활성화해 두 기능의 생명주기를 분리한다.</p>
 */
@Configuration
@EnableScheduling
public class CampaignSchedulingConfig {
}
