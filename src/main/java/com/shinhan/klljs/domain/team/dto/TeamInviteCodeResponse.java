package com.shinhan.klljs.domain.team.dto;

import java.time.OffsetDateTime;

public record TeamInviteCodeResponse(
        String inviteCode,
        OffsetDateTime inviteCodeExpiresAt
) {
}