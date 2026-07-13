package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
import com.shinhan.klljs.domain.team.dto.TeamCreateResponse;
import com.shinhan.klljs.domain.team.entity.TeamBusinessRegistration;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.entity.VerificationStatus;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.domain.team.exception.BusinessVerificationRejectionCode;
import com.shinhan.klljs.domain.team.repository.TeamBusinessRegistrationRepository;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadTokenSigner;
import com.shinhan.klljs.domain.team.verification.BusinessCheckResult;
import com.shinhan.klljs.domain.team.verification.BusinessRegistrationVerifier;
import com.shinhan.klljs.domain.team.verification.CertificateValidity;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.code.BaseErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 팀 생성 (docs/team-creation-api-spec.md 5절).
 *
 * 핵심 계약: <b>검증을 통과한 경우에만 팀이 만들어진다.</b> 실패하면 400이고 어떤 row도 남지 않는다.
 */
// 로컬 시드 데이터 초기화기(LocalDashboardMockDataInitializer)를 끈다.
// 그 초기화기는 ApplicationRunner라 컨텍스트 기동 시점에 LocalDateTime.now(clock)을 호출하는데,
// 이 테스트는 시간을 고정하려고 Clock을 @MockitoBean으로 갈아끼운다. 목 Clock은 getZone()이 null을
// 돌려주므로 초기화기가 NPE로 죽고 컨텍스트 자체가 뜨지 못한다. 이 테스트는 시드 데이터가 필요 없다.
@SpringBootTest(properties = "app.local-test-data.enabled=false")
@Transactional
class TeamCreateServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T07:43:25Z");
    private static final String S3_KEY = "team-registrations/doc.pdf";

    @Autowired
    private TeamCreateService teamCreateService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private TeamBusinessRegistrationRepository registrationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRegistrationUploadTokenSigner uploadTokenSigner;

    @MockitoBean
    private BusinessRegistrationVerifier verifier;

    @MockitoBean
    private Clock clock;

    private Long userId;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);

        this.userId = userRepository.save(
                        User.builder().displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build())
                .getId();
    }

    /** 팀 + OWNER + 사업자등록(APPROVED)이 한 번에 만들어진다. */
    @Test
    void create_makesTeamOwnerAndApprovedRegistration() {
        given(verifier.check(any())).willReturn(activeAndValid());

        TeamCreateResponse response = teamCreateService.create(userId, request("광고물제작", "광고대행"));

        assertThat(response.teamId()).isNotNull();
        assertThat(response.teamName()).isEqualTo("루비 광고 3팀");
        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.APPROVED);

        assertThat(teamRepository.findById(response.teamId()).orElseThrow().getStatus())
                .isEqualTo(TeamStatus.ACTIVE);

        // 팀을 만든 사람이 OWNER다. 초대로 들어온 게 아니므로 joinedViaInvite는 null이다.
        TeamMember owner = teamMemberRepository.findAll().getFirst();
        assertThat(owner.getRole()).isEqualTo(TeamMemberRole.OWNER);
        assertThat(owner.getStatus()).isEqualTo(TeamMemberStatus.ACTIVE);
        assertThat(owner.getJoinedViaInvite()).isNull();

        TeamBusinessRegistration registration =
                registrationRepository.findByTeamId(response.teamId()).orElseThrow();
        assertThat(registration.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(registration.getVerifiedAt()).isNotNull();
        assertThat(registration.getRejectionReason()).isNull();
        // 정규화된 값이 저장된다 (하이픈 제거, DATE 변환).
        assertThat(registration.getBusinessNumber()).isEqualTo("4959240582");
        assertThat(registration.getBusinessOpeningDate()).isEqualTo(LocalDate.of(2024, 6, 24));
        // 업태·종목은 토큰(OCR 원본)에서 왔다. 사업장 소재지는 이 플로우에서 받지 않는다.
        assertThat(registration.getBusinessType()).isEqualTo("광고물제작");
        assertThat(registration.getBusinessItem()).isEqualTo("광고대행");
        assertThat(registration.getBusinessAddress()).isNull();
        // DB에는 서명 토큰이 아니라 순수 S3 키가 저장된다.
        assertThat(registration.getDocumentStorageKey()).isEqualTo(S3_KEY);
    }

    /** 광고업이 아니면 400이고 <b>팀이 만들어지지 않는다.</b> */
    @Test
    void create_rejectsNonAdvertisingBusinessAndLeavesNoRows() {
        given(verifier.check(any())).willReturn(activeAndValid());

        assertThatThrownBy(() -> teamCreateService.create(userId, request("정보통신업", "소프트웨어")))
                .satisfies(hasErrorCode(BusinessVerificationRejectionCode.NON_ADVERTISING_BUSINESS));

        assertNothingWasCreated();
    }

    /** 진위 확인을 통과한 폐업자도 막는다. 반려 사유에 폐업일이 함께 내려간다. */
    @Test
    void create_rejectsClosedBusinessEvenThoughCertificateIsValid() {
        given(verifier.check(any())).willReturn(BusinessCheckResult.of(
                CertificateValidity.VALID, null, "03", "폐업자", "일반과세자", "20240624"));

        assertThatThrownBy(() -> teamCreateService.create(userId, request("광고물제작", "광고대행")))
                .satisfies(hasErrorCode(BusinessVerificationRejectionCode.INACTIVE_BUSINESS))
                .hasMessageContaining("2024-06-24");

        assertNothingWasCreated();
    }

    /**
     * review_required도 400이다 (rejected와 마찬가지로 팀이 안 생긴다).
     * 수동 검토 대기열이 없어서 PENDING인 팀을 만들어두면 "PENDING 동안 팀을 쓸 수 있는가"를 정해야 한다.
     */
    @Test
    void create_rejectsReviewRequiredBecauseThereIsNoManualReviewQueue() {
        given(verifier.check(any())).willReturn(activeAndValid());

        // "마케팅"은 중간 신뢰도라 광고 관련으로 보되 사람이 확인해야 한다.
        assertThatThrownBy(() -> teamCreateService.create(userId, request("서비스업", "마케팅")))
                .satisfies(hasErrorCode(
                        BusinessVerificationRejectionCode.ADVERTISING_CLASSIFICATION_REVIEW_REQUIRED));

        assertNothingWasCreated();
    }

    /**
     * OCR이 업태·종목을 못 읽으면 판단 근거가 없어 막힌다.
     * <b>사용자가 폼에서 고칠 수 없는 유일한 경우다</b> - 재업로드를 안내해야 한다.
     */
    @Test
    void create_rejectsWhenOcrCouldNotReadTheBusinessTypeAndTellsUserToReupload() {
        given(verifier.check(any())).willReturn(activeAndValid());

        assertThatThrownBy(() -> teamCreateService.create(userId, request(null, null)))
                .satisfies(hasErrorCode(
                        BusinessVerificationRejectionCode.ADVERTISING_CLASSIFICATION_INPUT_INCOMPLETE));

        assertThat(BusinessVerificationRejectionCode.ADVERTISING_CLASSIFICATION_INPUT_INCOMPLETE.getMessage())
                .contains("다시 업로드");
        assertNothingWasCreated();
    }

    /** 형식이 틀리면 국세청을 부르지도 않고 막는다 (재제출 API와 달리 여기서는 400이다). */
    @Test
    void create_rejectsMalformedInputWithoutCallingNts() {
        TeamCreateRequest malformed = new TeamCreateRequest(
                "루비 광고 3팀", "루비 광고", "홍길동", "12345", "2024-06-24",
                signedToken("광고물제작", "광고대행"));

        assertThatThrownBy(() -> teamCreateService.create(userId, malformed))
                .satisfies(hasErrorCode(BusinessVerificationRejectionCode.VALIDATION_INPUT_INCOMPLETE));

        verify(verifier, never()).check(any());
        assertNothingWasCreated();
    }

    /**
     * 미래 개업일도 형식 오류로 막는다 (team-creation-api-spec.md 5절).
     * 아직 오지 않은 날짜가 사업자등록증에 찍혀 있을 수는 없으므로, 국세청 쿼터를 쓰기 전에 걸러낸다.
     * 고정된 NOW(2026-07-13 KST) 기준으로 다음 날이다.
     */
    @Test
    void create_rejectsFutureOpeningDateWithoutCallingNts() {
        TeamCreateRequest futureOpening = new TeamCreateRequest(
                "루비 광고 3팀", "루비 광고", "홍길동", "495-92-40582", "2026-07-14",
                signedToken("광고물제작", "광고대행"));

        assertThatThrownBy(() -> teamCreateService.create(userId, futureOpening))
                .satisfies(hasErrorCode(BusinessVerificationRejectionCode.VALIDATION_INPUT_INCOMPLETE));

        verify(verifier, never()).check(any());
        assertNothingWasCreated();
    }

    /**
     * 사용자가 타이핑한 이름 세 개는 앞뒤 공백을 지우고 저장한다 (team-creation-api-spec.md 5절).
     * @NotBlank는 "공백뿐인 값"만 막을 뿐 " 루비 광고 "는 그대로 통과시켜서, 이 처리가 없으면
     * 공백이 붙은 채로 DB에 들어간다. 이름 <b>안쪽</b>의 공백은 건드리지 않는다.
     */
    @Test
    void create_trimsSurroundingWhitespaceOfUserTypedNames() {
        given(verifier.check(any())).willReturn(activeAndValid());

        TeamCreateRequest padded = new TeamCreateRequest(
                "  루비 광고 3팀  ", "  루비 광고  ", "  홍길동 외 1명  ", "495-92-40582", "2024-06-24",
                signedToken("광고물제작", "광고대행"));

        TeamCreateResponse response = teamCreateService.create(userId, padded);

        assertThat(response.teamName()).isEqualTo("루비 광고 3팀");
        assertThat(teamRepository.findById(response.teamId()).orElseThrow().getTeamName())
                .isEqualTo("루비 광고 3팀");

        TeamBusinessRegistration registration =
                registrationRepository.findByTeamId(response.teamId()).orElseThrow();
        assertThat(registration.getCompanyName()).isEqualTo("루비 광고");
        assertThat(registration.getRepresentativeName()).isEqualTo("홍길동 외 1명");
    }

    /**
     * <b>§7의 우회가 막혔는지 확인한다.</b> 업태·종목은 요청 본문에 아예 없고 서명 토큰에서만 온다 -
     * 음식점업 사업자가 폼을 조작해 광고업자로 승인받을 수 없다.
     */
    @Test
    void create_classifiesWithTokenValuesSoTheUserCannotForgeTheBusinessType() {
        given(verifier.check(any())).willReturn(activeAndValid());

        // 토큰에는 OCR이 읽은 "음식점업/한식"이 서명돼 있다. 요청 본문에는 업태 자리가 없다.
        assertThatThrownBy(() -> teamCreateService.create(userId, request("음식점업", "한식")))
                .satisfies(hasErrorCode(BusinessVerificationRejectionCode.NON_ADVERTISING_BUSINESS));

        assertNothingWasCreated();
    }

    /** 위조된 토큰은 국세청을 부르기도 전에 막힌다. */
    @Test
    void create_rejectsForgedToken() {
        TeamCreateRequest forged = new TeamCreateRequest(
                "루비 광고 3팀", "루비 광고", "홍길동", "495-92-40582", "2024-06-24",
                signedToken("광고물제작", "광고대행") + "tampered");

        assertThatThrownBy(() -> teamCreateService.create(userId, forged))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));

        verify(verifier, never()).check(any());
        assertNothingWasCreated();
    }

    /** 남이 업로드한 사업자등록증으로는 팀을 만들 수 없다. */
    @Test
    void create_rejectsTokenIssuedToAnotherUser() {
        TeamCreateRequest othersDocument = new TeamCreateRequest(
                "루비 광고 3팀", "루비 광고", "홍길동", "495-92-40582", "2024-06-24",
                uploadTokenSigner.sign(S3_KEY, userId + 999, "광고물제작", "광고대행"));

        assertThatThrownBy(() -> teamCreateService.create(userId, othersDocument))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));

        assertNothingWasCreated();
    }

    /**
     * <b>OCR이 100자를 넘는 업태를 읽어와도 팀 생성이 터지면 안 된다.</b>
     *
     * 업태·종목은 이 플로우에서 유일하게 요청 DTO를 거치지 않는 값이라(OCR 원문이 서명 토큰으로
     * 들어온다) @Size 검증을 받지 않는다. OCR이 줄을 잘못 합치면 200자짜리 업태가 그대로 온다.
     * 자르지 않으면 insert가 "Value too long for column BUSINESS_TYPE"으로 터지고, 그 사용자는
     * <b>팀을 영영 못 만든다</b> - 업태는 화면에 입력란이 없어 고칠 수단이 없고, 같은 문서를 다시
     * 올려도 OCR이 같은 값을 뱉기 때문이다.
     */
    @Test
    void create_truncatesOverlongOcrBusinessTypeInsteadOfCrashing() {
        given(verifier.check(any())).willReturn(activeAndValid());

        // OCR 노이즈로 아주 긴 업태가 나왔다. 앞부분에 "광고대행"이 있어 광고업 판정은 통과한다.
        String overlong = "광고대행" + "가".repeat(200);

        TeamCreateResponse response = teamCreateService.create(userId, request(overlong, "광고대행"));

        TeamBusinessRegistration saved =
                registrationRepository.findByTeamId(response.teamId()).orElseThrow();
        assertThat(saved.getBusinessType())
                .as("컬럼 길이(100)에 맞춰 잘려서 저장돼야 한다")
                .hasSize(100)
                .isEqualTo(overlong.substring(0, 100));

        // 판정은 자르기 전 원문으로 이미 끝났으므로 광고업 매칭에는 영향이 없다.
        assertThat(saved.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
    }

    /**
     * <b>사용자는 팀 하나에만 속한다.</b> UserMeResponse가 teamId를 하나만 반환하는 것도 이 가정 위에
     * 있어서, 막지 않으면 두 번째 팀은 만들어지되 사용자가 접근할 수 없는 유령 팀이 된다.
     *
     * 화면이 "소속 팀 없는 사용자"에게만 노출된다고 해서 서버가 안 막아도 되는 건 아니다 -
     * API는 직접 호출할 수 있다.
     */
    @Test
    void create_rejectsSecondTeamForTheSameUser() {
        given(verifier.check(any())).willReturn(activeAndValid());
        teamCreateService.create(userId, request("광고물제작", "광고대행"));

        assertThatThrownBy(() -> teamCreateService.create(userId, request("광고물제작", "광고대행")))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.ALREADY_HAS_TEAM));

        assertThat(teamRepository.findAll()).hasSize(1);
    }

    /** 팀이 이미 있으면 국세청을 부르기 전에 막는다 (외부 API 쿼터를 아낀다). */
    @Test
    void create_doesNotCallNtsWhenTheUserAlreadyHasATeam() {
        given(verifier.check(any())).willReturn(activeAndValid());
        teamCreateService.create(userId, request("광고물제작", "광고대행"));
        org.mockito.Mockito.clearInvocations(verifier);

        assertThatThrownBy(() -> teamCreateService.create(userId, request("광고물제작", "광고대행")))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.ALREADY_HAS_TEAM));

        verify(verifier, never()).check(any());
    }

    /** 팀을 나온 사람은 새 팀을 만들 수 있어야 한다 (LEFT/REMOVED는 세지 않는다). */
    @Test
    void create_allowsANewTeamAfterLeavingTheOldOne() {
        given(verifier.check(any())).willReturn(activeAndValid());
        Long firstTeamId = teamCreateService.create(userId, request("광고물제작", "광고대행")).teamId();

        TeamMember membership = teamMemberRepository.findAll().getFirst();
        membership.changeStatus(TeamMemberStatus.LEFT);
        teamMemberRepository.saveAndFlush(membership);

        Long secondTeamId = teamCreateService.create(userId, request("광고물제작", "광고대행")).teamId();

        assertThat(secondTeamId).isNotEqualTo(firstTeamId);
    }

    /** 검증에 실패하면 teams/team_members/team_business_registrations 어디에도 아무것도 남지 않는다. */
    private void assertNothingWasCreated() {
        assertThat(teamRepository.findAll()).isEmpty();
        assertThat(teamMemberRepository.findAll()).isEmpty();
        assertThat(registrationRepository.findAll()).isEmpty();
    }

    private static java.util.function.Consumer<Throwable> hasErrorCode(BaseErrorCode expected) {
        return throwable -> {
            assertThat(throwable).isInstanceOf(GeneralException.class);
            assertThat(((GeneralException) throwable).getErrorCode()).isEqualTo(expected);
        };
    }

    private static BusinessCheckResult activeAndValid() {
        return BusinessCheckResult.of(
                CertificateValidity.VALID, null, "01", "계속사업자", "부가가치세 일반과세자", null);
    }

    /** 업태·종목은 요청이 아니라 서명 토큰으로 들어간다. */
    private TeamCreateRequest request(String businessType, String businessItem) {
        return new TeamCreateRequest(
                "루비 광고 3팀", "루비 광고", "홍길동 외 1명", "495-92-40582", "2024-06-24",
                signedToken(businessType, businessItem));
    }

    private String signedToken(String businessType, String businessItem) {
        return uploadTokenSigner.sign(S3_KEY, userId, businessType, businessItem);
    }
}
