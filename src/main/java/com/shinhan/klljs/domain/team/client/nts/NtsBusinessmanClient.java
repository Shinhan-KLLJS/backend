package com.shinhan.klljs.domain.team.client.nts;

import com.shinhan.klljs.domain.team.client.nts.dto.NtsBusinessStatus;
import com.shinhan.klljs.domain.team.client.nts.dto.NtsStatusRequest;
import com.shinhan.klljs.domain.team.client.nts.dto.NtsStatusResponse;
import com.shinhan.klljs.domain.team.client.nts.dto.NtsValidateRequest;
import com.shinhan.klljs.domain.team.client.nts.dto.NtsValidateResponse;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.domain.team.verification.BusinessCheckResult;
import com.shinhan.klljs.domain.team.verification.BusinessRegistrationVerifier;
import com.shinhan.klljs.domain.team.verification.CertificateValidity;
import com.shinhan.klljs.domain.team.verification.NormalizedBusinessInput;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;

/**
 * data.go.kr(공공데이터포털)의 국세청 사업자등록정보 API 클라이언트 (server-verification-spec.md §2).
 *
 * 운영에서는 항상 {@code /validate}(진위확인) 모드를 쓴다. {@code /status}는 진위확인 응답에
 * 상태 객체가 없을 때 영업 상태를 채우기 위해서만 보조로 호출한다.
 */
@Slf4j
@Component
public class NtsBusinessmanClient implements BusinessRegistrationVerifier {

    private static final String STATUS_CODE_OK = "OK";
    private static final String VALID_CODE = "01";

    private static final String VALIDATE_PATH = "/validate";
    private static final String STATUS_PATH = "/status";

    private final RestClient restClient;
    private final NtsBusinessmanProperties properties;

    public NtsBusinessmanClient(@Qualifier("ntsRestClient") RestClient restClient,
                                 NtsBusinessmanProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * 진위확인을 수행하고, 필요하면 상태조회를 덧붙여 판정에 필요한 값을 모두 채운다.
     *
     * @throws GeneralException 국세청 API가 실패하면 BUSINESS_500_004. 이 경우 DB에는 아무것도 쓰지 않는다
     */
    @Override
    public BusinessCheckResult check(NormalizedBusinessInput input) {
        NtsValidateResponse response = post(VALIDATE_PATH, NtsValidateRequest.of(input), NtsValidateResponse.class);
        NtsValidateResponse.Data data = firstData(VALIDATE_PATH, response.statusCode(), response.data());

        CertificateValidity validity = VALID_CODE.equals(data.valid())
                ? CertificateValidity.VALID
                : CertificateValidity.INVALID;

        // valid == "01"이 영업 중이라는 뜻은 아니다. 진위 확인을 통과한 폐업자가 실제로 존재하므로
        // 영업 상태를 반드시 별도로 확인한다. status 객체가 응답에 없으면 /status를 한 번 더 호출한다.
        NtsBusinessStatus status = (data.status() != null)
                ? data.status()
                : fetchStatus(input.businessNumber());

        return BusinessCheckResult.of(
                validity,
                data.validMsg(),
                status.businessStatusCode(),
                status.businessStatus(),
                status.taxType(),
                status.closingDate());
    }

    /** 사업자등록번호만으로 등록 여부와 영업 상태를 조회한다. 진위 확인이 아니므로 단독으로 승인 근거가 될 수 없다. */
    private NtsBusinessStatus fetchStatus(String businessNumber) {
        NtsStatusResponse response = post(STATUS_PATH, NtsStatusRequest.of(businessNumber), NtsStatusResponse.class);
        return firstData(STATUS_PATH, response.statusCode(), response.data());
    }

    /** status_code가 "OK"가 아니거나 data가 비어 있으면 실패로 처리한다. */
    private <T> T firstData(String path, String statusCode, List<T> data) {
        if (!STATUS_CODE_OK.equals(statusCode) || data == null || data.isEmpty()) {
            log.error("국세청 API 응답이 올바르지 않음: path={}, status_code={}, dataSize={}",
                    path, statusCode, (data == null) ? null : data.size());
            throw new GeneralException(BusinessRegistrationErrorCode.NTS_API_FAILED);
        }
        return data.getFirst();
    }

    private <REQ, RES> RES post(String path, REQ body, Class<RES> responseType) {
        if (!properties.hasServiceKey()) {
            log.error("국세청 API 서비스 키가 설정되지 않았습니다. app.nts.service-key(NTS_SERVICE_KEY)를 확인하세요.");
            throw new GeneralException(BusinessRegistrationErrorCode.NTS_SERVICE_KEY_MISSING);
        }

        // 이미 인코딩된 serviceKey를 다시 인코딩하지 않도록 URI를 직접 만들어 넘긴다.
        URI uri = URI.create(properties.baseUrl() + path + "?serviceKey=" + properties.encodedServiceKey());

        try {
            return restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException e) {
            // 예외 메시지에는 요청 URL이 그대로 들어가고 거기에 serviceKey가 실려 있다.
            // 마스킹해서 로그에 남기고, 원본 예외를 cause로 연결하지 않는다 (스택트레이스로도 새어 나간다).
            log.error("국세청 API 호출 실패: path={}, message={}", path, properties.maskSecrets(e.getMessage()));
            throw new GeneralException(BusinessRegistrationErrorCode.NTS_API_FAILED);
        }
    }
}
