package com.shinhan.klljs.domain.team.service;

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
import com.shinhan.klljs.domain.team.verification.BusinessInputNormalizer;
import com.shinhan.klljs.domain.team.verification.BusinessVerificationCommand;
import com.shinhan.klljs.domain.team.verification.NormalizedBusinessInput;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 팀·OWNER·사업자등록을 <b>한 트랜잭션으로</b> 만든다 (docs/team-creation-api-spec.md 5절).
 * 셋 중 하나라도 실패하면 전체 롤백된다 - OWNER 없는 팀이나 사업자등록 없는 팀이 남지 않는다.
 *
 * 검증(국세청 호출 포함)이 끝난 뒤에만 호출되는 짧은 쓰기 트랜잭션 전용 서비스다.
 *
 * <h3>여기엔 teams 행 잠금이 필요 없다</h3>
 * 재제출({@link BusinessRegistrationWriteService})은 항상 존재하는 teams 행을 잠가 동시 요청을
 * 직렬화하지만, 여기는 팀을 <b>이 트랜잭션 안에서 INSERT</b>하므로 잠글 기존 행이 없다.
 * 동시 요청이 와도 서로 다른 팀이 만들어지고, team_business_registrations의 team_id UNIQUE 제약이
 * 자연히 "팀당 사업자등록 1건"을 지켜준다.
 */
@Service
@RequiredArgsConstructor
public class TeamCreateWriteService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamBusinessRegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public Team create(TeamCreateCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);

        // (1) 사용자 행 잠금. 반드시 이 트랜잭션의 첫 DB 읽기여야 한다.
        //     한 사용자의 동시 팀 생성을 한 줄로 세워, 팀이 두 개 생기는 것을 막는다.
        User owner = userRepository.findByIdForUpdate(command.userId())
                .orElseThrow(() -> new GeneralException(BusinessRegistrationErrorCode.NOT_TEAM_MEMBER));

        // (2) 잠금 이후의 첫 일반 조회 - 경쟁 트랜잭션이 방금 만든 팀도 여기서 보인다.
        //     사전 검사(TeamCreateService)는 국세청 쿼터를 아끼기 위한 빠른 실패일 뿐이고,
        //     권위적인 판단은 여기서 한다.
        ensureHasNoTeam(command.userId());

        Team team = teamRepository.save(Team.builder()
                .teamName(command.teamName())
                .status(TeamStatus.ACTIVE)
                .build());

        // 팀을 만든 사람이 OWNER다. 초대로 들어온 게 아니므로 joinedViaInvite는 null이다.
        // 팀당 활성 OWNER 1명은 DB 유니크 인덱스(active_owner_marker)가 강제한다.
        teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .user(owner)
                .role(TeamMemberRole.OWNER)
                .status(TeamMemberStatus.ACTIVE)
                .joinedAt(now)
                .build());

        registrationRepository.save(approvedRegistration(team, owner, command, now));

        return team;
    }

    /**
     * 사용자는 팀 하나에만 속한다. UserMeResponse가 teamId를 하나만 반환하는 것도 이 가정 위에 있어서,
     * 막지 않으면 두 번째 팀은 만들어지되 <b>사용자가 접근할 수 없는 유령 팀</b>이 된다.
     *
     * LEFT/REMOVED된 팀은 세지 않는다 - 팀을 나온 사람은 새 팀을 만들 수 있어야 한다.
     */
    private void ensureHasNoTeam(Long userId) {
        if (!teamMemberRepository.findTeamIdsByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE).isEmpty()) {
            throw new GeneralException(BusinessRegistrationErrorCode.ALREADY_HAS_TEAM);
        }
    }

    /**
     * 검증을 이미 통과했으므로 PENDING을 거치지 않고 바로 APPROVED로 만든다
     * (통과하지 못했으면 TeamCreateService가 400으로 막아 여기 오지 않는다).
     */
    private TeamBusinessRegistration approvedRegistration(
            Team team, User owner, TeamCreateCommand command, LocalDateTime now) {

        BusinessVerificationCommand verification = command.verification();
        NormalizedBusinessInput normalized = normalize(verification, KstDateTimes.todayKst(now));

        TeamBusinessRegistration registration =
                TeamBusinessRegistration.pending(team, owner, command.documentS3Key());

        registration.updateSubmission(
                owner,
                // 숫자 10자리로 정규화된 값을 저장한다 (하이픈 없이).
                normalized.businessNumber(),
                command.companyName(),
                verification.representativeName(),
                // 업태·종목은 사용자가 보낸 값이 아니라 OCR 원본이다 (서명 토큰에서 꺼냈다).
                verification.businessType(),
                verification.businessItem(),
                // 사업장 소재지는 이 플로우에서 받지 않는다 (화면에 없고 판정에도 안 쓴다).
                null,
                normalized.openingDate(),
                command.documentS3Key());

        registration.applyDecision(VerificationStatus.APPROVED, null, now);
        return registration;
    }

    /**
     * 검증을 통과했다는 것은 정규화에도 성공했다는 뜻이다 - 실패했다면 판정 규칙 1번
     * (VALIDATION_INPUT_INCOMPLETE)에 걸려 이미 400으로 막혔을 것이다.
     * 그래서 여기서 정규화가 실패하면 판정 순서가 깨진 것이므로 조용히 넘기지 않고 터뜨린다.
     *
     * 개업일자를 저장해두는 이유: 진위확인에 필수인데, 승인된 사업자의 폐업 여부를 나중에 서버가
     * 스스로 재검증할 때 사용자에게 값을 다시 물을 수 없다 (server-verification-spec.md §5.1).
     */
    private static NormalizedBusinessInput normalize(
            BusinessVerificationCommand verification, LocalDate todayKst) {
        return BusinessInputNormalizer.normalize(
                        verification.businessNumber(),
                        verification.businessOpeningDate(),
                        verification.representativeName(),
                        todayKst)
                .orElseThrow(() -> new IllegalStateException(
                        "검증을 통과한 요청은 반드시 정규화에 성공한다. 판정 순서가 깨졌다."));
    }
}
