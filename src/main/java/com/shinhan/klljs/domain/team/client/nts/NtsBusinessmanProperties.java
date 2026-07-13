package com.shinhan.klljs.domain.team.client.nts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * application.yml의 app.nts.* 값을 주입받는다 (VisionSqsProperties와 같은 패턴).
 * 국세청 사업자등록정보 진위확인·상태조회 API(공공데이터포털 odcloud) 설정이다.
 */
@Component
public class NtsBusinessmanProperties {

    /** 예외 메시지에 실려 나가는 {@code ...?serviceKey=xxx} 형태를 통째로 지우기 위한 패턴. */
    private static final Pattern SERVICE_KEY_PARAM = Pattern.compile("(?i)servicekey=[^&\\s\"']*");

    private static final String MASKED = "serviceKey=***";

    private final String baseUrl;
    private final String serviceKey;
    private final String encodedServiceKey;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public NtsBusinessmanProperties(
            @Value("${app.nts.base-url}") String baseUrl,
            @Value("${app.nts.service-key}") String serviceKey,
            @Value("${app.nts.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${app.nts.read-timeout-ms}") int readTimeoutMs
    ) {
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
        this.encodedServiceKey = encode(serviceKey);
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * 공공데이터포털은 이미 URL 인코딩된 서비스 키를 발급하는 경우가 있다.
     * 키에 '%'가 포함되어 있으면 인코딩된 키로 보고 그대로 쓰고, 없을 때만 인코딩한다.
     * <b>이중 인코딩하면 인증에 실패한다.</b>
     */
    private static String encode(String rawServiceKey) {
        if (rawServiceKey == null || rawServiceKey.isBlank()) {
            return "";
        }
        if (rawServiceKey.contains("%")) {
            return rawServiceKey;
        }
        return URLEncoder.encode(rawServiceKey, StandardCharsets.UTF_8);
    }

    /**
     * 예외 메시지·로그에 섞여 들어간 서비스 키를 지운다 (§2.3).
     * requests/RestClient 계열은 예외 메시지에 요청 URL을 그대로 넣는데, 거기에 serviceKey가 실려 있다.
     * 쿼리 파라미터 형태와 키 원문·인코딩본을 모두 지워야 우회 경로로 새어 나가지 않는다.
     */
    public String maskSecrets(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String masked = SERVICE_KEY_PARAM.matcher(message).replaceAll(MASKED);
        if (!serviceKey.isBlank()) {
            masked = masked.replace(serviceKey, "***");
        }
        if (!encodedServiceKey.isBlank()) {
            masked = masked.replace(encodedServiceKey, "***");
        }
        return masked;
    }

    /** 서비스 키가 설정되지 않았으면 국세청 호출 자체를 시도하지 않는다. */
    public boolean hasServiceKey() {
        return !serviceKey.isBlank();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String encodedServiceKey() {
        return encodedServiceKey;
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int readTimeoutMs() {
        return readTimeoutMs;
    }
}
