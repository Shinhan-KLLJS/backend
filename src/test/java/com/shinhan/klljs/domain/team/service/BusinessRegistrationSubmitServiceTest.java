package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.BusinessRegistrationSubmitRequest;
import com.shinhan.klljs.domain.team.dto.BusinessRegistrationSubmitResponse;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamBusinessRegistration;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.entity.VerificationStatus;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamBusinessRegistrationRepository;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadTokenSigner;
import com.shinhan.klljs.domain.team.verification.BusinessCheckResult;
import com.shinhan.klljs.domain.team.verification.BusinessRegistrationVerifier;
import com.shinhan.klljs.domain.team.verification.CertificateValidity;
import com.shinhan.klljs.domain.team.verification.VerificationDecisionStatus;
import com.shinhan.klljs.domain.team.verification.VerificationReasonCode;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 제출 -> 검증 -> 적재 전 구간 (server-verification-spec.md §5).
 * 국세청 호출만 대역으로 바꾸고 나머지(권한, 트랜잭션, UPSERT)는 실제로 동작시킨다.
 */
// 로컬 시드 데이터 초기화기(LocalDashboardMockDataInitializer)를 끈다.
// 그 초기화기는 ApplicationRunner라 컨텍스트 기동 시점에 LocalDateTime.now(clock)을 호출하는데,
// 이 테스트는 시간을 고정하려고 Clock을 @MockitoBean으로 갈아끼운다. 목 Clock은 getZone()이 null을
// 돌려주므로 초기화기가 NPE로 죽고 컨텍스트 자체가 뜨지 못한다. 이 테스트는 시드 데이터가 필요 없다.
@SpringBootTest(properties = "app.local-test-data.enabled=false")
@Transactional
class BusinessRegistrationSubmitServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-10T07:43:25Z");

    @Autowired
    private BusinessRegistrationSubmitService submitService;

    @Autowired
    private TeamBusinessRegistrationRepository registrationRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private UserRepository userRepository;

    /** 진짜 서명 토큰을 만들어 쓴다 (업로드 API가 발급하는 것과 같은 것). */
    @Autowired
    private BusinessRegistrationUploadTokenSigner uploadTokenSigner;

    /** 국세청은 실제로 호출하지 않는다. 응답 파싱은 NtsBusinessmanClientTest가 따로 검증한다. */
    @MockitoBean
    private BusinessRegistrationVerifier verifier;

    /** verified_at을 정확히 단언하기 위해 시간을 고정한다. 토큰 만료 계산도 이 시계를 쓴다. */
    @MockitoBean
    private Clock clock;

    private Long userId;
    private Long teamId;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);

        User user = userRepository.save(
                User.builder().displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build());
        Team team = teamRepository.save(
                Team.builder().teamName("루비 광고").status(TeamStatus.ACTIVE).build());
        teamMemberRepository.save(TeamMember.builder()
                .team(team).user(user)
                .role(TeamMemberRole.OWNER).status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());

        this.userId = user.getId();
        this.teamId = team.getId();
    }

    @Test
    void submit_acceptedWhenValidActiveAndAdvertising() {
        given(verifier.check(any())).willReturn(activeAndValid());

        BusinessRegistrationSubmitResponse response = submitService.submit(userId, teamId, request("광고물제작", "광고대행"));

        assertThat(response.decisionStatus()).isEqualTo(VerificationDecisionStatus.ACCEPTED);
        assertThat(response.reasonCode()).isEqualTo(VerificationReasonCode.ACCEPTED);
        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(response.advertising().matchedKeywords()).containsExactly("광고대행", "광고물", "광고");
        // UTC 07:43:25 -> KST 16:43:25
        assertThat(response.verifiedAt()).isEqualTo("2026-07-10T16:43:25+09:00");

        TeamBusinessRegistration saved = registrationRepository.findByTeamId(teamId).orElseThrow();
        assertThat(saved.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(saved.getRejectionReason()).isNull();
        // 사용자가 확정 제출한 값이 저장된다. 사업자번호는 숫자만, 개업일은 DATE로 정규화된다.
        assertThat(saved.getBusinessNumber()).isEqualTo("4959240582");
        assertThat(saved.getBusinessOpeningDate()).isEqualTo(LocalDate.of(2024, 6, 24));
        assertThat(saved.getBusinessType()).isEqualTo("광고물제작");
        assertThat(saved.getBusinessItem()).isEqualTo("광고대행");
    }

    /** 진위 확인을 통과한 폐업자. 승인이 아니라 반려이고, 사유에 폐업일이 들어간다. */
    @Test
    void submit_rejectedWhenBusinessIsClosedEvenThoughCertificateIsValid() {
        given(verifier.check(any())).willReturn(BusinessCheckResult.of(
                CertificateValidity.VALID, null, "03", "폐업자", "일반과세자", "20240624"));

        BusinessRegistrationSubmitResponse response = submitService.submit(userId, teamId, request("광고물제작", "광고대행"));

        assertThat(response.decisionStatus()).isEqualTo(VerificationDecisionStatus.REJECTED);
        assertThat(response.reasonCode()).isEqualTo(VerificationReasonCode.INACTIVE_BUSINESS);
        assertThat(response.verifiedAt()).isNull();

        TeamBusinessRegistration saved = registrationRepository.findByTeamId(teamId).orElseThrow();
        assertThat(saved.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(saved.getRejectionReason()).contains("폐업자").contains("2024-06-24");
        assertThat(saved.getVerifiedAt()).isNull();
    }

    /** 입력 형식이 틀리면 국세청을 호출하지 않고 PENDING으로 저장한다 (400이 아니다). */
    @Test
    void submit_reviewRequiredWithoutCallingNtsWhenInputFormatIsInvalid() {
        // 사업자번호가 10자리가 아니다.
        BusinessRegistrationSubmitRequest invalid = new BusinessRegistrationSubmitRequest(
                "12345", "루비 광고", "이정현", "20240624", signedToken("광고물제작", "광고대행"));

        BusinessRegistrationSubmitResponse response = submitService.submit(userId, teamId, invalid);

        assertThat(response.decisionStatus()).isEqualTo(VerificationDecisionStatus.REVIEW_REQUIRED);
        assertThat(response.reasonCode()).isEqualTo(VerificationReasonCode.VALIDATION_INPUT_INCOMPLETE);
        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.PENDING);
        verify(verifier, never()).check(any());

        TeamBusinessRegistration saved = registrationRepository.findByTeamId(teamId).orElseThrow();
        // 개업일은 DATE 컬럼이라 정규화에 실패하면 저장하지 않는다. 사업자번호는 원문을 남겨 조사에 쓴다.
        assertThat(saved.getBusinessOpeningDate()).isNull();
        assertThat(saved.getBusinessNumber()).isEqualTo("12345");
        assertThat(saved.getRejectionReason()).isNotBlank();
    }

    /** 반려된 팀은 다시 제출할 수 있고, 같은 행이 승인으로 갱신된다 (team_id가 UNIQUE). */
    @Test
    void submit_resubmitAfterRejection_updatesSameRow() {
        given(verifier.check(any())).willReturn(BusinessCheckResult.of(
                CertificateValidity.VALID, null, "01", "계속사업자", "일반과세자", null));

        submitService.submit(userId, teamId, request("정보통신업", "소프트웨어")); // NON_ADVERTISING -> REJECTED
        assertThat(registrationRepository.findByTeamId(teamId).orElseThrow().getVerificationStatus())
                .isEqualTo(VerificationStatus.REJECTED);

        BusinessRegistrationSubmitResponse response = submitService.submit(userId, teamId, request("광고물제작", "광고대행"));

        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(registrationRepository.findAll()).hasSize(1);
    }

    /** 이미 승인된 팀은 재제출 자체를 막는다. 국세청도 부르지 않는다 (§5.3 상태 퇴행 방지). */
    @Test
    void submit_throwsConflictWhenAlreadyApproved() {
        given(verifier.check(any())).willReturn(activeAndValid());
        submitService.submit(userId, teamId, request("광고물제작", "광고대행"));

        assertThatThrownBy(() -> submitService.submit(userId, teamId, request("음식점업", "한식")))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.ALREADY_APPROVED));

        // 승인 상태가 그대로 유지된다.
        assertThat(registrationRepository.findByTeamId(teamId).orElseThrow().getVerificationStatus())
                .isEqualTo(VerificationStatus.APPROVED);
    }

    @Test
    void submit_throwsForbiddenWhenUserIsNotActiveTeamMember() {
        User outsider = userRepository.save(
                User.builder().displayName("영희").email("younghee@example.com").status(UserStatus.ACTIVE).build());

        assertThatThrownBy(() -> submitService.submit(outsider.getId(), teamId, request("광고물제작", "광고대행")))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.NOT_TEAM_MEMBER));

        verify(verifier, never()).check(any());
        assertThat(registrationRepository.findByTeamId(teamId)).isEmpty();
    }

    /**
     * 사업자등록증은 팀의 법적 신원이라 OWNER만 다룰 수 있다
     * (mvp-database-erd.md 4절 "권한 기준" - 사업자등록증이 OWNER 행에만 있다).
     * ADMIN은 초대와 캠페인 관리까지는 되지만 여기서는 막힌다.
     */
    @ParameterizedTest
    @EnumSource(value = TeamMemberRole.class, names = {"ADMIN", "MEMBER"})
    void submit_throwsForbiddenWhenActiveMemberIsNotOwner(TeamMemberRole role) {
        Long nonOwnerId = joinTeamAs(role);

        assertThatThrownBy(() -> submitService.submit(nonOwnerId, teamId, request("광고물제작", "광고대행")))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.NOT_TEAM_OWNER));

        // 권한 검사는 국세청 호출보다 먼저다 - 쿼터를 쓰지 않고, DB에도 아무것도 남기지 않는다.
        verify(verifier, never()).check(any());
        assertThat(registrationRepository.findByTeamId(teamId)).isEmpty();
    }

    /**
     * <b>§7의 우회가 막혔는지 확인한다.</b> 업태·종목은 요청 본문에 아예 없고 서명 토큰에서만 온다 -
     * 실제로는 음식점업인 사업자가 폼을 조작해 "광고대행"으로 승인받을 수 없다.
     * 토큰에 담긴 OCR 원본(정보통신업/소프트웨어)대로 광고업이 아니라고 판정되어야 한다.
     */
    @Test
    void submit_classifiesWithTokenValuesSoTheUserCannotForgeTheBusinessType() {
        given(verifier.check(any())).willReturn(activeAndValid());

        // 토큰에는 OCR이 읽은 "정보통신업/소프트웨어"가 서명돼 있다.
        BusinessRegistrationSubmitResponse response =
                submitService.submit(userId, teamId, request("정보통신업", "소프트웨어"));

        assertThat(response.decisionStatus()).isEqualTo(VerificationDecisionStatus.REJECTED);
        assertThat(response.reasonCode()).isEqualTo(VerificationReasonCode.NON_ADVERTISING_BUSINESS);
        assertThat(response.advertising().matchedKeywords()).isEmpty();
    }

    /** 서명을 위조한 토큰은 국세청을 부르기도 전에 막히고, DB에도 아무것도 남지 않는다. */
    @Test
    void submit_rejectsForgedToken() {
        String forged = signedToken("정보통신업", "소프트웨어") + "tampered";

        assertThatThrownBy(() -> submitService.submit(userId, teamId, requestWithToken(forged)))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));

        verify(verifier, never()).check(any());
        assertThat(registrationRepository.findByTeamId(teamId)).isEmpty();
    }

    /** 남이 업로드한 사업자등록증으로는 제출할 수 없다 (토큰의 uploaderId가 요청자와 달라야 한다). */
    @Test
    void submit_rejectsTokenIssuedToAnotherUser() {
        String othersToken = uploadTokenSigner.sign(
                "team-registrations/doc.pdf", userId + 999, "광고물제작", "광고대행");

        assertThatThrownBy(() -> submitService.submit(userId, teamId, requestWithToken(othersToken)))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));

        verify(verifier, never()).check(any());
        assertThat(registrationRepository.findByTeamId(teamId)).isEmpty();
    }

    /** DB에는 서명 토큰이 아니라 토큰에서 꺼낸 순수 S3 키가 저장된다. */
    @Test
    void submit_storesTheRealS3KeyNotTheSignedToken() {
        given(verifier.check(any())).willReturn(activeAndValid());

        submitService.submit(userId, teamId, request("광고물제작", "광고대행"));

        assertThat(registrationRepository.findByTeamId(teamId).orElseThrow().getDocumentStorageKey())
                .isEqualTo("team-registrations/doc.pdf");
    }

    private BusinessRegistrationSubmitRequest requestWithToken(String documentStorageKey) {
        return new BusinessRegistrationSubmitRequest(
                "495-92-40582", "루비 광고", "홍길동 외 1명", "2024-06-24", documentStorageKey);
    }

    /** 이 팀에 주어진 역할의 ACTIVE 멤버를 하나 더 만들고 그 사용자 ID를 돌려준다. */
    private Long joinTeamAs(TeamMemberRole role) {
        User user = userRepository.save(User.builder()
                .displayName(role.name())
                .email(role.name().toLowerCase() + "@example.com")
                .status(UserStatus.ACTIVE)
                .build());
        teamMemberRepository.save(TeamMember.builder()
                .team(teamRepository.getReferenceById(teamId)).user(user)
                .role(role).status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());
        return user.getId();
    }

    private static BusinessCheckResult activeAndValid() {
        return BusinessCheckResult.of(
                CertificateValidity.VALID, null, "01", "계속사업자", "부가가치세 일반과세자", null);
    }

    /** 하이픈과 "외 1명"이 섞인, 실제로 프론트가 보낼 법한 요청. */
    private BusinessRegistrationSubmitRequest request(String businessType, String businessItem) {
        return new BusinessRegistrationSubmitRequest(
                "495-92-40582", "루비 광고", "홍길동 외 1명", "2024-06-24",
                signedToken(businessType, businessItem));
    }

    /**
     * 업로드 API가 발급했을 법한 진짜 서명 토큰.
     *
     * 업태·종목은 <b>요청 본문이 아니라 여기</b>에 들어간다 - 사용자가 폼에서 "광고대행"으로 고쳐
     * 스스로를 승인시키는 것을 막기 위해서다 (server-verification-spec.md §7).
     */
    private String signedToken(String businessType, String businessItem) {
        return uploadTokenSigner.sign(
                "team-registrations/doc.pdf", userId, businessType, businessItem);
    }
}
