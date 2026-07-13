package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamJoinResponse;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamInviteLink;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamInviteLinkRepository;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import com.shinhan.klljs.global.util.TokenHasher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TeamJoinServiceTest {

    @Autowired
    private TeamJoinService service;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private TeamInviteLinkRepository inviteRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    @Test
    void join_createsMemberAndConsumesInvite() {
        Fixture fixture = persistFixture("AB12CD3", TeamStatus.ACTIVE);

        TeamJoinResponse response = service.join(fixture.joiner().getId(), "  ab12cd3  ");

        assertThat(response.teamId()).isEqualTo(fixture.team().getId());
        assertThat(response.teamName()).isEqualTo(fixture.team().getTeamName());
        assertThat(response.role()).isEqualTo(TeamMemberRole.MEMBER);

        TeamMember member = teamMemberRepository
                .findByUserIdAndTeamId(fixture.joiner().getId(), fixture.team().getId())
                .orElseThrow();
        assertThat(member.getStatus()).isEqualTo(TeamMemberStatus.ACTIVE);
        assertThat(member.getRole()).isEqualTo(TeamMemberRole.MEMBER);
        assertThat(member.getJoinedViaInvite().getId()).isEqualTo(fixture.invite().getId());
        assertThat(fixture.invite().getUsedCount()).isEqualTo(1);
    }

    @Test
    void join_reactivatesRemovedMemberAsMember() {
        Fixture fixture = persistFixture("REJOIN1", TeamStatus.ACTIVE);
        TeamMember previous = persistMember(
                fixture.team(), fixture.joiner(), TeamMemberRole.ADMIN, TeamMemberStatus.REMOVED);

        service.join(fixture.joiner().getId(), "REJOIN1");

        assertThat(previous.getStatus()).isEqualTo(TeamMemberStatus.ACTIVE);
        assertThat(previous.getRole()).isEqualTo(TeamMemberRole.MEMBER);
        assertThat(previous.getJoinedViaInvite().getId()).isEqualTo(fixture.invite().getId());
        assertThat(teamMemberRepository.findAll().stream()
                .filter(member -> member.getTeam().getId().equals(fixture.team().getId()))
                .filter(member -> member.getUser().getId().equals(fixture.joiner().getId())))
                .hasSize(1);
    }

    @Test
    void join_rejectsAlreadyActiveMemberWithoutConsumingInvite() {
        Fixture fixture = persistFixture("ACTIVE1", TeamStatus.ACTIVE);
        persistMember(fixture.team(), fixture.joiner(), TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE);

        assertTeamError(
                () -> service.join(fixture.joiner().getId(), "ACTIVE1"),
                TeamErrorCode.ALREADY_TEAM_MEMBER
        );
        assertThat(fixture.invite().getUsedCount()).isZero();
    }

    @Test
    void join_rejectsUnknownExpiredAndRevokedCodes() {
        Fixture expired = persistFixture("EXPIRE1", TeamStatus.ACTIVE,
                LocalDateTime.now(clock).minusSeconds(1), false);
        Fixture revoked = persistFixture("REVOKE1", TeamStatus.ACTIVE,
                LocalDateTime.now(clock).plusDays(1), true);

        assertTeamError(() -> service.join(expired.joiner().getId(), "UNKNOWN"), TeamErrorCode.INVALID_INVITE);
        assertTeamError(() -> service.join(expired.joiner().getId(), "EXPIRE1"), TeamErrorCode.INVALID_INVITE);
        assertTeamError(() -> service.join(revoked.joiner().getId(), "REVOKE1"), TeamErrorCode.INVALID_INVITE);
    }

    @Test
    void join_rejectsInactiveTeam() {
        Fixture fixture = persistFixture("CLOSED1", TeamStatus.CLOSED);

        assertTeamError(
                () -> service.join(fixture.joiner().getId(), "CLOSED1"),
                TeamErrorCode.TEAM_NOT_ACTIVE
        );
    }

    private Fixture persistFixture(String code, TeamStatus status) {
        return persistFixture(code, status, LocalDateTime.now(clock).plusDays(1), false);
    }

    private Fixture persistFixture(
            String code,
            TeamStatus status,
            LocalDateTime expiresAt,
            boolean revoked
    ) {
        Team team = Team.builder()
                .teamName("합류 테스트 " + code)
                .status(status)
                .build();
        entityManager.persist(team);

        User inviter = persistUser("초대자 " + code);
        User joiner = persistUser("합류자 " + code);

        TeamInviteLink invite = TeamInviteLink.builder()
                .team(team)
                .createdBy(inviter)
                .tokenHash(TokenHasher.sha256(code))
                .maxUses(null)
                .expiresAt(expiresAt)
                .build();
        if (revoked) {
            invite.revoke(LocalDateTime.now(clock));
        }
        inviteRepository.save(invite);
        entityManager.flush();
        return new Fixture(team, joiner, invite);
    }

    private User persistUser(String name) {
        User user = User.builder()
                .displayName(name)
                .email(name + "@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
        return user;
    }

    private TeamMember persistMember(
            Team team,
            User user,
            TeamMemberRole role,
            TeamMemberStatus status
    ) {
        TeamMember member = TeamMember.builder()
                .team(team)
                .user(user)
                .role(role)
                .status(status)
                .joinedAt(LocalDateTime.now(clock).minusDays(1))
                .build();
        entityManager.persist(member);
        entityManager.flush();
        return member;
    }

    private void assertTeamError(Runnable invocation, TeamErrorCode errorCode) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(GeneralException.class)
                .satisfies(exception -> assertThat(((GeneralException) exception).getErrorCode())
                        .isEqualTo(errorCode));
    }

    private record Fixture(Team team, User joiner, TeamInviteLink invite) {
    }
}