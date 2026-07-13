package com.shinhan.klljs.domain.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 팀 생성 요청 (docs/team-creation-api-spec.md 5절).
 *
 * <h3>여기는 @NotBlank를 건다 (재제출 API와 다르다)</h3>
 * 재제출 API는 사업자번호·대표자명·개업일이 비어도 400이 아니라 판정 규칙 1번(review_required)으로
 * 흘려보낸다 - 이미 있는 행에 "검토 필요" 상태를 남기는 것이 의미가 있기 때문이다.
 *
 * 팀 생성은 다르다. 검증을 통과하지 못하면 <b>어떤 row도 만들지 않고</b> 400을 낸다. 화면에서도
 * 전부 필수 입력이라, 빈 값을 굳이 국세청까지 들고 갈 이유가 없다 - 여기서 바로 막는다.
 *
 * 비어 있지는 않은데 형식이 틀린 경우(9자리 사업자번호, 20240631 같은 없는 날짜)는 @NotBlank를
 * 통과해 판정 규칙 1번까지 가고, 거기서도 400이 된다. 결과적으로 둘 다 400이라 일관된다.
 *
 * <h3>업태·종목·소재지는 받지 않는다</h3>
 * 업태·종목은 광고업 판단의 유일한 근거인데 국세청이 확인해주지 않는다. 폼으로 받으면
 * <b>음식점업 사업자가 "광고대행"으로 고쳐 제출해 스스로를 승인시킬 수 있다</b>
 * (server-verification-spec.md §7). OCR이 읽은 원본을 {@code documentStorageKey} 서명 토큰에서 꺼내 쓴다.
 *
 * @param documentStorageKey 업로드 API가 내려준 서명 토큰. 원본 S3 키가 아니다
 */
public record TeamCreateRequest(

        @NotBlank(message = "팀명을 입력해 주세요.")
        @Size(max = 200, message = "팀명은 200자를 넘을 수 없습니다.")
        String teamName,

        @NotBlank(message = "사업자명을 입력해 주세요.")
        @Size(max = 200, message = "사업자명은 200자를 넘을 수 없습니다.")
        String companyName,

        @NotBlank(message = "대표자명을 입력해 주세요.")
        @Size(max = 100, message = "대표자명은 100자를 넘을 수 없습니다.")
        String representativeName,

        @NotBlank(message = "사업자등록번호를 입력해 주세요.")
        @Size(max = 20, message = "사업자등록번호는 20자를 넘을 수 없습니다.")
        String businessNumber,

        @NotBlank(message = "개업일자를 입력해 주세요.")
        @Size(max = 20, message = "개업일자는 20자를 넘을 수 없습니다.")
        String businessOpeningDate,

        @NotBlank(message = "사업자등록증 파일 정보가 없습니다.")
        @Size(max = 1024, message = "문서 키는 1024자를 넘을 수 없습니다.")
        String documentStorageKey
) {
}
