package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.BusinessRegistrationSubmitRequest;
import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
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
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 같은 팀에 사업자등록증 제출이 동시에 두 건 들어왔을 때, teams 행 잠금이 정말로 이를 직렬화하는지
 * 실제 두 스레드로 검증한다 (RefreshTokenConcurrencyMySqlTest와 같은 패턴).
 *
 * <b>반드시 실제 MySQL로 검증한다. H2로는 이 버그가 재현되지 않는다.</b>
 * 테스트용 H2는 READ COMMITTED로 돌고 MySQL InnoDB는 REPEATABLE READ가 기본인데, 잡으려는 버그가
 * 정확히 REPEATABLE READ에서만 나타난다 - 트랜잭션이 첫 읽기에서 뜬 스냅샷이 고정돼 상대의 커밋을
 * 못 보기 때문이다. H2에서는 매 쿼리가 새 스냅샷을 떠서, 잠금을 지워도 테스트가 통과해버린다
 * (통과하는데 아무것도 보장하지 못하는 테스트가 된다).
 *
 * 실제로 확인한 결과, {@code @Lock}을 지우면 최초 제출 경합에서 이렇게 터진다:
 * <pre>
 * DataIntegrityViolationException: Duplicate entry '2' for key
 *   'team_business_registrations.UKrdjmxdfdkw2ta14mwyl07g0ib'
 * </pre>
 * 사용자에게는 500으로 나간다.
 *
 * 실행:
 * <pre>
 * docker compose -f docker-compose.test.yml up -d --wait
 * ./gradlew mysqlConcurrencyTest
 * </pre>
 * 기본 './gradlew test'에서는 제외된다.
 */
@Tag("mysql-concurrency")
@SpringBootTest
class BusinessRegistrationConcurrencyMySqlTest {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:mysql://localhost:13308/klljs_concurrency_test?allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "root");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

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

    @Autowired
    private BusinessRegistrationUploadTokenSigner uploadTokenSigner;

    @Autowired
    private TeamCreateService teamCreateService;

    /** 국세청은 부르지 않는다. 여기서 보는 것은 DB 잠금이지 판정 로직이 아니다. */
    @MockitoBean
    private BusinessRegistrationVerifier verifier;

    private Long userId;
    private Long teamId;

    @BeforeEach
    void setUp() {
        registrationRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();

        User owner = userRepository.save(
                User.builder().displayName("동시성테스트").status(UserStatus.ACTIVE).build());
        Team team = teamRepository.save(
                Team.builder().teamName("루비 광고").status(TeamStatus.ACTIVE).build());
        teamMemberRepository.save(TeamMember.builder()
                .team(team).user(owner)
                .role(TeamMemberRole.OWNER).status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build());

        this.userId = owner.getId();
        this.teamId = team.getId();
    }

    /**
     * <b>잠금이 없으면 UNIQUE 위반 500이 난다.</b> 둘 다 "registration 없음"을 읽고 각자 INSERT하는데,
     * REPEATABLE READ라 상대의 커밋이 스냅샷에 보이지 않기 때문이다.
     * (H2로는 이 버그가 재현되지 않는다 - READ COMMITTED라 매 쿼리가 새 스냅샷을 뜬다.)
     *
     * 잠금이 걸리면 뒤에 처리되는 요청이 앞선 요청이 커밋한 행을 <b>보게 되어</b>:
     * <ul>
     *   <li>앞선 요청: INSERT -> APPROVED</li>
     *   <li>뒤선 요청: 그 APPROVED 행을 보고 409로 거부 (§5.3 승인 퇴행 방지)</li>
     * </ul>
     * 뒤선 요청이 409를 받는 것은 <b>정상이다</b> - 이미 승인된 정보를 덮어쓰지 않겠다는 뜻이다.
     * 여기서 지키려는 것은 "UNIQUE 위반 500이 새어 나가지 않는다"와 "행이 정확히 1개"다.
     */
    @Test
    void firstSubmit_underConcurrentRequests_isSerializedWithoutAUniqueViolation() throws Exception {
        given(verifier.check(any())).willReturn(activeAndValid());

        List<Outcome> outcomes = submitTwiceConcurrently(request("광고물제작", "광고대행"));

        // 핵심: UNIQUE 위반이 500으로 새어 나가면 안 된다. 도메인 예외(409)는 정상 응답이다.
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error())
                .as("동시 최초 제출이 UNIQUE 위반(DataIntegrityViolationException)으로 실패하면 안 된다")
                .satisfiesAnyOf(
                        error -> assertThat(error).isNull(),
                        error -> assertThat(error).isInstanceOf(GeneralException.class)));

        // 정확히 한 요청만 승인시키고, 다른 하나는 그 승인을 보고 물러난다.
        assertThat(outcomes.stream().filter(o -> o.error() == null).count())
                .as("한 요청은 성공해야 한다")
                .isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> o.error() != null).toList())
                .allSatisfy(o -> assertThat(errorCodeOf(o))
                        .as("진 요청은 '이미 승인됨'으로 물러난다 - 승인을 반려로 덮지 않는다")
                        .isEqualTo(BusinessRegistrationErrorCode.ALREADY_APPROVED));

        assertThat(registrationRepository.findAll()).hasSize(1);
        assertThat(registrationRepository.findByTeamId(teamId).orElseThrow().getVerificationStatus())
                .isEqualTo(VerificationStatus.APPROVED);
    }

    /**
     * 이미 승인된 팀에 재제출 2건이 동시에 오면 둘 다 409로 막히고 승인이 살아남는다.
     *
     * <b>주의: 이 테스트는 잠금의 가드가 아니다.</b> 잠금을 지워도 통과한다 - 확인해봤다.
     * 이미 APPROVED인 팀은 트랜잭션에 들어가기도 전에 {@code ensureSubmittable()} 사전 검사가
     * 막아서, save()의 잠금까지 도달하지 않기 때문이다. 여기서 검증하는 것은 <b>사전 검사가
     * 동시 요청에서도 제대로 동작하고 승인 상태가 흔들리지 않는다</b>는 것뿐이다.
     *
     * 잠금이 실제로 필요한 경로는 위의 firstSubmit 테스트다 (거기서는 잠금을 지우면 UNIQUE 위반
     * 500이 그대로 터진다).
     */
    @Test
    void resubmitToApprovedTeam_underConcurrentRequests_isRejectedAndApprovalSurvives() throws Exception {
        given(verifier.check(any())).willReturn(activeAndValid());
        submitService.submit(userId, teamId, request("광고물제작", "광고대행"));
        assertThat(registrationRepository.findByTeamId(teamId).orElseThrow().getVerificationStatus())
                .isEqualTo(VerificationStatus.APPROVED);

        // 광고업이 아닌 문서로 동시에 재제출 - 막히지 않으면 APPROVED가 REJECTED로 퇴행한다.
        List<Outcome> outcomes = submitTwiceConcurrently(request("음식점업", "한식"));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(errorCodeOf(outcome))
                .as("이미 승인된 팀의 재제출은 409로 막혀야 한다")
                .isEqualTo(BusinessRegistrationErrorCode.ALREADY_APPROVED));

        TeamBusinessRegistration saved = registrationRepository.findByTeamId(teamId).orElseThrow();
        assertThat(saved.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(saved.getVerifiedAt()).isNotNull();
    }

    /**
     * <b>사용자가 팀 생성을 더블클릭하면?</b> 사용자는 팀 하나에만 속해야 하는데, 이를 강제하는 DB 제약이
     * 없다(team_members의 UNIQUE는 팀 단위라 여러 팀에 걸친 중복은 못 막는다). 애플리케이션이 세야 한다.
     *
     * 잠금이 없으면 두 요청이 동시에 "소속 팀 없음"을 읽고 각자 팀을 만들어 <b>팀이 두 개 생긴다.</b>
     * 그런데 UserMeResponse는 teamId를 하나만 반환하므로, 두 번째 팀은 사용자가 영영 접근할 수 없는
     * 유령 팀이 된다. users 행을 잠가 한 사용자의 팀 생성을 한 줄로 세운다.
     */
    @Test
    void createTeam_underConcurrentRequests_makesExactlyOneTeam() throws Exception {
        given(verifier.check(any())).willReturn(activeAndValid());

        // 팀이 없는 새 사용자 (setUp의 owner는 이미 팀이 있다).
        User loner = userRepository.save(
                User.builder().displayName("팀없는사람").status(UserStatus.ACTIVE).build());
        TeamCreateRequest request = new TeamCreateRequest(
                "루비 광고", "루비 광고", "홍길동", "495-92-40582", "2024-06-24",
                uploadTokenSigner.sign("team-registrations/doc.pdf", loner.getId(), "광고물제작", "광고대행"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Void> attemptCreate = () -> {
            start.await();
            teamCreateService.create(loner.getId(), request);
            return null;
        };

        Future<Void> first = executor.submit(attemptCreate);
        Future<Void> second = executor.submit(attemptCreate);
        start.countDown();

        int created = 0;
        for (Future<Void> future : List.of(first, second)) {
            try {
                future.get(10, TimeUnit.SECONDS);
                created++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(GeneralException.class);
                assertThat(((GeneralException) e.getCause()).getErrorCode())
                        .isEqualTo(BusinessRegistrationErrorCode.ALREADY_HAS_TEAM);
            }
        }
        executor.shutdown();

        assertThat(created).as("동시 요청 2건 중 하나만 팀을 만들어야 한다").isEqualTo(1);
        assertThat(teamMemberRepository.findTeamIdsByUserIdAndStatus(loner.getId(), TeamMemberStatus.ACTIVE))
                .as("한 사용자에게 팀이 두 개 생기면 안 된다 - 두 번째는 접근 불가능한 유령 팀이 된다")
                .hasSize(1);
    }

    /** 같은 요청을 두 스레드에서 동시에 쏘고, 각각 성공했는지 어떤 예외로 실패했는지 모아 온다. */
    private List<Outcome> submitTwiceConcurrently(BusinessRegistrationSubmitRequest request) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        Callable<Void> attemptSubmit = () -> {
            startLatch.await();
            submitService.submit(userId, teamId, request);
            return null;
        };

        Future<Void> first = executor.submit(attemptSubmit);
        Future<Void> second = executor.submit(attemptSubmit);
        startLatch.countDown();

        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Void> future : List.of(first, second)) {
            try {
                future.get(10, TimeUnit.SECONDS);
                outcomes.add(new Outcome(null));
            } catch (ExecutionException e) {
                outcomes.add(new Outcome(e.getCause()));
            }
        }
        executor.shutdown();
        return outcomes;
    }

    private static BusinessRegistrationErrorCode errorCodeOf(Outcome outcome) {
        assertThat(outcome.error()).isInstanceOf(GeneralException.class);
        return (BusinessRegistrationErrorCode) ((GeneralException) outcome.error()).getErrorCode();
    }

    private static BusinessCheckResult activeAndValid() {
        return BusinessCheckResult.of(
                CertificateValidity.VALID, null, "01", "계속사업자", "부가가치세 일반과세자", null);
    }

    /** 업태·종목은 요청 본문이 아니라 서명 토큰에 담긴다 (server-verification-spec.md §7). */
    private BusinessRegistrationSubmitRequest request(String businessType, String businessItem) {
        return new BusinessRegistrationSubmitRequest(
                "495-92-40582", "루비 광고", "홍길동 외 1명", "2024-06-24",
                uploadTokenSigner.sign("team-registrations/doc.pdf", userId, businessType, businessItem));
    }

    /** 성공이면 error가 null, 실패면 그 원인 예외. */
    private record Outcome(Throwable error) {
    }
}
