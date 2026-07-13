package com.shinhan.klljs.domain.team.exception;

import com.shinhan.klljs.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TeamErrorCode implements BaseErrorCode {
    INVALID_INVITE(HttpStatus.BAD_REQUEST, "TEAM_400_001", "유효하지 않은 초대입니다."),
    INVALID_SELF_MEMBER_OPERATION(HttpStatus.BAD_REQUEST, "TEAM_400_002", "자기 자신에게 수행할 수 없는 작업입니다."),

    TEAM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "TEAM_403_001", "팀에 접근할 권한이 없습니다."),
    TEAM_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "TEAM_403_002", "팀원을 관리할 권한이 없습니다."),
    CAMPAIGN_MANAGEMENT_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "TEAM_403_003",
            "캠페인을 등록하거나 삭제할 권한이 없습니다."
    ),

    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "TEAM_404_001", "팀을 찾을 수 없습니다."),
    TEAM_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "TEAM_404_002", "활성 팀원을 찾을 수 없습니다."),

    TEAM_NOT_ACTIVE(HttpStatus.CONFLICT, "TEAM_409_001", "활성 상태의 팀에서만 수행할 수 있습니다."),
    OWNER_TRANSFER_REQUIRED(HttpStatus.CONFLICT, "TEAM_409_002", "팀을 나가기 전에 OWNER 역할을 이전해야 합니다."),

    INVITE_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TEAM_500_001", "초대 코드 생성에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
