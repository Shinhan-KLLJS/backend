package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
import com.shinhan.klljs.domain.team.dto.TeamCreateResponse;
import com.shinhan.klljs.domain.team.entity.TeamBusinessRegistration;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamBusinessRegistrationRepository;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadTokenSigner;
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
import static org.mockito.BDDMockito.given;

/**
 * 팀 생성 (docs/team-creation-api-spec.md 5절).
 *
 * 핵심 계약: <b>진위확인·자동 판정 없이</b>, 사용자가 화면에서 확인한 OCR 값 그대로
 * 팀 + OWNER + 사업자등록이 한 번에 만들어진다. 서버가 막는 것은 위조된 토큰과 중복 팀뿐이다.
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

    /** 팀 + OWNER + 사업자등록이 한 번에 만들어지고, 확인한 값이 그대로 저장된다. */
    @Test
    void create_makesTeamOwnerAndRegistrationFromConfirmedValues() {
        TeamCreateResponse response = teamCreateService.create(userId, request("광고물제작", "광고대행"));

        assertThat(response.teamId()).isNotNull();
        assertThat(response.teamName()).isEqualTo("루비 광고 3팀");

        assertThat(teamRepository.findById(response.teamId()).orElseThrow().getStatus())
                .isEqualTo(TeamStatus.ACTIVE);

        // 팀을 만든 사람이 OWNER다. 초대로 들어온 게 아니므로 joinedViaInvite는 null이다.
        TeamMember owner = teamMemberRepository.findAll().getFirst();
        assertThat(owner.getRole()).isEqualTo(TeamMemberRole.OWNER);
        assertThat(owner.getStatus()).isEqualTo(TeamMemberStatus.ACTIVE);
        assertThat(owner.getJoinedViaInvite()).isNull();

        TeamBusinessRegistration registration =
                registrationRepository.findByTeamId(response.teamId()).orElseThrow();
        // 형태만 정리해서 저장한다 (하이픈 제거, DATE 변환).
        assertThat(registration.getBusinessNumber()).isEqualTo("4959240582");
        assertThat(registration.getBusinessOpeningDate()).isEqualTo(LocalDate.of(2024, 6, 24));
        // 업태·종목은 토큰(OCR 원본)에서 왔다. 사업장 소재지는 이 플로우에서 받지 않는다.
        assertThat(registration.getBusinessType()).isEqualTo("광고물제작");
        assertThat(registration.getBusinessItem()).isEqualTo("광고대행");
        assertThat(registration.getBusinessAddress()).isNull();
        // DB에는 서명 토큰이 아니라 순수 S3 키가 저장된다.
        assertThat(registration.getDocumentStorageKey()).isEqualTo(S3_KEY);
    }

    /**
     * <b>형식이 어긋난 사업자번호도 팀 생성을 막지 않는다.</b> 진위확인·판정이 없어진 MVP에서
     * 확인 화면의 목적은 "그대로 생성"이지 재검증이 아니다 - OCR이 한 자리를 놓친 번호로 400을 내면
     * 화면에서 고칠 방법이 없는 사용자는 팀을 영영 못 만든다. 숫자 10자리가 아니면 트림한 원본을
     * 그대로 저장한다.
     */
    @Test
    void create_keepsRawBusinessNumberWhenItIsNotTenDigits() {
        TeamCreateRequest nineDigits = new TeamCreateRequest(
                "루비 광고 3팀", "루비 광고", "홍길동", "12345", "2024-06-24",
                signedToken("광고물제작", "광고대행"));

        TeamCreateResponse response = teamCreateService.create(userId, nineDigits);

        assertThat(registrationRepository.findByTeamId(response.teamId()).orElseThrow()
                .getBusinessNumber()).isEqualTo("12345");
    }

    /** 개업일은 "yyyyMMdd"(OCR이 자주 뱉는 형태)도 받아들인다. */
    @Test
    void create_parsesCompactOpeningDate() {
        TeamCreateRequest compact = new TeamCreateRequest(
                "루비 광고 3팀", "루비 광고", "홍길동", "495-92-40582", "20240624",
                signedToken("광고물제작", "광고대행"));

        TeamCreateResponse response = teamCreateService.create(userId, compact);

        assertThat(registrationRepository.findByTeamId(response.teamId()).orElseThrow()
                .getBusinessOpeningDate()).isEqualTo(LocalDate.of(2024, 6, 24));
    }

    /**
     * 날짜로 읽을 수 없는 개업일은 <b>비워두고 팀은 만든다.</b> 400을 내지 않는 이유는
     * 사업자번호와 같다 - 컬럼이 NULL 허용이므로 형식 노이즈로 흐름 전체를 죽이지 않는다.
     * (미래 날짜도 막지 않는다 - 날짜 타당성 판단 자체가 검증이고, 검증은 MVP 범위 밖이다.)
     */
    @Test
    void create_storesNullOpeningDateWhenUnparseable() {
        TeamCreateRequest garbageDate = new TeamCreateRequest(
                "루비 광고 3팀", "루비 광고", "홍길동", "495-92-40582", "2024년 6월경",
                signedToken("광고물제작", "광고대행"));

        TeamCreateResponse response = teamCreateService.create(userId, garbageDate);

        assertThat(registrationRepository.findByTeamId(response.teamId()).orElseThrow()
                .getBusinessOpeningDate()).isNull();
    }

    /**
     * OCR이 업태·종목을 못 읽었어도(토큰에 null) 팀은 만들어진다.
     * 예전엔 광고업 분류의 입력이라 재업로드를 강제했지만, 분류 자체가 MVP에서 빠졌다.
     */
    @Test
    void create_allowsNullBusinessTypeFromOcr() {
        TeamCreateResponse response = teamCreateService.create(userId, request(null, null));

        TeamBusinessRegistration registration =
                registrationRepository.findByTeamId(response.teamId()).orElseThrow();
        assertThat(registration.getBusinessType()).isNull();
        assertThat(registration.getBusinessItem()).isNull();
    }

    /**
     * 사용자가 타이핑한 이름 세 개는 앞뒤 공백을 지우고 저장한다 (team-creation-api-spec.md 5절).
     * @NotBlank는 "공백뿐인 값"만 막을 뿐 " 루비 광고 "는 그대로 통과시켜서, 이 처리가 없으면
     * 공백이 붙은 채로 DB에 들어간다. 이름 <b>안쪽</b>의 공백은 건드리지 않는다.
     */
    @Test
    void create_trimsSurroundingWhitespaceOfUserTypedNames() {
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
     * OCR·복사 경로로 섞여 들어오는 <b>NBSP(U+00A0)·전각 공백(U+3000)</b>도 걷어낸다.
     * String.strip()과 자바 기본 \s는 NBSP를 지우지 못해, 화면상 멀쩡해 보이는 값이
     * 보이지 않는 문자 하나 때문에 10자리 통일에 실패하거나 개업일이 조용히 null이 된다.
     */
    @Test
    void create_normalizesValuesContainingUnicodeWhitespace() {
        TeamCreateRequest unicodePadded = new TeamCreateRequest(
                "루비 광고 3팀", " 루비 광고　", "홍길동",
                "495 92　40582", " 2024-06-24 ",
                signedToken("광고물제작", "광고대행"));

        TeamCreateResponse response = teamCreateService.create(userId, unicodePadded);

        TeamBusinessRegistration registration =
                registrationRepository.findByTeamId(response.teamId()).orElseThrow();
        assertThat(registration.getCompanyName()).isEqualTo("루비 광고");
        assertThat(registration.getBusinessNumber()).isEqualTo("4959240582");
        assertThat(registration.getBusinessOpeningDate()).isEqualTo(LocalDate.of(2024, 6, 24));
    }

    /** 위조된 토큰은 팀을 만들지 않고 400으로 막는다. */
    @Test
    void create_rejectsForgedToken() {
        TeamCreateRequest forged = new TeamCreateRequest(
                "루비 광고 3팀", "루비 광고", "홍길동", "495-92-40582", "2024-06-24",
                signedToken("광고물제작", "광고대행") + "tampered");

        assertThatThrownBy(() -> teamCreateService.create(userId, forged))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));

        assertNothingWasCreated();
    }

    /** 만료된 업로드 토큰도 위조와 똑같이 400이다 (사유를 구분해 주지 않는다 - 판별기 방지). */
    @Test
    void create_rejectsExpiredUploadToken() {
        // 토큰은 NOW에 서명되어 1시간 뒤 만료된다. 검증 시점을 그 뒤로 옮긴다.
        TeamCreateRequest request = request("광고물제작", "광고대행");
        given(clock.instant()).willReturn(NOW.plusSeconds(3601));

        assertThatThrownBy(() -> teamCreateService.create(userId, request))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.INVALID_UPLOAD_TOKEN));

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
        String overlong = "광고대행" + "가".repeat(200);

        TeamCreateResponse response = teamCreateService.create(userId, request(overlong, "광고대행"));

        TeamBusinessRegistration saved =
                registrationRepository.findByTeamId(response.teamId()).orElseThrow();
        assertThat(saved.getBusinessType())
                .as("컬럼 길이(100)에 맞춰 잘려서 저장돼야 한다")
                .hasSize(100)
                .isEqualTo(overlong.substring(0, 100));
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
        teamCreateService.create(userId, request("광고물제작", "광고대행"));

        assertThatThrownBy(() -> teamCreateService.create(userId, request("광고물제작", "광고대행")))
                .satisfies(hasErrorCode(BusinessRegistrationErrorCode.ALREADY_HAS_TEAM));

        assertThat(teamRepository.findAll()).hasSize(1);
    }

    /** 팀을 나온 사람은 새 팀을 만들 수 있어야 한다 (LEFT/REMOVED는 세지 않는다). */
    @Test
    void create_allowsANewTeamAfterLeavingTheOldOne() {
        Long firstTeamId = teamCreateService.create(userId, request("광고물제작", "광고대행")).teamId();

        TeamMember membership = teamMemberRepository.findAll().getFirst();
        membership.changeStatus(TeamMemberStatus.LEFT);
        teamMemberRepository.saveAndFlush(membership);

        Long secondTeamId = teamCreateService.create(userId, request("광고물제작", "광고대행")).teamId();

        assertThat(secondTeamId).isNotEqualTo(firstTeamId);
    }

    /** 요청이 거부되면 teams/team_members/team_business_registrations 어디에도 아무것도 남지 않는다. */
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
