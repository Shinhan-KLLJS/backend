package com.shinhan.klljs.domain.team.entity;

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
 * 초대 코드로 합류하면 항상 MEMBER로 합류한다 (역할 선택 없음, 승격은 팀원 관리 화면에서 별도 처리).
 * 팀당 폐기되지 않은(revoked_at IS NULL) 코드는 항상 하나만 존재한다 — 새 코드를 발급하면
 * 기존 코드를 같은 트랜잭션에서 먼저 폐기해야 한다 (activeCodeMarker 유니크 인덱스가 강제).
 */
@Entity
@Table(name = "team_invite_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class TeamInviteLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(name = "invite_code", nullable = false, unique = true, length = 7)
    private String inviteCode;

    /** null이면 사용 횟수 무제한 */
    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * DB generated column (revoked_at이 NULL이면 team_id, 아니면 NULL).
     * 팀당 활성 코드 1개를 유니크 인덱스로 강제하기 위한 컬럼 — 애플리케이션에서는 값을 쓰지 않는다.
     */
    @Column(name = "active_code_marker", insertable = false, updatable = false)
    private Long activeCodeMarker;

    @Builder
    public TeamInviteLink(Team team, User createdBy, String inviteCode, Integer maxUses, LocalDateTime expiresAt) {
        this.team = team;
        this.createdBy = createdBy;
        this.inviteCode = inviteCode;
        this.maxUses = maxUses;
        this.usedCount = 0;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(LocalDateTime now) {
        boolean withinUseLimit = maxUses == null || usedCount < maxUses;
        return revokedAt == null && now.isBefore(expiresAt) && withinUseLimit;
    }

    /** 호출 전 반드시 SELECT ... FOR UPDATE로 이 행을 잠근 상태여야 한다. */
    public void consumeOneUse(LocalDateTime now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("TeamInviteLink is not usable: id=" + id);
        }
        this.usedCount += 1;
    }

    /** 새 코드를 발급하기 전, 기존 활성 코드에 반드시 먼저 호출해야 한다 (같은 트랜잭션). */
    public void revoke(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }
}
