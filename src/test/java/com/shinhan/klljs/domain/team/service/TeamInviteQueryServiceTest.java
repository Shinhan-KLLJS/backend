package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamInviteLink;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamInviteLinkRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import com.shinhan.klljs.global.util.TokenHasher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional // 각 테스트 종료 후 자동 롤백
class TeamInviteQueryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private TeamInviteLinkRepository teamInviteLinkRepository;

    @Autowired
    private EntityManager entityManager;

    private TeamInviteQueryService service;

    @BeforeEach
    void setUp() {
        service = new TeamInviteQueryService(teamInviteLinkRepository, FIXED_CLOCK);
    }

    @Test
    void validateAndGetId_returnsIdForUsableInvite() {
        TeamInviteLink invite = persistInvite("raw-token-1", LocalDateTime.now(FIXED_CLOCK).plusHours(24));

        Long result = service.validateAndGetId("raw-token-1");

        assertThat(result).isEqualTo(invite.getId());
    }

    @Test
    void validateAndGetId_throwsInvalidInviteWhenTokenNotFound() {
        assertThatThrownBy(() -> service.validateAndGetId("no-such-token"))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(TeamErrorCode.INVALID_INVITE));
    }

    @Test
    void validateAndGetId_throwsInvalidInviteWhenExpired() {
        persistInvite("expired-token", LocalDateTime.now(FIXED_CLOCK).minusMinutes(1));

        assertThatThrownBy(() -> service.validateAndGetId("expired-token"))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(TeamErrorCode.INVALID_INVITE));
    }

    @Test
    void validateAndGetId_throwsInvalidInviteWhenRevoked() {
        TeamInviteLink invite = persistInvite("revoked-token", LocalDateTime.now(FIXED_CLOCK).plusHours(24));
        invite.revoke(LocalDateTime.now(FIXED_CLOCK));
        entityManager.flush();

        assertThatThrownBy(() -> service.validateAndGetId("revoked-token"))
                .isInstanceOf(GeneralException.class);
    }

    private TeamInviteLink persistInvite(String rawToken, LocalDateTime expiresAt) {
        Team team = Team.builder().teamName("팀A").status(TeamStatus.ACTIVE).build();
        entityManager.persist(team);

        User user = User.builder().displayName("철수").status(UserStatus.ACTIVE).build();
        entityManager.persist(user);

        TeamInviteLink invite = TeamInviteLink.builder()
                .team(team)
                .createdBy(user)
                .tokenHash(TokenHasher.sha256(rawToken))
                .maxUses(null)
                .expiresAt(expiresAt)
                .build();

        return teamInviteLinkRepository.save(invite);
    }
}
