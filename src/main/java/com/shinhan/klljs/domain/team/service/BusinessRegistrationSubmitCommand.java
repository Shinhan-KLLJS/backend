package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.BusinessRegistrationSubmitRequest;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadToken;
import com.shinhan.klljs.domain.team.verification.BusinessVerificationCommand;
import com.shinhan.klljs.global.util.Texts;

/**
 * 사업자등록증 재제출 한 건의 처리에 필요한 값 전부.
 * HTTP 요청 DTO를 서비스 계층 안으로 끌고 들어오지 않기 위한 경계다.
 *
 * 검증에 쓰이는 값({@link BusinessVerificationCommand})과 적재에만 쓰이는 값(누가/어느 팀/어느 문서,
 * 사업자명)을 분리해 담는다 - 전자는 팀 생성 API도 그대로 재사용하고, 후자는 API마다 다르다.
 *
 * @param documentS3Key 토큰에서 꺼낸 <b>실제 S3 키</b>. DB에는 이 값만 저장한다
 *                      (서명 토큰은 API 레벨에만 존재하는 값이다)
 */
public record BusinessRegistrationSubmitCommand(
        Long userId,
        Long teamId,
        String companyName,
        String documentS3Key,
        BusinessVerificationCommand verification
) {

    /**
     * 요청 DTO와 <b>검증이 끝난</b> 업로드 토큰을 합쳐 커맨드를 만든다.
     *
     * 값의 출처가 둘로 나뉘는 것이 핵심이다.
     * <ul>
     *   <li>사용자가 확정한 값(사업자번호·대표자명·개업일·사업자명): 요청 DTO에서</li>
     *   <li><b>사용자가 손댈 수 없어야 하는 값(업태·종목)과 S3 키: 서명 토큰에서</b></li>
     * </ul>
     * 업태·종목을 폼으로 받으면 음식점업 사업자가 "광고대행"으로 고쳐 스스로를 승인시킬 수 있다
     * (server-verification-spec.md §7). 토큰은 서명돼 있어 값을 바꾸면 검증 단계에서 깨진다.
     */
    public static BusinessRegistrationSubmitCommand of(
            Long userId, Long teamId,
            BusinessRegistrationSubmitRequest request,
            BusinessRegistrationUploadToken token) {

        // 사용자가 타이핑한 이름은 앞뒤 공백을 제거하고 저장한다 (team-creation-api-spec.md 5절).
        return new BusinessRegistrationSubmitCommand(
                userId,
                teamId,
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
