package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamMemberListResponse;
import com.shinhan.klljs.domain.team.dto.TeamMemberRoleChangeResponse;
import com.shinhan.klljs.domain.team.dto.TeamRenameResponse;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TeamMemberManagementServiceTest {

    @Autowired
    private TeamMemberManagementService service;

    @Autowired
    private EntityManager entityManager;

    @Test
    void getMembers_returnsTeamInfoAndKeepsRequesterFirst() {
        Team team = persistTeam(TeamStatus.ACTIVE);
        TeamMember owner = persistMember(team, "가Owner", "owner@example.com", TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE);
        TeamMember requester = persistMember(team, "나Admin", "admin@example.com", TeamMemberRole.ADMIN, TeamMemberStatus.ACTIVE);
        persistMember(team, "다Member", null, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE);
        persistMember(team, "라Left", "left@example.com", TeamMemberRole.MEMBER, TeamMemberStatus.LEFT);
        entityManager.flush();

        TeamMemberListResponse response = service.getMembers(requester.getUser().getId(), team.getId(), null);

        assertThat(response.teamId()).isEqualTo(team.getId());
        assertThat(response.teamName()).isEqualTo(team.getTeamName());
        assertThat(response.members()).hasSize(3);
        assertThat(response.members().getFirst().userId()).isEqualTo(requester.getUser().getId());
        assertThat(response.members().get(1).userId()).isEqualTo(owner.getUser().getId());
        assertThat(response.members().get(2).email()).isNull();
    }

    @Test
    void getMembers_filtersNameAndEmailCaseInsensitively() {
        Team team = persistTeam(TeamStatus.ACTIVE);
        TeamMember owner = persistMember(team, "홍길동", "Owner@Example.com", TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE);
        TeamMember member = persistMember(team, "김철수", null, TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE);
        entityManager.flush();

        TeamMemberListResponse byEmail = service.getMembers(member.getUser().getId(), team.getId(), " owner@ ");
        TeamMemberListResponse byName = service.getMembers(owner.getUser().getId(), team.getId(), "철수");

        assertThat(byEmail.members()).extracting("userId").containsExactly(owner.getUser().getId());
        assertThat(byName.members()).extracting("userId").containsExactly(member.getUser().getId());
    }

    @Test
    void changeRole_transfersOwnerWithoutViolatingUniqueConstraint() {
        Team team = persistTeam(TeamStatus.ACTIVE);
        TeamMember owner = persistMember(team, "기존오너", "owner@example.com", TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE);
        TeamMember target = persistMember(team, "새오너", "target@example.com", TeamMemberRole.ADMIN, TeamMemberStatus.ACTIVE);
        entityManager.flush();

        TeamMemberRoleChangeResponse response = service.changeRole(
                owner.getUser().getId(), team.getId(), target.getUser().getId(), TeamMemberRole.OWNER);
        entityManager.flush();

        assertThat(response.updatedMembers()).hasSize(2);
        assertThat(response.updatedMembers().getFirst().userId()).isEqualTo(target.getUser().getId());
        assertThat(target.getRole()).isEqualTo(TeamMemberRole.OWNER);
        assertThat(owner.getRole()).isEqualTo(TeamMemberRole.ADMIN);
    }

    @Test
    void changeRole_returnsCurrentRoleForIdempotentRequest() {
        Team team = persistTeam(TeamStatus.ACTIVE);
        TeamMember owner = persistMember(team, "오너", "owner@example.com", TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE);
        TeamMember target = persistMember(team, "관리자", "admin@example.com", TeamMemberRole.ADMIN, TeamMemberStatus.ACTIVE);
        entityManager.flush();

        TeamMemberRoleChangeResponse response = service.changeRole(
                owner.getUser().getId(), team.getId(), target.getUser().getId(), TeamMemberRole.ADMIN);

        assertThat(response.updatedMembers()).hasSize(1);
        assertThat(response.updatedMembers().getFirst().role()).isEqualTo(TeamMemberRole.ADMIN);
    }

    @Test
    void renameTeam_allowsOwnerAndAdminButRejectsMember() {
        Team team = persistTeam(TeamStatus.ACTIVE);
        TeamMember owner = persistMember(team, "오너", "owner@example.com", TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE);
        TeamMember admin = persistMember(team, "관리자", "admin@example.com", TeamMemberRole.ADMIN, TeamMemberStatus.ACTIVE);
        TeamMember member = persistMember(team, "멤버", "member@example.com", TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE);
        entityManager.flush();

        TeamRenameResponse response = service.renameTeam(owner.getUser().getId(), team.getId(), "새 팀명");
        entityManager.flush();

        assertThat(response.teamId()).isEqualTo(team.getId());
        assertThat(response.teamName()).isEqualTo("새 팀명");
        assertThat(team.getTeamName()).isEqualTo("새 팀명");

        service.renameTeam(admin.getUser().getId(), team.getId(), "관리자가 바꾼 팀명");
        assertThat(team.getTeamName()).isEqualTo("관리자가 바꾼 팀명");

        assertTeamError(
                () -> service.renameTeam(member.getUser().getId(), team.getId(), "멤버가 시도한 팀명"),
                TeamErrorCode.TEAM_SETTINGS_FORBIDDEN
        );
        assertThat(team.getTeamName()).isEqualTo("관리자가 바꾼 팀명");
    }

    @Test
    void renameTeam_trimsEdgeWhitespace() {
        Team team = persistTeam(TeamStatus.ACTIVE);
        TeamMember owner = persistMember(team, "오너", "owner@example.com", TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE);
        entityManager.flush();

        TeamRenameResponse response = service.renameTeam(owner.getUser().getId(), team.getId(), "  공백 팀명  ");
        entityManager.flush();
        entityManager.clear();

        assertThat(response.teamName()).isEqualTo("공백 팀명");
        // 영속성 컨텍스트를 비우고 다시 조회해서, 메모리상 엔티티 상태가 아니라 실제 DB 반영 여부를 확인한다.
        assertThat(entityManager.find(Team.class, team.getId()).getTeamName()).isEqualTo("공백 팀명");
    }

    @Test
    void removeAndLeave_changeMembershipStatuses() {
        Team team = persistTeam(TeamStatus.ACTIVE);
        TeamMember owner = persistMember(team, "오너", "owner@example.com", TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE);
        TeamMember removed = persistMember(team, "삭제대상", "removed@example.com", TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE);
        TeamMember leaver = persistMember(team, "탈퇴자", "leaver@example.com", TeamMemberRole.ADMIN, TeamMemberStatus.ACTIVE);
        entityManager.flush();

        service.removeMember(owner.getUser().getId(), team.getId(), removed.getUser().getId());
        service.leaveTeam(leaver.getUser().getId(), team.getId());

        assertThat(removed.getStatus()).isEqualTo(TeamMemberStatus.REMOVED);
        assertThat(leaver.getStatus()).isEqualTo(TeamMemberStatus.LEFT);
    }

    @Test
    void leaveTeam_rejectsOwnerWhenOtherActiveMembersExist() {
        Team team = persistTeam(TeamStatus.ACTIVE);
        TeamMember owner = persistMember(team, "오너", "owner@example.com", TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE);
        persistMember(team, "멤버", "member@example.com", TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE);
        entityManager.flush();

        assertTeamError(
                () -> service.leaveTeam(owner.getUser().getId(), team.getId()),
                TeamErrorCode.OWNER_TRANSFER_REQUIRED
        );
    }

    @Test
    void leaveTeam_allowsSoleOwnerToLeaveWithoutTransfer() {
        Team team = persistTeam(TeamStatus.ACTIVE);
        TeamMember owner = persistMember(team, "오너", "owner@example.com", TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE);
        // 비활성 이력 멤버를 같이 둬서 countByTeamIdAndStatus가 ACTIVE만 세는지도 검증한다 -
        // 멤버가 owner 하나뿐이면 상태 필터가 빠진 버그가 있어도 우연히 통과해버린다.
        TeamMember leftMember = persistMember(team, "이전 멤버", "left@example.com", TeamMemberRole.MEMBER, TeamMemberStatus.LEFT);
        entityManager.flush();

        service.leaveTeam(owner.getUser().getId(), team.getId());
        entityManager.flush();
        entityManager.clear();

        // 영속성 컨텍스트를 비우고 다시 조회해서, 메모리상 엔티티 상태가 아니라 실제 DB 반영 여부를 확인한다.
        assertThat(entityManager.find(TeamMember.class, owner.getId()).getStatus()).isEqualTo(TeamMemberStatus.LEFT);
        assertThat(entityManager.find(TeamMember.class, leftMember.getId()).getStatus()).isEqualTo(TeamMemberStatus.LEFT);
        // 팀 삭제 기능이 없으므로 "유령 팀"이 되어도 팀 상태 자체는 ACTIVE로 남는다(PR 설명 참고).
        assertThat(entityManager.find(Team.class, team.getId()).getStatus()).isEqualTo(TeamStatus.ACTIVE);
    }

    @Test
    void mutationsRejectInactiveTeam() {
        Team team = persistTeam(TeamStatus.SUSPENDED);
        TeamMember owner = persistMember(team, "오너", "owner@example.com", TeamMemberRole.OWNER, TeamMemberStatus.ACTIVE);
        TeamMember target = persistMember(team, "대상", "target@example.com", TeamMemberRole.MEMBER, TeamMemberStatus.ACTIVE);
        entityManager.flush();

        assertTeamError(
                () -> service.removeMember(owner.getUser().getId(), team.getId(), target.getUser().getId()),
                TeamErrorCode.TEAM_NOT_ACTIVE
        );
        assertTeamError(
                () -> service.renameTeam(owner.getUser().getId(), team.getId(), "정지된 팀명"),
                TeamErrorCode.TEAM_NOT_ACTIVE
        );
    }

    private Team persistTeam(TeamStatus status) {
        Team team = Team.builder().teamName("테스트 팀 " + System.nanoTime()).status(status).build();
        entityManager.persist(team);
        return team;
    }

    private TeamMember persistMember(
            Team team,
            String displayName,
            String email,
            TeamMemberRole role,
            TeamMemberStatus status
    ) {
        User user = User.builder()
                .displayName(displayName)
                .email(email)
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);

        TeamMember member = TeamMember.builder()
                .team(team)
                .user(user)
                .role(role)
                .status(status)
                .joinedAt(LocalDateTime.of(2026, 7, 10, 0, 0))
                .build();
        entityManager.persist(member);
        return member;
    }

    private void assertTeamError(Runnable invocation, TeamErrorCode errorCode) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(GeneralException.class)
                .satisfies(exception -> assertThat(((GeneralException) exception).getErrorCode())
                        .isEqualTo(errorCode));
    }
}