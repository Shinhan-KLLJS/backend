package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadToken;
import com.shinhan.klljs.domain.team.verification.BusinessVerificationCommand;
import com.shinhan.klljs.global.util.Texts;

/**
 * 팀 생성 한 건의 처리에 필요한 값 전부.
 *
 * 재제출({@link BusinessRegistrationSubmitCommand})과 <b>같은
 * {@link BusinessVerificationCommand}를 품는다</b> - 그래서 두 API가 검증 로직(정규화, 국세청 조회,
 * 광고업 분류, 최종 판정)을 그대로 공유한다. 다른 것은 그 앞뒤뿐이다:
 * 팀 생성은 teamId가 없고 teamName이 있으며, 실패 시 row를 아예 만들지 않는다.
 *
 * @param documentS3Key 토큰에서 꺼낸 실제 S3 키. DB에는 이 값만 저장한다
 */
public record TeamCreateCommand(
        Long userId,
        String teamName,
        String companyName,
        String documentS3Key,
        BusinessVerificationCommand verification
) {

    /**
     * 업태·종목과 S3 키는 <b>검증이 끝난 서명 토큰</b>에서 온다. 요청 DTO에는 아예 없다 -
     * 사용자가 업태를 "광고대행"으로 고쳐 스스로를 승인시키는 것을 막기 위해서다
     * (server-verification-spec.md §7).
     */
    public static TeamCreateCommand of(
            Long userId, TeamCreateRequest request, BusinessRegistrationUploadToken token) {

        // 사용자가 타이핑한 이름 세 개는 앞뒤 공백을 제거하고 저장한다 (team-creation-api-spec.md 5절).
        // @NotBlank는 "공백뿐인 값"만 막을 뿐 " 루비 광고 "는 통과시킨다.
        return new TeamCreateCommand(
                userId,
                Texts.trim(request.teamName()),
                Texts.trim(request.companyName()),
                token.s3Key(),
                new BusinessVerificationCommand(
                        request.businessNumber(),
                        request.businessOpeningDate(),
                        Texts.trim(request.representativeName()),
                        token.businessType(),
                        token.businessItem()));
    }
}
