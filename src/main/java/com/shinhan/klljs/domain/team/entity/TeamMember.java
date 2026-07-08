package com.shinhan.klljs.domain.team.entity;

import com.shinhan.klljs.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "team_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_team_member_team_user", columnNames = {"team_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "joined_via_invite_id")
    private TeamInviteLink joinedViaInvite;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private TeamMemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TeamMemberStatus status;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    /**
     * DB generated column (team_id when role=OWNER AND status=ACTIVE, else NULL).
     * 팀당 활성 OWNER 1명을 유니크 인덱스로 강제하기 위한 컬럼 — 애플리케이션에서는 절대 값을 쓰지 않는다.
     */
    @Column(name = "active_owner_marker", insertable = false, updatable = false)
    private Long activeOwnerMarker;

    @Builder
    public TeamMember(Team team, User user, TeamInviteLink joinedViaInvite,
                       TeamMemberRole role, TeamMemberStatus status, LocalDateTime joinedAt) {
        this.team = team;
        this.user = user;
        this.joinedViaInvite = joinedViaInvite;
        this.role = role;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    public void rejoin(TeamMemberRole role, TeamInviteLink joinedViaInvite, LocalDateTime joinedAt) {
        this.role = role;
        this.joinedViaInvite = joinedViaInvite;
        this.joinedAt = joinedAt;
        this.status = TeamMemberStatus.ACTIVE;
    }

    public void changeRole(TeamMemberRole role) {
        this.role = role;
    }

    public void changeStatus(TeamMemberStatus status) {
        this.status = status;
    }
}
