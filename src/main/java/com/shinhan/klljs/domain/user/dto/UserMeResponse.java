package com.shinhan.klljs.domain.user.dto;

import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;

public record UserMeResponse(
        Long id,
        String displayName,
        String email,
        String profileImageUrl,
        UserStatus status
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getStatus()
        );
    }
}
