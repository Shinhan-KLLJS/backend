package com.shinhan.klljs.domain.vision.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * app.vision.sqs.consumer-enabled=true일 때만 활성화된다 (기본값 false).
 * 이 조건이 없으면 테스트 실행이나 아무 설정 없는 로컬 기동에서도 스프링 컨텍스트가 뜰 때마다
 * 실제 운영 SQS 큐에 연결을 시도하게 된다. 스케줄링 인프라는 캠페인 상태 보정 설정에서도
 * 활성화되지만, 이 조건 덕분에 consumer Bean과 AWS client는 큐 설정이 있을 때만 만들어진다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.vision.sqs", name = "consumer-enabled", havingValue = "true")
@RequiredArgsConstructor
public class VisionSqsConfig {

    private final VisionSqsProperties properties;

    @Bean
    public SqsClient sqsClient() {
        // 자격증명은 기본 체인 사용: 로컬은 aws configure/환경변수, 운영(EC2)은 인스턴스 프로파일.
        return SqsClient.builder()
                .region(Region.of(properties.region()))
                .build();
    }

    /**
     * SQS 메시지 파싱 전용 ObjectMapper. 스프링 MVC 응답 직렬화에 쓰는 기본 ObjectMapper와
     * 분리해서, 여기서만 엄격하게(알 수 없는 필드가 오면 예외) 검증한다 - 원래 가이드가 권장했던
     * JSON Schema 검증(vision-summary-schema.json) 대신, 별도 라이브러리 없이 Jackson deserialization
     * 자체를 필드명 오타 방지용 검증으로 쓰는 것이다.
     */
    @Bean
    public ObjectMapper visionMessageObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }
}
