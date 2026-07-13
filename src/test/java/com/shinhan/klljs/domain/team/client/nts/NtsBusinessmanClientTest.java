package com.shinhan.klljs.domain.team.client.nts;

import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.domain.team.verification.BusinessCheckResult;
import com.shinhan.klljs.domain.team.verification.CertificateValidity;
import com.shinhan.klljs.domain.team.verification.NormalizedBusinessInput;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 국세청 API 클라이언트 (server-verification-spec.md §2).
 * 여기서 깨지기 쉬운 것은 serviceKey 인코딩과 status 객체 폴백이라 그 두 가지에 집중한다.
 */
class NtsBusinessmanClientTest {

    private static final String BASE_URL = "https://api.odcloud.kr/api/nts-businessman/v1";

    private static final NormalizedBusinessInput INPUT = new NormalizedBusinessInput(
            "4959240582", "20240624", LocalDate.of(2024, 6, 24), "이정현");

    /** valid + status 객체가 함께 오면 /status를 부르지 않는다. */
    @Test
    void check_usesStatusObjectFromValidateResponse_withoutCallingStatusApi() {
        Fixture fixture = fixture("plain-key");
        fixture.server.expect(requestTo(BASE_URL + "/validate?serviceKey=plain-key"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.businesses[0].b_no").value("4959240582"))
                .andExpect(jsonPath("$.businesses[0].start_dt").value("20240624"))
                .andExpect(jsonPath("$.businesses[0].p_nm").value("이정현"))
                // 사업자명·업태·종목·주소는 보내지 않는다 (OCR 흔들림 때문에 정상 사업자가 반려된다).
                .andExpect(jsonPath("$.businesses[0].b_nm").doesNotExist())
                .andRespond(withSuccess("""
                        {
                          "status_code": "OK",
                          "data": [{
                            "valid": "01",
                            "status": {
                              "b_no": "4959240582", "b_stt": "계속사업자", "b_stt_cd": "01",
                              "tax_type": "부가가치세 일반과세자"
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        BusinessCheckResult result = fixture.client.check(INPUT);

        assertThat(result.certificateValidity()).isEqualTo(CertificateValidity.VALID);
        assertThat(result.registered()).isTrue();
        assertThat(result.active()).isTrue();
        assertThat(result.businessStatus()).isEqualTo("계속사업자");
        fixture.server.verify(); // /status 호출이 없었음을 함께 보장한다
    }

    /** status 객체가 없으면 /status를 한 번 더 호출해 영업 상태를 채운다. */
    @Test
    void check_fallsBackToStatusApiWhenValidateResponseHasNoStatusObject() {
        Fixture fixture = fixture("plain-key");
        fixture.server.expect(requestTo(BASE_URL + "/validate?serviceKey=plain-key"))
                .andRespond(withSuccess("""
                        {"status_code": "OK", "data": [{"valid": "01"}]}
                        """, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(BASE_URL + "/status?serviceKey=plain-key"))
                .andExpect(jsonPath("$.b_no[0]").value("4959240582"))
                .andRespond(withSuccess("""
                        {
                          "status_code": "OK",
                          "data": [{"b_no": "4959240582", "b_stt": "폐업자", "b_stt_cd": "03",
                                    "tax_type": "부가가치세 일반과세자", "end_dt": "20240624"}]
                        }
                        """, MediaType.APPLICATION_JSON));

        BusinessCheckResult result = fixture.client.check(INPUT);

        // 진위 확인은 통과했지만 폐업자다. 두 값은 반드시 따로 판단해야 한다.
        assertThat(result.certificateValidity()).isEqualTo(CertificateValidity.VALID);
        assertThat(result.active()).isFalse();
        assertThat(result.closingDate()).isEqualTo("20240624");
        fixture.server.verify();
    }

    /** 미등록 번호는 b_stt_cd가 빈 문자열로 온다. tax_type 문구로 판단하지 않는다. */
    @Test
    void check_treatsEmptyStatusCodeAsUnregistered() {
        Fixture fixture = fixture("plain-key");
        fixture.server.expect(requestTo(BASE_URL + "/validate?serviceKey=plain-key"))
                .andRespond(withSuccess("""
                        {
                          "status_code": "OK",
                          "data": [{
                            "valid": "02", "valid_msg": "확인할 수 없습니다.",
                            "status": {"b_no": "4959240582", "b_stt_cd": "",
                                       "tax_type": "국세청에 등록되지 않은 사업자등록번호입니다."}
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        BusinessCheckResult result = fixture.client.check(INPUT);

        assertThat(result.registered()).isFalse();
        assertThat(result.active()).isFalse();
        assertThat(result.certificateValidity()).isEqualTo(CertificateValidity.INVALID);
        assertThat(result.invalidMessage()).isEqualTo("확인할 수 없습니다.");
    }

    /**
     * 이미 URL 인코딩된 키('%' 포함)는 그대로 쓴다. 다시 인코딩하면 '%'가 '%25'가 되어 인증에 실패한다.
     */
    @Test
    void check_doesNotDoubleEncodeAlreadyEncodedServiceKey() {
        Fixture fixture = fixture("abc%2Fdef%3D%3D");
        fixture.server.expect(requestTo(BASE_URL + "/validate?serviceKey=abc%2Fdef%3D%3D"))
                .andRespond(withSuccess(validateOk(), MediaType.APPLICATION_JSON));

        fixture.client.check(INPUT);

        fixture.server.verify();
    }

    /** 인코딩되지 않은 키는 URL 인코딩해서 보낸다. '+'와 '='가 쿼리에서 깨지지 않아야 한다. */
    @Test
    void check_encodesRawServiceKey() {
        Fixture fixture = fixture("abc+def=");
        fixture.server.expect(requestTo(BASE_URL + "/validate?serviceKey=abc%2Bdef%3D"))
                .andRespond(withSuccess(validateOk(), MediaType.APPLICATION_JSON));

        fixture.client.check(INPUT);

        fixture.server.verify();
    }

    @Test
    void check_throwsWhenStatusCodeIsNotOk() {
        Fixture fixture = fixture("plain-key");
        fixture.server.expect(requestTo(BASE_URL + "/validate?serviceKey=plain-key"))
                .andRespond(withSuccess("""
                        {"status_code": "BAD_JSON_REQUEST", "data": []}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client.check(INPUT))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.NTS_API_FAILED));
    }

    /** HTTP 오류는 502로 바꾸고, 예외 메시지에 serviceKey가 새어 나가지 않아야 한다. */
    @Test
    void check_masksServiceKeyWhenHttpCallFails() {
        Fixture fixture = fixture("super-secret-key");
        fixture.server.expect(requestTo(BASE_URL + "/validate?serviceKey=super-secret-key"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> fixture.client.check(INPUT))
                .isInstanceOf(GeneralException.class)
                .hasMessageNotContaining("super-secret-key")
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.NTS_API_FAILED));
    }

    /** 서비스 키가 없으면 국세청을 호출하지도 않는다. */
    @Test
    void check_throwsWithoutCallingNtsWhenServiceKeyIsMissing() {
        Fixture fixture = fixture("");

        assertThatThrownBy(() -> fixture.client.check(INPUT))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.NTS_SERVICE_KEY_MISSING));

        fixture.server.verify(); // 기대한 요청이 0건이므로 아무 호출도 없었어야 한다
    }

    @Test
    void maskSecrets_removesServiceKeyFromQueryStringAndRawValue() {
        NtsBusinessmanProperties properties = new NtsBusinessmanProperties(BASE_URL, "super-secret", 1000, 1000);

        assertThat(properties.maskSecrets("POST " + BASE_URL + "/validate?serviceKey=super-secret failed"))
                .doesNotContain("super-secret")
                .contains("serviceKey=***");
        assertThat(properties.maskSecrets("key was super-secret")).isEqualTo("key was ***");
    }

    private static String validateOk() {
        return """
                {
                  "status_code": "OK",
                  "data": [{"valid": "01",
                            "status": {"b_no": "4959240582", "b_stt": "계속사업자", "b_stt_cd": "01"}}]
                }
                """;
    }

    private static Fixture fixture(String serviceKey) {
        NtsBusinessmanProperties properties = new NtsBusinessmanProperties(BASE_URL, serviceKey, 1000, 1000);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new NtsBusinessmanClient(builder.build(), properties), server);
    }

    private record Fixture(NtsBusinessmanClient client, MockRestServiceServer server) {
    }
}
