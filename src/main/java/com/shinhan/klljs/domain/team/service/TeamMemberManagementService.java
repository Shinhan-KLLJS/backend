package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamMemberListResponse;
import com.shinhan.klljs.domain.team.dto.TeamMemberRoleChangeResponse;
import com.shinhan.klljs.domain.team.dto.TeamMemberSummary;
import com.shinhan.klljs.domain.team.dto.UpdatedTeamMember;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.global.apiPayload.code.GeneralErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TeamMemberManagementService {

    private static final int MAX_KEYWORD_LENGTH = 100;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Transactional(readOnly = true)
    public TeamMemberListResponse getMembers(Long requesterId, Long teamId, String keyword) {
        Team team = getTeam(teamId);
        requireActiveMember(requesterId, teamId);
        requireActiveTeam(team);

        String normalizedKeyword = normalizeKeyword(keyword);
        List<TeamMember> members = teamMemberRepository.findAllWithUserByTeamIdAndStatus(
                teamId, TeamMemberStatus.ACTIVE);

        Comparator<TeamMember> groupedOrder = Comparator
                .comparingInt((TeamMember member) -> roleOrder(member.getRole()))
                .thenComparing(member -> member.getUser().getDisplayName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(member -> member.getUser().getId());

        Comparator<TeamMember> finalOrder = normalizedKeyword == null
                ? Comparator.comparingInt((TeamMember member) ->
                        member.getUser().getId().equals(requesterId) ? 0 : 1).thenComparing(groupedOrder)
                : groupedOrder;

        List<TeamMemberSummary> summaries = members.stream()
                .filter(member -> matchesKeyword(member, normalizedKeyword))
                .sorted(finalOrder)
                .map(member -> TeamMemberSummary.from(member, requesterId))
                .toList();

        return new TeamMemberListResponse(team.getId(), team.getTeamName(), summaries);
    }

    @Transactional
    public TeamMemberRoleChangeResponse changeRole(
            Long requesterId,
            Long teamId,
            Long targetUserId,
            TeamMemberRole requestedRole
    ) {
        Team team = lockTeam(teamId);
        TeamMember requester = requireActiveMember(requesterId, teamId);
        requireActiveTeam(team);

        if (requesterId.equals(targetUserId)) {
            throw new GeneralException(TeamErrorCode.INVALID_SELF_MEMBER_OPERATION);
        }

        TeamMember target = requireTargetMember(targetUserId, teamId);
        validateRoleChangePermission(requester, target, requestedRole);

        if (target.getRole() == requestedRole) {
            return TeamMemberRoleChangeResponse.of(UpdatedTeamMember.from(target));
        }

        if (requestedRole != TeamMemberRole.OWNER) {
            target.changeRole(requestedRole);
            return TeamMemberRoleChangeResponse.of(UpdatedTeamMember.from(target));
        }

        requester.changeRole(TeamMemberRole.ADMIN);
        teamMemberRepository.flush();
        target.changeRole(TeamMemberRole.OWNER);

        return TeamMemberRoleChangeResponse.of(
                UpdatedTeamMember.from(target),
                UpdatedTeamMember.from(requester)
        );
    }

    @Transactional
    public void removeMember(Long requesterId, Long teamId, Long targetUserId) {
        Team team = lockTeam(teamId);
        TeamMember requester = requireActiveMember(requesterId, teamId);
        requireActiveTeam(team);

        if (requester.getRole() != TeamMemberRole.OWNER) {
            throw new GeneralException(TeamErrorCode.TEAM_MANAGEMENT_FORBIDDEN);
        }
        if (requesterId.equals(targetUserId)) {
            throw new GeneralException(TeamErrorCode.INVALID_SELF_MEMBER_OPERATION);
        }

        TeamMember target = requireTargetMember(targetUserId, teamId);
        target.changeStatus(TeamMemberStatus.REMOVED);
    }

    @Transactional
    public void leaveTeam(Long requesterId, Long teamId) {
        Team team = lockTeam(teamId);
        TeamMember requester = requireActiveMember(requesterId, teamId);
        requireActiveTeam(team);

        if (requester.getRole() == TeamMemberRole.OWNER) {
            throw new GeneralException(TeamErrorCode.OWNER_TRANSFER_REQUIRED);
        }
        requester.changeStatus(TeamMemberStatus.LEFT);
    }

    private void validateRoleChangePermission(
            TeamMember requester,
            TeamMember target,
            TeamMemberRole requestedRole
    ) {
        if (requester.getRole() == TeamMemberRole.MEMBER) {
            throw new GeneralException(TeamErrorCode.TEAM_MANAGEMENT_FORBIDDEN);
        }
        if (requester.getRole() == TeamMemberRole.ADMIN
                && (requestedRole == TeamMemberRole.OWNER || target.getRole() == TeamMemberRole.OWNER)) {
            throw new GeneralException(TeamErrorCode.TEAM_MANAGEMENT_FORBIDDEN);
        }
    }

    private Team getTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_NOT_FOUND));
    }

    private Team lockTeam(Long teamId) {
        return teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_NOT_FOUND));
    }

    private TeamMember requireActiveMember(Long userId, Long teamId) {
        return teamMemberRepository.findByUserIdAndTeamIdAndStatus(
                        userId, teamId, TeamMemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_ACCESS_DENIED));
    }

    private TeamMember requireTargetMember(Long userId, Long teamId) {
        return teamMemberRepository.findByUserIdAndTeamIdAndStatus(
                        userId, teamId, TeamMemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_MEMBER_NOT_FOUND));
    }

    private void requireActiveTeam(Team team) {
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new GeneralException(TeamErrorCode.TEAM_NOT_ACTIVE);
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new GeneralException(GeneralErrorCode.VALIDATION_ERROR, "keyword는 100자 이하여야 합니다.");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private boolean matchesKeyword(TeamMember member, String normalizedKeyword) {
        if (normalizedKeyword == null) {
            return true;
        }
        String displayName = member.getUser().getDisplayName().toLowerCase(Locale.ROOT);
        String email = member.getUser().getEmail();
        return displayName.contains(normalizedKeyword)
                || (email != null && email.toLowerCase(Locale.ROOT).contains(normalizedKeyword));
    }

    private int roleOrder(TeamMemberRole role) {
        return switch (role) {
            case OWNER -> 0;
            case ADMIN -> 1;
            case MEMBER -> 2;
        };
    }
}