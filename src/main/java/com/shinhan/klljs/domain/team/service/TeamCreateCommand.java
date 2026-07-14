package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadToken;
import com.shinhan.klljs.global.util.Texts;

/**
 * 팀 생성 한 건의 처리에 필요한 값 전부.
 *
 * 사업자 정보는 두 곳에서 온다.
 * <ul>
 *   <li>요청 본문: 사용자가 화면에서 OCR 결과를 확인하고 제출한 값 (팀명·사업자명·대표자명·번호·개업일)</li>
 *   <li>서명 토큰: 서버가 업로드 시점에 서명해 둔 값 (실제 S3 키, OCR이 읽은 업태·종목)</li>
 * </ul>
 *
 * @param businessNumber      사용자가 확인한 사업자등록번호 (원본 문자열. 저장 시 형태만 정리한다)
 * @param businessOpeningDate 사용자가 확인한 개업일 (원본 문자열. 저장 시 날짜로 파싱을 시도한다)
 * @param businessType        OCR이 읽은 업태. 토큰에서 꺼내므로 전송 구간에서 변조될 수 없다
 * @param businessItem        OCR이 읽은 종목. businessType과 같은 경로로 온다
 * @param documentS3Key       토큰에서 꺼낸 실제 S3 키. DB에는 이 값만 저장한다
 */
public record TeamCreateCommand(
        Long userId,
        String teamName,
        String companyName,
        String representativeName,
        String businessNumber,
        String businessOpeningDate,
        String businessType,
        String businessItem,
        String documentS3Key
) {

    public static TeamCreateCommand of(
            Long userId, TeamCreateRequest request, BusinessRegistrationUploadToken token) {

        // 사용자가 타이핑한 이름 세 개는 앞뒤 공백을 제거하고 저장한다 (team-creation-api-spec.md 5절).
        // @NotBlank는 "공백뿐인 값"만 막을 뿐 " 루비 광고 "는 통과시킨다.
        return new TeamCreateCommand(
                userId,
                Texts.trim(request.teamName()),
                Texts.trim(request.companyName()),
                Texts.trim(request.representativeName()),
                request.businessNumber(),
                request.businessOpeningDate(),
                token.businessType(),
                token.businessItem(),
                token.s3Key());
    }
}
