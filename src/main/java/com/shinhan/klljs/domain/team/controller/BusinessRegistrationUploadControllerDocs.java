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
        name = "사업자등록증",
        description = "사업자등록증을 업로드해 OCR로 읽고, 사용자가 확인한 값으로 팀을 만드는 API"
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

                    ### 업태·종목은 응답과 토큰 양쪽에 실린다
                    업태(`businessType`)·종목(`businessItem`)도 다른 필드처럼 `ocrResult`로 내려가
                    화면에 보여줄 수 있다. 같은 값이 `documentStorageKey` 토큰 안에도 서명돼 있고,
                    **팀 생성 시 DB에 저장되는 것은 토큰 쪽 값이다** — 전송 구간에서 변조된 값이 아니라
                    OCR이 실제로 읽은 값임을 보증한다.

                    ### OCR 실패는 에러가 아니다
                    특정 필드를 못 읽거나 OCR이 통째로 실패해도 **업로드는 성공(200)** 이다.
                    실패한 필드는 `null`로 내려가고, 사용자가 직접 입력하면 된다.

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
                                          "businessOpeningDate": "2024-06-24",
                                          "businessType": "서비스업",
                                          "businessItem": "광고대행"
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
                                          "businessOpeningDate": null,
                                          "businessType": null,
                                          "businessItem": null
                                        }
                                      }
                                    }
                                    """)
                    })
            ),
            @ApiResponse(responseCode = "400", description = """
                    - `BUSINESS_400_002`: JPG/PNG/PDF가 아님 (매직바이트 기준. 확장자만 바꾼 파일 포함)
                    - `BUSINESS_400_003`: 파일 용량 초과 (10MB)
                    - `BUSINESS_400_004`: 파일이 첨부되지 않음
                    - `BUSINESS_400_005`: 파일이 손상되어 열 수 없음 (헤더만 흉내 낸 파일 포함)
                    - `BUSINESS_400_006`: 암호가 걸린 PDF
                    - `BUSINESS_400_007`: PDF 페이지 수 초과 (5쪽)
                    - `BUSINESS_400_008`: 이미지 해상도(픽셀 수) 초과 (30MP)"""),
            @ApiResponse(responseCode = "401", description = "`Authorization` 헤더의 액세스 토큰이 만료·위조됨"),
            @ApiResponse(responseCode = "429", description = "`BUSINESS_429_001`: 24시간 내 업로드 횟수 초과"),
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
