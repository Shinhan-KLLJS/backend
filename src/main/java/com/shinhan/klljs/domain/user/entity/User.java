package com.shinhan.klljs.domain.user.entity;

import com.shinhan.klljs.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Builder
    public User(String displayName, String email, String profileImageUrl, UserStatus status) {
        this.displayName = displayName;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.status = status;
    }

    public void recordLogin(LocalDateTime loginAt) {
        this.lastLoginAt = loginAt;
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }
}
