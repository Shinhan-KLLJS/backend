package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.BusinessRegistrationSubmitRequest;
import com.shinhan.klljs.domain.team.dto.BusinessRegistrationSubmitResponse;
import com.shinhan.klljs.domain.team.entity.TeamBusinessRegistration;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.exception.BusinessRegistrationErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamBusinessRegistrationRepository;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadToken;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadTokenSigner;
import com.shinhan.klljs.domain.team.verification.BusinessVerificationResult;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 사업자등록증 제출 API의 진입점. 세 단계를 순서대로 엮는다.
 *
 * <ol>
 *   <li>제출 자격 확인 (팀 소속, 이미 승인됐는지)</li>
 *   <li>검증 (국세청 호출 포함, 트랜잭션 밖)</li>
 *   <li>판정 결과 적재 (짧은 쓰기 트랜잭션)</li>
 * </ol>
 *
 * <b>이 클래스에는 @Transactional을 걸지 않는다.</b> 2단계의 외부 HTTP 호출이 트랜잭션 안에 들어오면
 * 응답이 느릴 때 DB 커넥션을 그대로 붙잡고 있게 된다.
 */
@Service
@RequiredArgsConstructor
public class BusinessRegistrationSubmitService {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamBusinessRegistrationRepository registrationRepository;
    private final BusinessRegistrationUploadTokenSigner uploadTokenSigner;
    private final BusinessVerificationService verificationService;
    private final BusinessRegistrationWriteService writeService;

    public BusinessRegistrationSubmitResponse submit(
            Long userId, Long teamId, BusinessRegistrationSubmitRequest request) {

        ensureSubmittable(userId, teamId);

        // 업로드 토큰 검증. 국세청을 부르기 전에 끝낸다 (CPU만 쓰고 DB/외부 API와 무관하다).
        // 여기서 업태·종목과 실제 S3 키를 꺼낸다 - 사용자가 보낸 값이 아니라 서버가 서명한 값이다.
        BusinessRegistrationUploadToken token =
                uploadTokenSigner.verify(request.documentStorageKey(), userId);

        BusinessRegistrationSubmitCommand command =
                BusinessRegistrationSubmitCommand.of(userId, teamId, request, token);

        BusinessVerificationResult result = verificationService.verify(command.verification());
        TeamBusinessRegistration registration = writeService.save(command, result);

        return BusinessRegistrationSubmitResponse.of(registration, result);
    }

    /**
     * 국세청 API를 호출하기 전에 미리 걸러낸다 (빠른 실패 + 외부 API 쿼터 절약).
     * 최종 방어는 writeService의 쓰기 트랜잭션 안에서 한 번 더 한다.
     */
    private void ensureSubmittable(Long userId, Long teamId) {
        // LEFT(탈퇴)나 REMOVED(강퇴)된 멤버는 예전에 그 팀에 있었더라도 제출 권한이 없다.
        // 팀 자체가 없어도 같은 코드로 응답한다 - 팀의 존재 여부를 외부에 알려주지 않기 위해서다.
        TeamMemberRole role = teamMemberRepository
                .findRoleByUserIdAndTeamIdAndStatus(userId, teamId, TeamMemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(BusinessRegistrationErrorCode.NOT_TEAM_MEMBER));

        // 사업자등록증은 팀의 법적 신원 그 자체라 OWNER만 다룰 수 있다
        // (mvp-database-erd.md 4절 "권한 기준" - 사업자등록증이 OWNER 행에만 있다).
        if (role != TeamMemberRole.OWNER) {
            throw new GeneralException(BusinessRegistrationErrorCode.NOT_TEAM_OWNER);
        }

        // 이미 승인된 팀의 재제출은 막는다. 허용하면 잘못된 문서 한 번으로 APPROVED가 REJECTED로 퇴행한다 (§5.3).
        registrationRepository.findByTeamId(teamId)
                .filter(TeamBusinessRegistration::isApproved)
                .ifPresent(approved -> {
                    throw new GeneralException(BusinessRegistrationErrorCode.ALREADY_APPROVED);
                });
    }
}
