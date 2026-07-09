package com.shinhan.klljs.domain.vision.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.klljs.domain.vision.config.VisionSqsProperties;
import com.shinhan.klljs.domain.vision.dto.VisionSummaryMessage;
import com.shinhan.klljs.domain.vision.service.VisionSummaryIngestService;
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
 * 스펙 5-1절 "권장 구현 로직"의 백엔드 SQS Consumer.
 * long polling(waitTimeSeconds)으로 대기하는 동안은 API 호출이 아니라 커넥션만 열려있는
 * 상태라, fixedDelay를 짧게 잡아도(폴링이 끝나자마자 바로 다음 폴링 시작) 실제로는
 * long polling 대기시간만큼만 텀이 생긴다.
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

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .maxNumberOfMessages(properties.pollMaxMessages())
                .waitTimeSeconds(properties.pollWaitTimeSec())
                .build());

        for (Message message : response.messages()) {
            handle(message);
        }
    }

    private void handle(Message message) {
        try {
            VisionSummaryMessage parsed = visionMessageObjectMapper.readValue(message.body(), VisionSummaryMessage.class);
            boolean saved = ingestService.ingest(parsed, message.body());
            log.info("Vision summary 처리 완료: device={}, board={}, seq={}, eventTime={}, {}",
                    parsed.deviceId(), parsed.boardId(), parsed.seq(), parsed.timestamp(),
                    saved ? "저장" : "중복(이미 처리됨)");
            deleteMessage(message);
        } catch (VisionSummaryIngestService.MediaUnitNotFoundException e) {
            // 재시도해도 해결 안 되는 오류 - 삭제하지 않고 둔다. 큐의 redrive policy에 따라
            // 일정 횟수 재시도 후 DLQ로 자동 이동한다.
            log.warn("매체 매핑 실패, 메시지를 삭제하지 않고 DLQ 재시도 경로로 넘김: messageId={}, {}",
                    message.messageId(), e.getMessage());
        } catch (Exception e) {
            // JSON 파싱 실패 등 - 마찬가지로 삭제하지 않는다.
            log.error("Vision summary 메시지 처리 실패, 메시지를 삭제하지 않고 DLQ 재시도 경로로 넘김: messageId={}",
                    message.messageId(), e);
        }
    }

    private void deleteMessage(Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
