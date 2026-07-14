package com.shinhan.klljs.domain.team.upload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.Map;

/**
 * OCR Lambda를 동기 invoke한다 (OCR 레포: Shinhan-KLLJS/biz-eligibility).
 *
 * <h3>API Gateway를 거치지 않는다</h3>
 * API GW는 동기 호출에 29초 제한이 있고, 인증(JWT authorizer)과 CORS를 따로 관리해야 하며,
 * 인증 없이 열려 있으면 누구나 OCR 비용을 태울 수 있다. SDK로 직접 부르면 이 문제가 전부 없어진다.
 * 그래서 Lambda도 proxy 봉투를 만들지 않고 결과 dict를 그대로 돌려준다.
 *
 * <h3>파일 바이트를 다시 보내지 않는다</h3>
 * S3 버킷/키만 넘기고 Lambda가 자기 IAM 권한으로 직접 읽는다. 동기 invoke 페이로드에는 6MB 제한이
 * 있어 큰 이미지를 실어 보내면 걸린다.
 *
 * <h3>실패해도 업로드는 성공이다</h3>
 * OCR은 사용자 입력을 도와주는 편의 기능일 뿐이다. Lambda가 죽든 응답이 이상하든
 * {@link OcrResult#empty()}를 돌려주고, 사용자가 값을 직접 입력하게 한다
 * (docs/team-creation-api-spec.md 8절). 원인은 로그에만 남긴다.
 */
@Slf4j
@Component
public class BusinessRegistrationOcrClient {

    /** Lambda가 요구하는 동작 지정. 없으면 S3 이벤트 트리거 오설정으로 보고 거부한다. */
    private static final String ACTION_OCR = "ocr";

    private final LambdaClient lambdaClient;
    private final BusinessRegistrationUploadProperties properties;
    private final ObjectMapper objectMapper;

    public BusinessRegistrationOcrClient(
            @Qualifier("businessRegistrationOcrLambdaClient") LambdaClient lambdaClient,
            BusinessRegistrationUploadProperties properties) {
        this.lambdaClient = lambdaClient;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    /** 실패하지 않는다. 어떤 이유로든 OCR을 못 하면 모든 필드가 null인 결과를 돌려준다. */
    public OcrResult extract(String s3Key) {
        if (!properties.hasOcrFunction() || !properties.hasBucket()) {
            log.warn("OCR Lambda가 설정되지 않아 건너뜁니다. key={}", s3Key);
            return OcrResult.empty();
        }

        try {
            InvokeResponse response = lambdaClient.invoke(InvokeRequest.builder()
                    .functionName(properties.ocrFunctionName())
                    .payload(SdkBytes.fromUtf8String(requestPayload(s3Key)))
                    .build());

            return parse(response, s3Key);
        } catch (Exception e) {
            // 타임아웃, 권한 없음, 함수 없음 등. 사용자 흐름을 막지 않는다.
            log.error("OCR Lambda 호출 실패: key={}", s3Key, e);
            return OcrResult.empty();
        }
    }

    private String requestPayload(String s3Key) throws com.fasterxml.jackson.core.JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "action", ACTION_OCR,
                "bucket", properties.bucket(),
                "key", s3Key));
    }

    /**
     * Lambda는 실패해도 예외를 던지지 않고 {@code {"error", "message"}}를 돌려준다.
     * 성공이면 응답 계약 6개 필드가 최상위에 온다.
     */
    private OcrResult parse(InvokeResponse response, String s3Key) throws Exception {
        // 함수가 처리하지 못한 예외로 죽은 경우 (Lambda 런타임이 표시해준다).
        if (response.functionError() != null) {
            log.error("OCR Lambda가 예외로 종료했습니다: key={}, error={}, payload={}",
                    s3Key, response.functionError(), response.payload().asUtf8String());
            return OcrResult.empty();
        }

        JsonNode body = objectMapper.readTree(response.payload().asByteArray());

        if (body.hasNonNull("error")) {
            log.error("OCR 실패: key={}, error={}, message={}",
                    s3Key, body.path("error").asText(), body.path("message").asText());
            return OcrResult.empty();
        }

        return objectMapper.treeToValue(body, OcrResult.class);
    }
}
