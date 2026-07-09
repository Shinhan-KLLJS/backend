package com.shinhan.klljs.domain.vision.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * application.yml의 app.vision.sqs.* 값을 그대로 주입받는다 (AppAuthProperties와 같은 패턴).
 * consumerEnabled 기본값은 application.yml에서 false로 고정되어 있고,
 * application-prod.yml처럼 명시적으로 값을 준 프로필에서만 true가 될 수 있다.
 */
@Component
public class VisionSqsProperties {

    private final String queueUrl;
    private final String region;
    private final boolean consumerEnabled;
    private final int pollWaitTimeSec;
    private final int pollMaxMessages;

    public VisionSqsProperties(
            @Value("${app.vision.sqs.queue-url}") String queueUrl,
            @Value("${app.vision.sqs.region}") String region,
            @Value("${app.vision.sqs.consumer-enabled}") boolean consumerEnabled,
            @Value("${app.vision.sqs.poll-wait-time-sec}") int pollWaitTimeSec,
            @Value("${app.vision.sqs.poll-max-messages}") int pollMaxMessages
    ) {
        this.queueUrl = queueUrl;
        this.region = region;
        this.consumerEnabled = consumerEnabled;
        this.pollWaitTimeSec = pollWaitTimeSec;
        this.pollMaxMessages = pollMaxMessages;
    }

    public String queueUrl() {
        return queueUrl;
    }

    public String region() {
        return region;
    }

    public boolean consumerEnabled() {
        return consumerEnabled;
    }

    public int pollWaitTimeSec() {
        return pollWaitTimeSec;
    }

    public int pollMaxMessages() {
        return pollMaxMessages;
    }
}
