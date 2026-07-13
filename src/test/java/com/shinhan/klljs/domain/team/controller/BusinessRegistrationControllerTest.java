package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
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

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 사업자등록증 제출 엔드포인트의 HTTP 계약.
 *
 * 운영 프로필처럼 CSRF를 켠 상태로 돌린다. oauth2ResourceServer()가 Bearer 토큰이 실린 요청을
 * CSRF 예외로 자동 등록하므로(OAuth2ResourceServerConfigurer.registerDefaultCsrfOverride) 별도
 * 설정 없이 통과해야 한다. 이 사실이 깨지면 운영에서만 403이 나므로 여기에 고정해 둔다.
 */
@SpringBootTest(properties = "app.security.csrf-enabled=true")
@AutoConfigureMockMvc
@Transactional
class BusinessRegistrationControllerTest {

    private static final String PATH = "/api/v1/teams/%d/business-registration";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private BusinessRegistrationUploadTokenSigner uploadTokenSigner;

    @MockitoBean
    private BusinessRegistrationVerifier verifier;

    private String accessToken;
    private Long teamId;
    private Long ownerId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(
                User.builder().displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build());
        Team team = teamRepository.save(Team.builder().teamName("루비 광고").status(TeamStatus.ACTIVE).build());
        teamMemberRepository.save(TeamMember.builder()
                .team(team).user(user)
                .role(TeamMemberRole.OWNER).status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());

        this.ownerId = user.getId();
        this.accessToken = jwtTokenService.generateAccessToken(user.getId());
        this.teamId = team.getId();
    }

    /** Bearer 토큰만으로 CSRF 토큰 없이 POST가 통과하고, 판정 결과가 200으로 내려온다. */
    @Test
    void submit_withBearerTokenAndNoCsrfToken_returnsDecision() throws Exception {
        given(verifier.check(any())).willReturn(BusinessCheckResult.of(
                CertificateValidity.VALID, null, "01", "계속사업자", "부가가치세 일반과세자", null));

        mockMvc.perform(post(PATH.formatted(teamId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("루비 광고", "광고물제작", "광고대행")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.decisionStatus").value("accepted"))
                .andExpect(jsonPath("$.result.verificationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.result.reasonCode").value("ACCEPTED"))
                .andExpect(jsonPath("$.result.advertising.confidence").value("high"))
                .andExpect(jsonPath("$.result.advertising.matchedKeywords[0]").value("광고대행"));
    }

    /**
     * <b>반려도 200이다.</b> 400으로 바꾸면 안 된다 — ApiResponse.onFailure()는 result를 무조건
     * null로 만들어서(생성자가 private), 실패 응답에는 errorDetail(String 리스트)밖에 실을 수 없다.
     * 반려 화면은 사유(reasonCode)와 "왜 광고업이 아니라고 봤는지"(advertising.matchedKeywords)를
     * 보여줘야 하는데, 400으로 만들면 이 구조체를 전부 잃는다.
     *
     * 팀 생성 API는 다르다 — 거기선 실패 시 row 자체가 안 생기므로 400이 맞다
     * (docs/team-creation-api-spec.md 5절 "판정과 응답" 대비표).
     */
    @Test
    void submit_whenRejected_returns200WithReasonAndAdvertisingEvidence() throws Exception {
        // 진위 확인은 통과했지만 폐업자 -> 반려 (규칙 4가 규칙 8보다 먼저 걸린다)
        given(verifier.check(any())).willReturn(BusinessCheckResult.of(
                CertificateValidity.VALID, null, "03", "폐업자", "일반과세자", "20240624"));

        mockMvc.perform(post(PATH.formatted(teamId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("루비 광고", "정보통신업", "소프트웨어")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.decisionStatus").value("rejected"))
                .andExpect(jsonPath("$.result.verificationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.result.reasonCode").value("INACTIVE_BUSINESS"))
                .andExpect(jsonPath("$.result.message").value(org.hamcrest.Matchers.containsString("2024-06-24")))
                .andExpect(jsonPath("$.result.advertising.classificationStatus").value("not_matched"))
                .andExpect(jsonPath("$.result.verifiedAt").doesNotExist());
    }

    /**
     * @Valid가 실제로 동작하는지 고정한다. hibernate-validator가 springdoc을 통해 전이로 들어와 있어,
     * 명시 선언이 사라지면 이 테스트만 검증 누락을 잡아낼 수 있다.
     */
    @Test
    void submit_withBlankCompanyName_returns400() throws Exception {
        mockMvc.perform(post(PATH.formatted(teamId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "광고물제작", "광고대행")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));
    }

    /**
     * 사업자번호·개업일자·대표자명은 형식이 틀려도 400이 아니라 200 + review_required다 (§4 1번 규칙).
     * @Valid를 이 세 필드에 걸면 이 경로가 사라지므로 함께 고정한다.
     */
    @Test
    void submit_withMalformedVerificationInput_returns200ReviewRequired() throws Exception {
        mockMvc.perform(post(PATH.formatted(teamId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessNumber":"","companyName":"루비 광고","representativeName":"",
                                 "businessOpeningDate":"","documentStorageKey":"%s"}
                                """.formatted(signedToken("광고물제작", "광고대행"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.decisionStatus").value("review_required"))
                .andExpect(jsonPath("$.result.reasonCode").value("VALIDATION_INPUT_INCOMPLETE"))
                .andExpect(jsonPath("$.result.verificationStatus").value("PENDING"));
    }

    /**
     * 토큰이 아예 없으면 401이 아니라 <b>403</b>이다. Bearer 토큰이 없어 CSRF 예외에 해당하지 않는데,
     * CsrfFilter는 ExceptionTranslationFilter보다 앞에 있어 자체 AccessDeniedHandler로 403을 내고
     * 401 진입점(BearerTokenAuthenticationEntryPoint)까지 가지 못하기 때문이다.
     *
     * 액세스 토큰이 만료된 경우는 다르다. 만료된 토큰이라도 Authorization 헤더가 붙어 있으면 CSRF 예외라
     * BearerTokenAuthenticationFilter까지 도달해 401이 나온다. 즉 프론트의 토큰 재발급 흐름은 정상 동작한다.
     *
     * (CSRF를 끈 local 프로필에서는 이 요청도 401이 된다.)
     */
    @Test
    void submit_withoutAccessToken_isBlockedByCsrfBeforeAuthentication() throws Exception {
        mockMvc.perform(post(PATH.formatted(teamId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("루비 광고", "광고물제작", "광고대행")))
                .andExpect(status().isForbidden());
    }

    /** 만료·위조 토큰은 Bearer 요청이므로 CSRF를 통과해 인증 단계에서 401로 걸린다. */
    @Test
    void submit_withGarbageAccessToken_returns401() throws Exception {
        mockMvc.perform(post(PATH.formatted(teamId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("루비 광고", "광고물제작", "광고대행")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 존재하지 않는 팀도 "멤버 아님"과 <b>같은 403</b>이다. 404로 구분해 주면 팀 ID를 하나씩 넣어보는
     * 것만으로 어떤 팀이 실재하는지 알아낼 수 있다.
     */
    @Test
    void submit_whenNotTeamMember_returns403() throws Exception {
        mockMvc.perform(post(PATH.formatted(teamId + 999))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("루비 광고", "광고물제작", "광고대행")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BUSINESS_403_001"));
    }

    /** 팀 멤버여도 OWNER가 아니면 막힌다. 멤버 아님(403_001)과 다른 코드를 줘서 프론트가 안내를 구분한다. */
    @Test
    void submit_whenActiveMemberIsNotOwner_returns403() throws Exception {
        User admin = userRepository.save(
                User.builder().displayName("관리자").email("admin@example.com").status(UserStatus.ACTIVE).build());
        teamMemberRepository.save(TeamMember.builder()
                .team(teamRepository.getReferenceById(teamId)).user(admin)
                .role(TeamMemberRole.ADMIN).status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());

        mockMvc.perform(post(PATH.formatted(teamId))
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtTokenService.generateAccessToken(admin.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("루비 광고", "광고물제작", "광고대행")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BUSINESS_403_002"));
    }

    /**
     * 업태·종목은 <b>요청 본문에 없다.</b> 업로드 API가 발급한 서명 토큰(documentStorageKey) 안에
     * OCR 원본으로 들어있고, 서버가 거기서 꺼내 쓴다 - 사용자가 "광고대행"으로 고쳐 스스로를
     * 승인시키는 것을 막기 위해서다 (server-verification-spec.md §7).
     */
    private String body(String companyName, String businessType, String businessItem) {
        return """
                {"businessNumber":"495-92-40582","companyName":"%s","representativeName":"홍길동 외 1명",
                 "businessOpeningDate":"2024-06-24","documentStorageKey":"%s"}
                """.formatted(companyName, signedToken(businessType, businessItem));
    }

    private String signedToken(String businessType, String businessItem) {
        return uploadTokenSigner.sign("team-registrations/doc.pdf", ownerId, businessType, businessItem);
    }
}
