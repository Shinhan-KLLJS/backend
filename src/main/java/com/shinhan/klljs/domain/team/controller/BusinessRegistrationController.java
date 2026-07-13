package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.dto.BusinessRegistrationSubmitRequest;
import com.shinhan.klljs.domain.team.dto.BusinessRegistrationSubmitResponse;
import com.shinhan.klljs.domain.team.service.BusinessRegistrationSubmitService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사업자등록증 검증 제출 API (server-verification-spec.md).
 *
 * SecurityConfig의 anyRequest().authenticated() 규칙으로 보호되므로 JWT 없이 호출하면
 * 이 컨트롤러에 들어오기 전에 401로 막힌다. jwt.getSubject()가 내부 사용자 ID(문자열)다.
 * Swagger 설명은 BusinessRegistrationControllerDocs에 몰아뒀다.
 */
@RestController
@RequiredArgsConstructor
public class BusinessRegistrationController implements BusinessRegistrationControllerDocs {

    private final BusinessRegistrationSubmitService businessRegistrationSubmitService;

    /**
     * 반려(rejected)나 검토 필요(review_required)도 요청 처리 자체는 성공이므로 200으로 응답한다.
     * 판정 결과는 result.decisionStatus/reasonCode로 구분한다.
     */
    @Override
    @PostMapping("/api/v1/teams/{teamId}/business-registration")
    public ApiResponse<BusinessRegistrationSubmitResponse> submitBusinessRegistration(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long teamId,
            @Valid @RequestBody BusinessRegistrationSubmitRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        BusinessRegistrationSubmitResponse response =
                businessRegistrationSubmitService.submit(userId, teamId, request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
