package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.dto.BusinessRegistrationUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@link BusinessRegistrationUploadController}의 Swagger 문서 전용 인터페이스.
 */
@Tag(
        name = "사업자등록증 검증",
        description = "사용자가 확정한 사업자등록증 정보를 국세청에 대조해 승인/반려/검토필요를 판정하는 API"
)
public interface BusinessRegistrationUploadControllerDocs {

    @Operation(
            summary = "사업자등록증 업로드 (OCR 포함)",
            description = """
                    파일을 비공개 스토리지에 저장하고, OCR로 읽어낸 값을 즉시 응답한다.
                    아직 팀이 없는 시점이라 **DB에는 아무것도 저장하지 않는다.**

                    ### 다음 단계
                    프론트는 `ocrResult`로 폼을 채워 사용자에게 보여주고, 사용자가 확인·수정한 값과
                    **`documentStorageKey`를 그대로** 팀 생성 API(`POST /api/v1/teams`)에 실어 보낸다.

                    ### documentStorageKey는 원본 파일 경로가 아니다
                    서버가 서명한 불투명 토큰이다. **프론트는 내용을 해석할 필요가 없고, 해석해서도 안 된다.**
                    받은 문자열을 그대로 되돌려 보내기만 하면 된다. 값을 조작하면 서명이 깨져 400이 난다.
                    유효 기간은 발급 후 **1시간**이다.

                    ### ⚠️ 업태·종목은 응답에 없다
                    OCR은 업태(`businessType`)와 종목(`businessItem`)도 읽지만 **응답에 담지 않는다.**
                    이 둘은 광고업 판단의 유일한 근거인데 국세청이 확인해주지 않아서, 화면에서 수정할 수 있게
                    하면 실제로는 음식점업인 사업자가 "광고대행"으로 고쳐 제출해 스스로를 승인시킬 수 있다.
                    그래서 `documentStorageKey` 토큰 안에 서명해 넣어 나른다 — 값을 바꾸면 서명이 깨진다.

                    ### OCR 실패는 에러가 아니다
                    특정 필드를 못 읽거나 OCR이 통째로 실패해도 **업로드는 성공(200)** 이다.
                    실패한 필드는 `null`로 내려가고, 사용자가 직접 입력하면 된다.
                    (다만 업태·종목을 못 읽으면 광고업 판단 근거가 없어 팀 생성이 `400`으로 막힌다 —
                    이때는 문서를 다시 업로드해 OCR을 재시도해야 한다.)

                    ### 파일 검증
                    확장자와 `Content-Type`은 믿지 않는다(클라이언트가 마음대로 붙일 수 있다).
                    **파일 앞부분의 매직바이트로 실제 형식을 판별**하므로, 확장자만 `.png`로 바꾼 파일은 거부된다.
                    허용 형식은 JPG·PNG·PDF, 최대 10MB다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "업로드 성공. OCR이 일부/전부 실패해도 200이다",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "OCR 성공", value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "성공적으로 요청을 처리했습니다.",
                                      "result": {
                                        "documentStorageKey": "v1.eyJwdXJwb3NlIjoiYnVzaW5lc3MtcmVnaXN0cmF0aW9uLXVwbG9hZCIsInMzS2V5IjoidGVhbS1yZWdpc3RyYXRpb25zLzNmMWE5YzJlLnBuZyJ9.9f2a1c",
                                        "ocrResult": {
                                          "companyName": "신한 KLLJS",
                                          "representativeName": "이정현",
                                          "businessNumber": "4959240582",
                                          "businessOpeningDate": "2024-06-24"
                                        }
                                      }
                                    }
                                    """),
                            @ExampleObject(name = "OCR 인식 실패 (업로드는 성공)", value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "성공적으로 요청을 처리했습니다.",
                                      "result": {
                                        "documentStorageKey": "v1.eyJwdXJwb3NlIjoi....9f2a1c",
                                        "ocrResult": {
                                          "companyName": null,
                                          "representativeName": null,
                                          "businessNumber": null,
                                          "businessOpeningDate": null
                                        }
                                      }
                                    }
                                    """)
                    })
            ),
            @ApiResponse(responseCode = "400", description = """
                    - `BUSINESS_400_002`: JPG/PNG/PDF가 아님 (매직바이트 기준. 확장자만 바꾼 파일 포함)
                    - `BUSINESS_400_003`: 파일 용량 초과 (10MB)
                    - `BUSINESS_400_004`: 파일이 첨부되지 않음"""),
            @ApiResponse(responseCode = "401", description = "`Authorization` 헤더의 액세스 토큰이 만료·위조됨"),
            @ApiResponse(responseCode = "500", description = """
                    - `BUSINESS_500_002`: 서버에 업로드 토큰 서명 키가 설정되지 않음
                    - `BUSINESS_500_003`: 스토리지 저장 실패 (인프라 문제. 재시도하면 된다)""")
    })
    com.shinhan.klljs.global.apiPayload.ApiResponse<BusinessRegistrationUploadResponse> uploadBusinessRegistration(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "사업자등록증 파일 (JPG/PNG/PDF, 최대 10MB)")
            MultipartFile file
    );
}
