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
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.exception.UserErrorCode;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TeamJoinService {

    private static final String INVITE_CODE_PATTERN = "[A-Z0-9]{7}";

    private final TeamInviteLinkRepository teamInviteLinkRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public TeamJoinResponse join(Long userId, String inviteCode) {
        String normalizedCode = normalize(inviteCode);
        TeamInviteLink inviteSnapshot = findInviteByCode(normalizedCode);

        Team team = teamRepository.findByIdForUpdate(inviteSnapshot.getTeam().getId())
                .orElseThrow(this::invalidInvite);
        TeamInviteLink invite = teamInviteLinkRepository.findByIdForUpdate(inviteSnapshot.getId())
                .orElseThrow(this::invalidInvite);

        LocalDateTime now = LocalDateTime.now(clock);
        if (!invite.isUsable(now)) {
            throw invalidInvite();
        }
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new GeneralException(TeamErrorCode.TEAM_NOT_ACTIVE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
        TeamMember member = teamMemberRepository.findByUserIdAndTeamId(userId, team.getId())
                .map(existing -> reactivate(existing, invite, now))
                .orElseGet(() -> createMember(team, user, invite, now));

        invite.consumeOneUse(now);
        return new TeamJoinResponse(team.getId(), team.getTeamName(), member.getRole());
    }

    private TeamInviteLink findInviteByCode(String normalizedCode) {
        return teamInviteLinkRepository.findByInviteCode(normalizedCode)
                .orElseThrow(this::invalidInvite);
    }

    private TeamMember reactivate(TeamMember member, TeamInviteLink invite, LocalDateTime joinedAt) {
        if (member.getStatus() == TeamMemberStatus.ACTIVE) {
            throw new GeneralException(TeamErrorCode.ALREADY_TEAM_MEMBER);
        }
        member.rejoin(TeamMemberRole.MEMBER, invite, joinedAt);
        return member;
    }

    private TeamMember createMember(Team team, User user, TeamInviteLink invite, LocalDateTime joinedAt) {
        return teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .user(user)
                .joinedViaInvite(invite)
                .role(TeamMemberRole.MEMBER)
                .status(TeamMemberStatus.ACTIVE)
                .joinedAt(joinedAt)
                .build());
    }

    private String normalize(String inviteCode) {
        String normalized = inviteCode == null ? "" : inviteCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches(INVITE_CODE_PATTERN)) {
            throw invalidInvite();
        }
        return normalized;
    }

    private GeneralException invalidInvite() {
        return new GeneralException(TeamErrorCode.INVALID_INVITE);
    }
}