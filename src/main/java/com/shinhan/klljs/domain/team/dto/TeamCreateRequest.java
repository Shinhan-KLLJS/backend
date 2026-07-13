package com.shinhan.klljs.domain.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 팀 생성 요청 (docs/team-creation-api-spec.md 5절).
 *
 * 값은 업로드 API가 OCR로 읽어 내려준 것을 사용자가 화면에서 확인한 결과다.
 * 진위확인·자동 판정은 하지 않으므로, 여기서 거는 검사는 필수 입력(@NotBlank)과
 * 길이(@Size)뿐이다. 형식이 어긋난 값(9자리 번호, 없는 날짜)도 400이 아니다 -
 * 저장 시 형태만 정리하고 팀은 그대로 만들어진다 (TeamCreateWriteService 참고).
 *
 * <h3>업태·종목·소재지는 받지 않는다</h3>
 * 업태·종목은 OCR이 읽은 원본을 {@code documentStorageKey} 서명 토큰에서 꺼내 쓴다 -
 * 요청 본문으로 받으면 전송 구간에서 바뀐 값이 OCR 원본 행세를 할 수 있다.
 * 소재지는 이 플로우의 화면에 없다.
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
