package com.shinhan.klljs.domain.auth.dto;

import com.shinhan.klljs.domain.user.entity.UserStatus;

public record AuthenticatedUser(
        Long userId,
        UserStatus status
) {
}
