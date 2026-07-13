package com.shinhan.klljs.domain.team.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

/**
 * 업로드 토큰을 서명·검증한다 (docs/team-creation-api-spec.md 5절).
 *
 * <pre>
 * v1.{base64url(payload)}.{base64url(hmacSha256(payload, secret))}
 * </pre>
 *
 * <h3>왜 TokenHasher를 재사용하지 않나</h3>
 * {@code TokenHasher.sha256()}은 단방향 해시다. 초대 코드처럼 "값을 저장해두고 나중에 대조"하는
 * 용도지, 여기처럼 <b>서버가 발급한 값인지 확인하고 내용을 다시 꺼내야 하는</b> 용도로는 쓸 수 없다.
 *
 * <h3>키는 JWT와 분리한다</h3>
 * 용도가 다른 키를 공유하면 한쪽이 유출됐을 때 다른 쪽까지 무너진다.
 * {@code BUSINESS_REGISTRATION_UPLOAD_TOKEN_SECRET}으로 따로 주입한다.
 */
@Slf4j
@Component
public class BusinessRegistrationUploadTokenSigner {

    /** 다른 용도로 발급된 토큰을 여기 들고 오는 것을 막는다. */
    static final String PURPOSE = "business-registration-upload";

    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TOKEN_PART_COUNT = 3;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final BusinessRegistrationUploadProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BusinessRegistrationUploadTokenSigner(
            BusinessRegistrationUploadProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // 스프링 MVC의 기본 ObjectMapper와 분리한다. 응답 직렬화 설정(필드 노출 규칙 등)이 바뀌어도
        // 토큰 payload의 JSON 모양은 흔들리면 안 된다 - 흔들리면 기존 토큰의 서명이 전부 깨진다.
        this.objectMapper = new ObjectMapper();
    }

    /** S3 업로드가 성공한 뒤에만 호출한다. 토큰의 존재 자체가 "그 객체가 있다"는 보증이 된다. */
    public String sign(String s3Key, Long uploaderId, String businessType, String businessItem) {
        BusinessRegistrationUploadToken token = new BusinessRegistrationUploadToken(
                PURPOSE,
                s3Key,
                uploaderId,
                clock.instant().getEpochSecond() + properties.tokenTtlSeconds(),
                businessType,
                businessItem);

        String payload = ENCODER.encodeToString(serialize(token));
        return VERSION + "." + payload + "." + ENCODER.encodeToString(hmac(payload));
    }

    /**
     * 5단계를 모두 통과해야 payload를 돌려준다 (문서 5절).
     * 버전/purpose -> 서명 -> 만료 -> 업로더 일치 -> S3 키 prefix.
     *
     * <b>어느 단계에서 실패했는지 구분해 알려주지 않는다.</b> 구분해 주면 공격자가 토큰을 조금씩
     * 바꿔가며 어느 필드가 문제인지 알아내는 판별기(validity oracle)가 된다.
     *
     * @param requesterId JWT sub. 토큰의 uploaderId와 다르면 거부한다
     * @throws GeneralException 하나라도 어긋나면 BUSINESS_400_001
     */
    public BusinessRegistrationUploadToken verify(String rawToken, Long requesterId) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken("토큰이 비어 있음");
        }

        String[] parts = rawToken.split("\\.");
        if (parts.length != TOKEN_PART_COUNT || !VERSION.equals(parts[0])) {
            throw invalidToken("토큰 형식/버전 불일치");
        }

        String payload = parts[1];
        // 서명부터 확인한다. 위조된 payload를 파싱하는 일 자체를 만들지 않기 위해서다.
        if (!isSignatureValid(payload, parts[2])) {
            throw invalidToken("서명 불일치");
        }

        BusinessRegistrationUploadToken token = deserialize(payload);

        if (!PURPOSE.equals(token.purpose())) {
            throw invalidToken("purpose 불일치: " + token.purpose());
        }
        if (token.expiresAtEpochSecond() <= clock.instant().getEpochSecond()) {
            throw invalidToken("만료됨");
        }
        // 다른 사람이 업로드한 사업자등록증으로 팀을 만드는 것을 막는다.
        if (requesterId == null || !requesterId.equals(token.uploaderId())) {
            throw invalidToken("업로더 불일치");
        }
        // 서버가 발급하지 않은 경로의 객체를 가리키는 토큰은 받지 않는다.
        if (token.s3Key() == null || !token.s3Key().startsWith(properties.keyPrefix())) {
            throw invalidToken("허용되지 않은 S3 키 prefix");
        }

        return token;
    }

    /**
     * <b>상수 시간 비교를 쓴다.</b> equals()는 첫 다른 바이트에서 바로 빠져나오므로, 응답 시간 차이로
     * 올바른 서명을 한 바이트씩 알아낼 수 있다.
     */
    private boolean isSignatureValid(String payload, String signature) {
        try {
            return MessageDigest.isEqual(hmac(payload), DECODER.decode(signature));
        } catch (IllegalArgumentException e) {
            // base64url이 아닌 문자열이 서명 자리에 들어온 경우.
            return false;
        }
    }

    private byte[] hmac(String payload) {
        if (!properties.hasTokenSecret()) {
            log.error("업로드 토큰 서명 키가 설정되지 않았습니다. "
                    + "app.upload.business-registration.token-secret(BUSINESS_REGISTRATION_UPLOAD_TOKEN_SECRET)을 확인하세요.");
            throw new GeneralException(BusinessRegistrationErrorCode.UPLOAD_TOKEN_SECRET_MISSING);
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.tokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA256은 표준 JDK 알고리즘이라 실제로는 도달하지 않는다.
            throw new IllegalStateException("HMAC 계산에 실패했습니다.", e);
        }
    }

    private byte[] serialize(BusinessRegistrationUploadToken token) {
        try {
            return objectMapper.writeValueAsBytes(token);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("업로드 토큰 직렬화에 실패했습니다.", e);
        }
    }

    private BusinessRegistrationUploadToken deserialize(String payload) {
        try {
            return objectMapper.readValue(DECODER.decode(payload), BusinessRegistrationUploadToken.class);
        } catch (Exception e) {
            // 서명이 유효한데 파싱이 안 된다면 우리가 발급 형식을 바꿔놓고 옛 토큰을 받은 경우다.
            throw invalidToken("payload 파싱 실패");
        }
    }

    /** 사용자에게는 사유를 구분해 주지 않고, 로그에만 남긴다. */
    private GeneralException invalidToken(String reason) {
        log.warn("업로드 토큰 검증 실패: {}", reason);
        return new GeneralException(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN);
    }
}
