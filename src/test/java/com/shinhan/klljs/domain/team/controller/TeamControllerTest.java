package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadTokenSigner;
import com.shinhan.klljs.domain.team.verification.BusinessCheckResult;
import com.shinhan.klljs.domain.team.verification.BusinessRegistrationVerifier;
import com.shinhan.klljs.domain.team.verification.CertificateValidity;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 팀 생성 엔드포인트의 HTTP 계약 (docs/team-creation-api-spec.md 5절).
 *
 * <b>성공은 200이다.</b> 문서 표에 201이라고 적힌 곳이 있으나, 같은 문서의 응답 예시와 dev 설계 문서는
 * 일관되게 COMMON_200_001(200)이다. 프론트가 201로 분기하면 깨지므로 여기에 고정해 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamControllerTest {

    private static final String PATH = "/api/v1/teams";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRegistrationUploadTokenSigner uploadTokenSigner;

    @MockitoBean
    private BusinessRegistrationVerifier verifier;

    private String accessToken;
    private Long userId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build());
        this.userId = user.getId();
        this.accessToken = jwtTokenService.generateAccessToken(userId);
    }

    @Test
    void createTeam_returns200NotCreated() throws Exception {
        given(verifier.check(any())).willReturn(BusinessCheckResult.of(
                CertificateValidity.VALID, null, "01", "계속사업자", "일반과세자", null));

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("루비 광고 3팀", "루비 광고", "홍길동")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200_001"))
                .andExpect(jsonPath("$.result.teamId").isNumber())
                .andExpect(jsonPath("$.result.verificationStatus").value("APPROVED"));
    }

    /**
     * DB 컬럼 길이를 넘는 값은 400으로 막는다 (team-creation-api-spec.md 5절 필수 테스트 목록).
     *
     * 이걸 막지 않으면 Hibernate가 INSERT를 시도하다 DataIntegrityViolationException으로 죽고,
     * 사용자에게는 <b>500</b>이 나간다 - 사용자 입력 오류인데 서버 장애처럼 보인다.
     * teamName/companyName은 200자, representativeName은 100자가 상한이다.
     */
    @Test
    void createTeam_withTeamNameOverTwoHundredChars_returns400NotServerError() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("가".repeat(201), "루비 광고", "홍길동")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));
    }

    @Test
    void createTeam_withRepresentativeNameOverHundredChars_returns400() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("루비 광고 3팀", "루비 광고", "홍".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));
    }

    /** 경계값: 상한과 정확히 같은 길이는 통과해야 한다. 200자짜리 팀명을 막으면 정상 입력이 반려된다. */
    @Test
    void createTeam_withTeamNameExactlyAtTheLimit_isAccepted() throws Exception {
        given(verifier.check(any())).willReturn(BusinessCheckResult.of(
                CertificateValidity.VALID, null, "01", "계속사업자", "일반과세자", null));

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("가".repeat(200), "루비 광고", "홍길동")))
                .andExpect(status().isOk());
    }

    private String body(String teamName, String companyName, String representativeName) {
        return """
                {"teamName":"%s","companyName":"%s","representativeName":"%s",
                 "businessNumber":"495-92-40582","businessOpeningDate":"2024-06-24",
                 "documentStorageKey":"%s"}
                """.formatted(teamName, companyName, representativeName, signedToken());
    }

    /** 업로드 API가 발급하는 것과 똑같은 진짜 서명 토큰. 업태·종목은 OCR 원본으로 실려 있다. */
    private String signedToken() {
        return uploadTokenSigner.sign("team-registrations/doc.pdf", userId, "광고물제작", "광고대행");
    }
}
