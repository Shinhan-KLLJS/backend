package com.shinhan.klljs.domain.auth.entity;

import com.shinhan.klljs.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 원본 토큰은 저장하지 않는다 — HttpOnly 쿠키로만 전달되고 DB에는 SHA-256 해시만 남는다.
 * 같은 tokenFamilyId를 공유하는 토큰들은 한 로그인 세션의 회전 계보다.
 * 이미 폐기된(revokedAt != null) 토큰이 다시 제시되면 탈취 의심 신호이므로 family 전체를 폐기해야 한다.
 */
@Entity
@Table(name = "auth_refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AuthRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 원본이 아니라 SHA-256 해시만 저장
    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "BINARY(32)")
    private byte[] tokenHash;

    //같은 "로그인 세션 계보"를 묶는 값
    @Column(name = "token_family_id", nullable = false, columnDefinition = "BINARY(16)")
    private byte[] tokenFamilyId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    //이 토큰이 회전되어 대체된 새 토큰의 id
    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AuthRefreshToken(User user, byte[] tokenHash, byte[] tokenFamilyId, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.tokenFamilyId = tokenFamilyId;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(LocalDateTime now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void revoke(LocalDateTime now) {
        this.revokedAt = now;
    }

    /** 회전 시 옛 토큰에 호출한다 — 폐기와 동시에 어떤 토큰으로 대체됐는지 남긴다. */
    public void markRotated(Long newTokenId, LocalDateTime now) {
        this.replacedByTokenId = newTokenId;
        this.revokedAt = now;
    }
}
