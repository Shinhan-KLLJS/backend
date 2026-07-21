package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamInviteCodeResponse;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamInviteLink;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamInviteLinkRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TeamInviteCodeIntegrationTest {

    @Autowired
    private TeamInviteCodeService service;

    @Autowired
    private TeamInviteLinkRepository inviteRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    @Test
    void issue_createsInviteWithPlaintextCode() {
        Fixture fixture = persistFixture(TeamStatus.ACTIVE, TeamMemberRole.OWNER);

        TeamInviteCodeResponse response = service.issue(fixture.member().getUser().getId(), fixture.team().getId());

        assertThat(response.inviteCode()).matches("[A-Z0-9]{7}");
        assertThat(response.inviteCodeExpiresAt().getOffset().getTotalSeconds()).isEqualTo(9 * 60 * 60);

        TeamInviteLink saved = inviteRepository.findByTeamIdAndRevokedAtIsNull(fixture.team().getId()).orElseThrow();
        assertThat(saved.getInviteCode()).isEqualTo(response.inviteCode());
    }

    @Test
    void issue_reusesActiveCodeWithinValidityWindow() {
        Fixture fixture = persistFixture(TeamStatus.ACTIVE, TeamMemberRole.ADMIN);

        TeamInviteCodeResponse first = service.issue(fixture.member().getUser().getId(), fixture.team().getId());
        TeamInviteCodeResponse second = service.issue(fixture.member().getUser().getId(), fixture.team().getId());
        entityManager.flush();

        List<TeamInviteLink> teamInvites = inviteRepository.findAll().stream()
                .filter(invite -> invite.getTeam().getId().equals(fixture.team().getId()))
                .toList();
        assertThat(teamInvites).hasSize(1);
        assertThat(teamInvites.getFirst().getRevokedAt()).isNull();
        assertThat(first.inviteCode()).isEqualTo(second.inviteCode());
        assertThat(first.inviteCodeExpiresAt()).isEqualTo(second.inviteCodeExpiresAt());
    }

    @Test
    void issue_reissuesWhenPreviousCodeExpired() {
        Fixture fixture = persistFixture(TeamStatus.ACTIVE, TeamMemberRole.OWNER);
        TeamInviteLink expired = TeamInviteLink.builder()
                .team(fixture.team())
                .createdBy(fixture.member().getUser())
                .inviteCode("OLDCOD1")
                .maxUses(null)
                .expiresAt(LocalDateTime.now(clock).minusHours(1))
                .build();
        entityManager.persist(expired);
        entityManager.flush();

        TeamInviteCodeResponse response = service.issue(fixture.member().getUser().getId(), fixture.team().getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(response.inviteCode()).isNotEqualTo("OLDCOD1");
        assertThat(entityManager.find(TeamInviteLink.class, expired.getId()).getRevokedAt()).isNotNull();
        TeamInviteLink newActive = inviteRepository.findByTeamIdAndRevokedAtIsNull(fixture.team().getId()).orElseThrow();
        assertThat(newActive.getInviteCode()).isEqualTo(response.inviteCode());
    }

    @Test
    void issue_reissuesWhenPreviousCodeIsLegacyNullInviteCode() {
        // V11 이전 해시 저장 방식에서 넘어온 행 재현 - revoke도 만료도 안 됐지만 평문 코드를
        // 복원할 수 없어 inviteCode가 null이다. isUsable()만 보면 재사용 가능하다고 오판해
        // inviteCode: null을 그대로 응답해버리는 회귀를 잡는 테스트다.
        Fixture fixture = persistFixture(TeamStatus.ACTIVE, TeamMemberRole.OWNER);
        TeamInviteLink legacyNullCode = TeamInviteLink.builder()
                .team(fixture.team())
                .createdBy(fixture.member().getUser())
                .inviteCode(null)
                .maxUses(null)
                .expiresAt(LocalDateTime.now(clock).plusDays(300))
                .build();
        entityManager.persist(legacyNullCode);
        entityManager.flush();

        TeamInviteCodeResponse response = service.issue(fixture.member().getUser().getId(), fixture.team().getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(response.inviteCode()).isNotNull();
        assertThat(entityManager.find(TeamInviteLink.class, legacyNullCode.getId()).getRevokedAt()).isNotNull();
        TeamInviteLink newActive = inviteRepository.findByTeamIdAndRevokedAtIsNull(fixture.team().getId()).orElseThrow();
        assertThat(newActive.getInviteCode()).isEqualTo(response.inviteCode());
    }

    @Test
    void issue_allowsMemberRole() {
        Fixture fixture = persistFixture(TeamStatus.ACTIVE, TeamMemberRole.MEMBER);

        TeamInviteCodeResponse response = service.issue(fixture.member().getUser().getId(), fixture.team().getId());

        assertThat(response.inviteCode()).matches("[A-Z0-9]{7}");
    }

    @Test
    void issue_rejectsInactiveTeam() {
        Fixture fixture = persistFixture(TeamStatus.SUSPENDED, TeamMemberRole.OWNER);

        assertTeamError(
                () -> service.issue(fixture.member().getUser().getId(), fixture.team().getId()),
                TeamErrorCode.TEAM_NOT_ACTIVE
        );
    }

    private Fixture persistFixture(TeamStatus teamStatus, TeamMemberRole role) {
        Team team = Team.builder().teamName("초대 테스트 " + System.nanoTime()).status(teamStatus).build();
        entityManager.persist(team);

        User user = User.builder()
                .displayName("초대자")
                .email("inviter@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);

        TeamMember member = TeamMember.builder()
                .team(team)
                .user(user)
                .role(role)
                .status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.of(2026, 7, 10, 0, 0))
                .build();
        entityManager.persist(member);
        entityManager.flush();
        return new Fixture(team, member);
    }

    private void assertTeamError(Runnable invocation, TeamErrorCode errorCode) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(GeneralException.class)
                .satisfies(exception -> assertThat(((GeneralException) exception).getErrorCode())
                        .isEqualTo(errorCode));
    }

    private record Fixture(Team team, TeamMember member) {
    }
}