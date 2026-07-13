package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamBusinessRegistration;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamBusinessRegistrationRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.team.verification.BusinessVerificationCommand;
import com.shinhan.klljs.domain.team.verification.BusinessVerificationResult;
import com.shinhan.klljs.domain.team.verification.NormalizedBusinessInput;
import com.shinhan.klljs.domain.team.verification.VerificationDecision;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 검증이 끝난 결과를 team_business_registrations에 한 번 UPSERT한다 (server-verification-spec.md §5).
 * team_id가 UNIQUE이므로 재제출은 자연히 UPDATE로 동작한다.
 *
 * 국세청 호출이 끝난 뒤에만 호출되는, 짧은 쓰기 트랜잭션 전용 서비스다.
 * BusinessVerificationService와 분리한 이유는 외부 HTTP 호출을 트랜잭션 밖에 두기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class BusinessRegistrationWriteService {

    private final TeamBusinessRegistrationRepository registrationRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * <h3>동시 요청 처리</h3>
     * 같은 팀에 제출이 두 건 동시에 오면 둘 다 깨진다.
     * <ul>
     *   <li><b>최초 제출 2건</b>: 둘 다 "registration 없음"을 읽고 각자 INSERT -> team_id UNIQUE
     *       위반으로 한쪽이 500</li>
     *   <li><b>재제출 2건</b>: MySQL 기본 격리수준(REPEATABLE READ)에서 각자의 스냅샷이 상대의 커밋을
     *       못 봐서, 아래 isApproved() 재확인이 무력화된다 (나중에 커밋하는 쪽이 이긴다)</li>
     * </ul>
     * 그래서 <b>항상 존재하는 teams 행을 먼저 잠가</b> 같은 팀의 요청을 한 줄로 세운다.
     * 잠금은 이 트랜잭션의 첫 DB 읽기여야 한다 - 이유는 {@code findByIdForUpdate}의 Javadoc 참고.
     *
     * 팀 생성 API에는 이 잠금이 필요 없다. 팀을 같은 트랜잭션에서 INSERT하므로 잠글 기존 행이
     * 없고, 서로 다른 팀이 생기니 team_id UNIQUE가 자연히 막아준다. 이 잠금은 재제출 전용이다.
     *
     * @throws GeneralException 이 트랜잭션에 들어오기 직전에 승인이 확정됐으면 BUSINESS_409_001
     */
    @Transactional
    public TeamBusinessRegistration save(
            BusinessRegistrationSubmitCommand command, BusinessVerificationResult result) {

        // (1) 팀 행 잠금. 반드시 이 트랜잭션의 첫 DB 읽기여야 한다.
        //     호출 전에 팀 소속을 이미 확인했으므로 여기서 비는 일은 사실상 없다 (경합 중 팀 삭제 등 극단적 경우).
        Team team = teamRepository.findByIdForUpdate(command.teamId())
                .orElseThrow(() -> new GeneralException(BusinessRegistrationErrorCode.NOT_TEAM_MEMBER));

        // (2) 잠금을 얻은 뒤의 첫 일반 조회 - 여기서 뜨는 스냅샷이 경쟁 트랜잭션의 커밋을 반영한다.
        TeamBusinessRegistration registration = registrationRepository.findByTeamId(command.teamId())
                .orElseGet(() -> newRegistration(team, command));

        // 제출 전 사전 확인과 이 트랜잭션 사이에 다른 요청이 승인을 끝냈을 수 있다.
        // 여기서 한 번 더 막지 않으면 APPROVED가 REJECTED로 퇴행한다 (§5.3).
        if (registration.isApproved()) {
            throw new GeneralException(BusinessRegistrationErrorCode.ALREADY_APPROVED);
        }

        BusinessVerificationCommand verification = command.verification();
        registration.updateSubmission(
                userRepository.getReferenceById(command.userId()),
                businessNumberToStore(verification, result),
                command.companyName(),
                verification.representativeName(),
                // 업태·종목은 사용자가 보낸 값이 아니라 OCR 원본이다 (서명 토큰에서 꺼냈다).
                verification.businessType(),
                verification.businessItem(),
                // 사업장 소재지는 이 플로우에서 받지 않는다 (화면에 없고 판정에도 안 쓴다).
                null,
                openingDateToStore(result),
                // DB에는 순수 S3 키만 남는다. 서명 토큰은 API 레벨에만 존재하는 값이다.
                command.documentS3Key());

        VerificationDecision decision = result.decision();
        registration.applyDecision(
                decision.status().toVerificationStatus(), decision.message(), LocalDateTime.now(clock));

        return registrationRepository.save(registration);
    }

    /**
     * 정규화에 성공했으면 숫자 10자리를, 실패했으면 사용자가 보낸 원문을 그대로 저장한다.
     * 원문을 남겨야 나중에 왜 review_required로 빠졌는지 조사할 수 있다.
     */
    private static String businessNumberToStore(
            BusinessVerificationCommand verification, BusinessVerificationResult result) {

        NormalizedBusinessInput normalized = result.normalizedInput();
        return (normalized != null) ? normalized.businessNumber() : verification.businessNumber();
    }

    /** DATE 컬럼에는 실재하는 날짜만 넣을 수 있다. 정규화에 실패했으면 저장하지 않는다. */
    private static LocalDate openingDateToStore(BusinessVerificationResult result) {
        NormalizedBusinessInput normalized = result.normalizedInput();
        return (normalized != null) ? normalized.openingDate() : null;
    }

    /**
     * 팀은 잠금 조회로 이미 손에 있으니 그대로 쓰고, 사용자는 FK만 세팅하면 되므로 프록시 참조만
     * 잡는다 (SELECT 한 번을 아낀다 - 팀 소속은 호출 전에 이미 확인했다).
     */
    private TeamBusinessRegistration newRegistration(Team team, BusinessRegistrationSubmitCommand command) {
        return TeamBusinessRegistration.pending(
                team,
                userRepository.getReferenceById(command.userId()),
                command.documentS3Key());
    }
}
