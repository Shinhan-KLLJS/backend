package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
import com.shinhan.klljs.domain.team.dto.TeamCreateResponse;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadToken;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadTokenSigner;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 팀 생성 API의 진입점 (docs/team-creation-api-spec.md 5절).
 *
 * <b>진위확인·자동 판정은 하지 않는다 (MVP 스코프).</b> 업로드 API가 OCR로 읽어 내려준 값을
 * 사용자가 화면에서 확인하고 제출하면, 그 값 그대로 팀 + OWNER + 사업자등록 정보를 만든다.
 * 서버가 검사하는 것은 업로드 토큰의 서명(내 파일이 맞는가)과 "이미 팀이 있는가"뿐이다.
 *
 * <b>@Transactional을 걸지 않는다.</b> 토큰 검증·값 정리는 DB가 필요 없는 작업이라,
 * 쓰기는 TeamCreateWriteService가 잠금을 포함한 짧은 트랜잭션으로만 맡는다.
 */
@Service
@RequiredArgsConstructor
public class TeamCreateService {

    private final TeamMemberRepository teamMemberRepository;
    private final BusinessRegistrationUploadTokenSigner uploadTokenSigner;
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
     *                          이미 팀이 있으면 BUSINESS_409_002
     */
    public TeamCreateResponse create(Long userId, TeamCreateRequest request) {
        // 0. 이미 팀이 있으면 빠르게 막는다. 권위적인 판단은 잠금을 잡은 쓰기 트랜잭션 안에서 한 번 더 한다.
        ensureHasNoTeam(userId);

        // 1. 업로드 토큰 검증. 여기서 업태·종목과 실제 S3 키를 꺼낸다.
        //    사용자가 보낸 값이 아니라 서버가 서명한 값이라 위조할 수 없다.
        BusinessRegistrationUploadToken token =
                uploadTokenSigner.verify(request.documentStorageKey(), userId);

        TeamCreateCommand command = TeamCreateCommand.of(userId, request, token);

        // 2. 팀/OWNER/사업자등록을 한 트랜잭션으로 만든다.
        Team team = writeService.create(command);

        return TeamCreateResponse.of(team);
    }
}
