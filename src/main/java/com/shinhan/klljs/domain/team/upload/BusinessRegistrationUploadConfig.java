package com.shinhan.klljs.domain.team.upload;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

/**
 * 사업자등록증 업로드에 쓰는 AWS 클라이언트 (VisionSqsConfig와 같은 패턴).
 *
 * 자격증명은 기본 체인을 그대로 쓴다 - 로컬은 aws configure/환경변수, 운영(EC2)은 인스턴스 프로파일.
 *
 * <b>@ConditionalOnProperty를 걸지 않는다.</b> VisionSqsConfig는 켜지는 순간 실제 큐를 폴링하기
 * 시작하므로 기본 비활성이 필요하지만, 이 클라이언트들은 <b>빈을 만든다고 해서 AWS를 호출하지 않는다</b>
 * (요청이 들어와야 호출한다). 대신 버킷/함수 이름이 비어 있으면 호출 시점에 막는다.
 */
@Configuration
@RequiredArgsConstructor
public class BusinessRegistrationUploadConfig {

    /**
     * S3 putObject 상한. 10MB 이하 파일 업로드에 넉넉한 값이다.
     * Lambda처럼 타임아웃을 걸지 않으면 네트워크 장애 시 SDK 기본값까지 톰캣 스레드가 묶인다.
     */
    private static final Duration S3_CALL_TIMEOUT = Duration.ofSeconds(30);

    private final BusinessRegistrationUploadProperties properties;

    @Bean
    public S3Client businessRegistrationS3Client() {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(S3_CALL_TIMEOUT)
                        .apiCallAttemptTimeout(S3_CALL_TIMEOUT)
                        .build())
                .build();
    }

    /**
     * OCR Lambda 동기 invoke 전용.
     *
     * <b>타임아웃을 반드시 건다.</b> Lambda가 응답하지 않으면 SDK 기본값(분 단위)까지 톰캣 스레드가
     * 그대로 묶인다. OCR이 느려도 업로드 응답이 무한정 늦어지면 안 된다 - 시간이 지나면 OCR 없이
     * (모든 필드 null로) 응답하고, 사용자가 값을 직접 입력하게 한다.
     */
    @Bean
    public LambdaClient businessRegistrationOcrLambdaClient() {
        Duration timeout = Duration.ofMillis(properties.ocrTimeoutMs());
        return LambdaClient.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(timeout)
                        .apiCallAttemptTimeout(timeout)
                        .build())
                .build();
    }
}
