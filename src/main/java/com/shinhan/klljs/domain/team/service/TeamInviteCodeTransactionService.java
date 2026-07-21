package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamInviteCodeResponse;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamInviteLink;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.exception.InviteCodeCollisionException;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamInviteLinkRepository;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TeamInviteCodeTransactionService {

    /**
     * 방치된 팀이 초대 자체를 못 하게 되는 걸 막으면서도, 유출된 코드의 노출 기간을 짧게
     * 가져가기 위한 값이다. 예전엔 1년이었지만, 버튼을 눌러도 코드가 안 바뀌는 재사용 방식으로
     * 바뀌면서 "길게 잡아야 할 이유"가 없어져 짧게 줄였다.
     */
    private static final long INVITE_CODE_TTL_DAYS = 1;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInviteLinkRepository teamInviteLinkRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final Clock clock;

    @Transactional
    public TeamInviteCodeResponse issueOnce(Long requesterId, Long teamId) {
        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_NOT_FOUND));
        TeamMember requester = teamMemberRepository.findByUserIdAndTeamIdAndStatus(
                        requesterId, teamId, TeamMemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_ACCESS_DENIED));

        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new GeneralException(TeamErrorCode.TEAM_NOT_ACTIVE);
        }

        LocalDateTime nowUtc = LocalDateTime.now(clock);
        Optional<TeamInviteLink> activeInvite = teamInviteLinkRepository.findByTeamIdAndRevokedAtIsNull(teamId);

        // 아직 안 만료된 코드가 있으면 그대로 재사용한다 - 버튼을 눌러도 새 코드가 안 나온다.
        if (activeInvite.isPresent() && activeInvite.get().isUsable(nowUtc)) {
            TeamInviteLink current = activeInvite.get();
            return new TeamInviteCodeResponse(current.getInviteCode(), KstDateTimes.toKstOffset(current.getExpiresAt()));
        }

        // 없거나 이미 만료된 코드만 있으면, 만료된 코드부터 폐기하고 새로 발급한다
        // (activeCodeMarker 유니크 인덱스가 팀당 미폐기 행 1개만 허용하므로 순서를 지켜야 한다).
        activeInvite.ifPresent(expired -> {
            expired.revoke(nowUtc);
            teamInviteLinkRepository.flush();
        });

        String rawCode = inviteCodeGenerator.generate();
        LocalDateTime expiresAtUtc = nowUtc.plusDays(INVITE_CODE_TTL_DAYS);
        TeamInviteLink invite = TeamInviteLink.builder()
                .team(team)
                .createdBy(requester.getUser())
                .inviteCode(rawCode)
                .maxUses(null)
                .expiresAt(expiresAtUtc)
                .build();

        try {
            teamInviteLinkRepository.saveAndFlush(invite);
        } catch (DataIntegrityViolationException e) {
            if (isInviteCodeCollision(e)) {
                throw new InviteCodeCollisionException(e);
            }
            throw e;
        }

        return new TeamInviteCodeResponse(rawCode, KstDateTimes.toKstOffset(expiresAtUtc));
    }

    private boolean isInviteCodeCollision(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null ? exception.getMessage() : cause.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("uk_invite_code")
                || (normalized.contains("invite_code")
                && (normalized.contains("unique") || normalized.contains("duplicate")));
    }
}