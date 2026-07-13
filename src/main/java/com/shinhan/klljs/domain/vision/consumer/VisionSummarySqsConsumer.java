package com.shinhan.klljs.domain.vision.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.klljs.domain.vision.config.VisionSqsProperties;
import com.shinhan.klljs.domain.vision.dto.VisionSummaryMessage;
import com.shinhan.klljs.domain.vision.service.VisionFanOutResult;
import com.shinhan.klljs.domain.vision.service.VisionSummaryIngestService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * AWS SQS long polling으로 Vision 메시지를 받고 fan-out 결과에 따라 ACK를 결정한다.
 *
 * <p>DeleteMessage가 ACK다. 메시지 파싱·매체 매핑 오류 또는 매체별 재시도 가능 실패가 있으면
 * 삭제하지 않아 visibility timeout 뒤 다시 받거나 redrive policy에 따라 DLQ로 이동하게 한다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.vision.sqs", name = "consumer-enabled", havingValue = "true")
@RequiredArgsConstructor
public class VisionSummarySqsConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper visionMessageObjectMapper;
    private final VisionSummaryIngestService ingestService;
    private final VisionSqsProperties properties;

    @PostConstruct
    void logStartup() {
        log.info(
                "Vision SQS consumer started: queueUrl={}, region={}, pollWaitTimeSec={}, pollMaxMessages={}",
                properties.queueUrl(),
                properties.region(),
                properties.pollWaitTimeSec(),
                properties.pollMaxMessages()
        );
    }

    /** long polling이 끝난 뒤 1초 후 다음 poll을 시작한다. */
    @Scheduled(fixedDelay = 1000)
    public void poll() {
        ReceiveMessageResponse response;
        try {
            response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(properties.queueUrl())
                    .maxNumberOfMessages(properties.pollMaxMessages())
                    .waitTimeSeconds(properties.pollWaitTimeSec())
                    .build());
        } catch (Exception exception) {
            log.error(
                    "Vision SQS polling failed: queueUrl={}, region={}",
                    properties.queueUrl(),
                    properties.region(),
                    exception
            );
            return;
        }

        if (!response.messages().isEmpty()) {
            log.info("Vision SQS messages received: count={}", response.messages().size());
        }
        for (Message message : response.messages()) {
            handle(message);
        }
    }

    private void handle(Message message) {
        try {
            VisionSummaryMessage parsed =
                    visionMessageObjectMapper.readValue(message.body(), VisionSummaryMessage.class);
            VisionFanOutResult result = ingestService.ingest(parsed, message.body());

            log.info(
                    "Vision summary fan-out 완료: device={}, board={}, seq={}, eventTime={}, "
                            + "mediaCount={}, saved={}, duplicate={}, noCampaign={}, ambiguous={}, retryableFailure={}",
                    parsed.deviceId(),
                    parsed.boardId(),
                    parsed.seq(),
                    parsed.timestamp(),
                    result.processedMediaCount(),
                    result.savedCount(),
                    result.duplicateCount(),
                    result.noCampaignCount(),
                    result.ambiguousCampaignCount(),
                    result.retryableFailureCount()
            );

            if (result.shouldAck()) {
                deleteMessage(message);
            } else {
                log.warn(
                        "Vision summary 일부 매체 저장 실패로 미ACK: messageId={}, retryableFailureCount={}",
                        message.messageId(),
                        result.retryableFailureCount()
                );
            }
        } catch (VisionSummaryIngestService.MediaUnitNotFoundException exception) {
            // 설정 오류는 반복 처리해도 저장할 수 없으므로 삭제하지 않고 DLQ 경로로 보낸다.
            log.warn(
                    "매체 매핑 실패, 메시지를 삭제하지 않고 DLQ 재시도 경로로 넘김: messageId={}, {}",
                    message.messageId(),
                    exception.getMessage()
            );
        } catch (Exception exception) {
            // JSON 파싱 실패, 필수 중첩 객체 누락, 예상하지 못한 처리 오류도 ACK하지 않는다.
            log.error(
                    "Vision summary 메시지 처리 실패, 메시지를 삭제하지 않고 DLQ 재시도 경로로 넘김: messageId={}",
                    message.messageId(),
                    exception
            );
        }
    }

    private void deleteMessage(Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
