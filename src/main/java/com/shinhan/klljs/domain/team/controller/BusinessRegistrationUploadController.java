package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.dto.BusinessRegistrationUploadResponse;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationUploadService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사업자등록증 업로드 API (docs/team-creation-api-spec.md 4절).
 *
 * 아직 팀이 없는 시점이라 "인증된 사용자인가"만 확인한다 - 팀 소속 검사가 없다.
 * SecurityConfig의 anyRequest().authenticated()로 보호된다.
 */
@RestController
@RequiredArgsConstructor
public class BusinessRegistrationUploadController implements BusinessRegistrationUploadControllerDocs {

    private final BusinessRegistrationUploadService uploadService;

    /**
     * {@code required = false}가 중요하다. 기본값(required=true)이면 파일을 첨부하지 않은 요청에서
     * Spring이 컨트롤러 진입 전에 MissingServletRequestPartException을 던져버려,
     * 서비스의 "파일 없음" 검사(BUSINESS_400_004)가 <b>실행조차 되지 않는다</b>.
     * null을 그대로 받아 서비스가 판단하게 한다.
     */
    @Override
    @PostMapping(
            value = "/api/v1/teams/business-registration",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BusinessRegistrationUploadResponse> uploadBusinessRegistration(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, uploadService.upload(userId, file));
    }
}
