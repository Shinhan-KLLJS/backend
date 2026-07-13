package com.shinhan.klljs.domain.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사업자등록증 검증 제출 요청. 모두 <b>사용자가 화면에서 확인·수정해 확정한 값</b>이며,
 * OCR이 준 추정값을 그대로 쓰지 않는다.
 *
 * <h3>왜 businessNumber/businessOpeningDate/representativeName에 @NotBlank가 없는가</h3>
 * 이 세 값은 국세청 진위확인의 입력이고, 누락·형식오류일 때의 동작이 스펙 §4의 1번 규칙으로
 * 이미 정해져 있다 - 400이 아니라 <b>review_required(VALIDATION_INPUT_INCOMPLETE)</b>다.
 * @NotBlank를 걸면 이 규칙에 도달하기 전에 Spring이 400을 내버려서 판정 경로가 달라진다.
 * 형식 검사는 BusinessInputNormalizer가 담당하고, 여기서는 DB 컬럼 길이만 지킨다.
 *
 * <h3>업태·종목은 여기 없다</h3>
 * 광고업 분류의 유일한 입력인데 국세청이 확인해주지 않는다. 요청 본문으로 받으면
 * <b>실제로는 음식점업이어도 업태를 "광고대행"으로 고쳐 제출해 스스로를 승인시킬 수 있다</b>
 * (server-verification-spec.md §7). 그래서 OCR이 읽은 원본을 {@code documentStorageKey} 서명 토큰
 * payload에 넣어 나르고, 서버가 검증 후 꺼내 쓴다 - 값을 바꾸면 서명이 깨진다.
 *
 * 사업장 소재지도 화면에 입력란이 없고 판정에도 쓰이지 않아 받지 않는다 (DB에는 null로 남는다).
 *
 * @param businessNumber      사업자등록번호. 하이픈이 있어도 되고 서버가 숫자만 남긴다
 * @param businessOpeningDate 개업일자. {@code YYYYMMDD} 또는 {@code yyyy-MM-dd} 모두 허용
 * @param documentStorageKey  업로드 API가 내려준 서명 토큰. 원본 S3 키가 아니다
 */
public record BusinessRegistrationSubmitRequest(

        @Size(max = 20, message = "사업자등록번호는 20자를 넘을 수 없습니다.")
        String businessNumber,

        @NotBlank(message = "사업자명을 입력해 주세요.")
        @Size(max = 200, message = "사업자명은 200자를 넘을 수 없습니다.")
        String companyName,

        @Size(max = 100, message = "대표자명은 100자를 넘을 수 없습니다.")
        String representativeName,

        @Size(max = 20, message = "개업일자는 20자를 넘을 수 없습니다.")
        String businessOpeningDate,

        @NotBlank(message = "사업자등록증 파일 정보가 없습니다.")
        @Size(max = 1024, message = "문서 키는 1024자를 넘을 수 없습니다.")
        String documentStorageKey
) {
}
