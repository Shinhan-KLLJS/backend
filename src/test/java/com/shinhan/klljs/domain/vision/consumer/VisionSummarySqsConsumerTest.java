package com.shinhan.klljs.domain.vision.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shinhan.klljs.domain.vision.VisionSummaryFixtures;
import com.shinhan.klljs.domain.vision.config.VisionSqsProperties;
import com.shinhan.klljs.domain.vision.service.VisionFanOutResult;
import com.shinhan.klljs.domain.vision.service.VisionSummaryIngestService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionSummarySqsConsumerTest {

    @Test
    void poll_deletesMessageWhenAllMediaResultsAreTerminal() {
        SqsClient sqsClient = mock(SqsClient.class);
        VisionSummaryIngestService ingestService = mock(VisionSummaryIngestService.class);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(response());
        when(ingestService.ingest(any(), any()))
                .thenReturn(new VisionFanOutResult(1, 1, 1, 1, 0));
        VisionSummarySqsConsumer consumer = consumer(sqsClient, ingestService);

        consumer.poll();

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void poll_doesNotDeleteMessageWhenAnyMediaHasRetryableFailure() {
        SqsClient sqsClient = mock(SqsClient.class);
        VisionSummaryIngestService ingestService = mock(VisionSummaryIngestService.class);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(response());
        when(ingestService.ingest(any(), any()))
                .thenReturn(new VisionFanOutResult(2, 0, 0, 0, 1));
        VisionSummarySqsConsumer consumer = consumer(sqsClient, ingestService);

        consumer.poll();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    private VisionSummarySqsConsumer consumer(
            SqsClient sqsClient,
            VisionSummaryIngestService ingestService
    ) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        VisionSqsProperties properties =
                new VisionSqsProperties("https://sqs.example.com/vision", "ap-northeast-2", true, 1, 10);
        return new VisionSummarySqsConsumer(sqsClient, mapper, ingestService, properties);
    }

    private ReceiveMessageResponse response() {
        Message message = Message.builder()
                .messageId("message-1")
                .receiptHandle("receipt-1")
                .body(VisionSummaryFixtures.RAW_BODY)
                .build();
        return ReceiveMessageResponse.builder().messages(message).build();
    }
}
