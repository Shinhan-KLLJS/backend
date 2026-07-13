package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
import com.shinhan.klljs.domain.team.dto.TeamCreateResponse;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.domain.team.exception.BusinessVerificationRejectionCode;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadToken;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadTokenSigner;
import com.shinhan.klljs.domain.team.verification.BusinessVerificationResult;
import com.shinhan.klljs.domain.team.verification.VerificationDecision;
import com.shinhan.klljs.domain.team.verification.VerificationDecisionStatus;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 팀 생성 API의 진입점 (docs/team-creation-api-spec.md 5절).
 *
 * <b>검증을 통과한 경우에만 팀이 만들어진다.</b> 유효하지 않은 사업자등록증으로 일단 팀을 만들고
 * 나중에 반려하는 것보다, 애초에 유효할 때만 팀이 생기는 쪽이 사용자 경험(그 자리에서 바로 알려줌)과
 * 데이터 정합성(반려된 팀이 DB에 남지 않음) 모두에 낫다.
 *
 * <b>@Transactional을 걸지 않는다.</b> 국세청 호출이 수 초 걸릴 수 있는데, 그동안 DB 커넥션을
 * 붙잡고 있으면 팀 생성과 무관한 요청까지 커넥션 풀을 기다리게 된다. 쓰기는 TeamCreateWriteService가
 * 짧은 트랜잭션으로 맡는다.
 */
@Service
@RequiredArgsConstructor
public class TeamCreateService {

    private final TeamMemberRepository teamMemberRepository;
    private final BusinessRegistrationUploadTokenSigner uploadTokenSigner;
    private final BusinessVerificationService verificationService;
    private final TeamCreateWriteService writeService;

    /**
     * 사용자는 팀 하나에만 속한다. 이 화면 자체가 "소속 팀이 없는 사용자"에게만 노출되지만,
     * 프론트가 막는 것과 서버가 막는 것은 다른 문제다 - API는 직접 호출할 수 있다.
     */
    private void ensureHasNoTeam(Long userId) {
        if (!teamMemberRepository.findTeamIdsByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE).isEmpty()) {
            throw new GeneralException(BusinessRegistrationErrorCode.ALREADY_HAS_TEAM);
        }
    }

    /**
     * @throws GeneralException 토큰이 유효하지 않으면 BUSINESS_400_001,
     *                          검증을 통과하지 못하면 사유별 BUSINESS_400_1xx (row는 생기지 않는다)
     */
    public TeamCreateResponse create(Long userId, TeamCreateRequest request) {
        // 0. 이미 팀이 있으면 국세청을 부르기 전에 막는다 (빠른 실패 + 외부 API 쿼터 절약).
        //    권위적인 판단은 잠금을 잡은 쓰기 트랜잭션 안에서 한 번 더 한다.
        ensureHasNoTeam(userId);

        // 1. 업로드 토큰 검증. 여기서 업태·종목과 실제 S3 키를 꺼낸다.
        //    사용자가 보낸 값이 아니라 서버가 서명한 값이라 위조할 수 없다.
        BusinessRegistrationUploadToken token =
                uploadTokenSigner.verify(request.documentStorageKey(), userId);

        TeamCreateCommand command = TeamCreateCommand.of(userId, request, token);

        // 2. 국세청 진위·영업상태 확인 + 광고업 분류 (트랜잭션 밖).
        BusinessVerificationResult result = verificationService.verify(command.verification());
        ensureAccepted(result);

        // 3. 여기까지 왔으면 검증을 통과했다. 팀/OWNER/사업자등록을 한 트랜잭션으로 만든다.
        Team team = writeService.create(command);

        return TeamCreateResponse.of(team);
    }

    /**
     * accepted가 아니면 팀을 만들지 않고 400으로 막는다.
     *
     * rejected(반려)뿐 아니라 <b>review_required(검토 필요)도 막는다.</b> 수동 검토 대기열이 아직 없어서
     * PENDING 상태의 팀을 만들어두면 "PENDING인 동안 팀을 쓸 수 있는가"를 정해야 하는데, MVP에서는
     * 그 질문을 아예 피한다.
     */
    private static void ensureAccepted(BusinessVerificationResult result) {
        VerificationDecision decision = result.decision();
        if (decision.status() == VerificationDecisionStatus.ACCEPTED) {
            return;
        }

        // 사유별 코드로 응답해 프론트가 안내 문구를 다르게 띄울 수 있게 한다.
        // 상세 메시지(폐업일, 진위확인 실패 사유 등)는 errorDetail로 함께 내려간다.
        throw new GeneralException(
                BusinessVerificationRejectionCode.from(decision.reasonCode()), decision.message());
    }
}
