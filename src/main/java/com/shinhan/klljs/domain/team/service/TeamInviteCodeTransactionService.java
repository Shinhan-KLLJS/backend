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

@Service
@RequiredArgsConstructor
public class TeamInviteCodeTransactionService {

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
        teamInviteLinkRepository.findByTeamIdAndRevokedAtIsNull(teamId).ifPresent(activeInvite -> {
            activeInvite.revoke(nowUtc);
            teamInviteLinkRepository.flush();
        });

        String rawCode = inviteCodeGenerator.generate();
        LocalDateTime expiresAtUtc = nowUtc.plusYears(1);
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