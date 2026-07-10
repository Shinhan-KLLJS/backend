package com.shinhan.klljs.domain.user.service;

import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.user.dto.UserMeResponse;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserQueryServiceTest {

    @Autowired
    private UserQueryService userQueryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Test
    void getMe_returnsUserInfoWithEmptyTeamsWhenNoTeam() {
        User user = userRepository.save(
                User.builder().displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build()
        );

        UserMeResponse result = userQueryService.getMe(user.getId());

        assertThat(result.id()).isEqualTo(user.getId());
        assertThat(result.displayName()).isEqualTo("철수");
        assertThat(result.teams()).isEmpty();
    }

    @Test
    void getMe_throwsWhenUserDoesNotExist() {
        assertThatThrownBy(() -> userQueryService.getMe(999_999L))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void getMe_includesActiveTeamMembershipsWithNameAndRole() {
        User user = userRepository.save(
                User.builder().displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build()
        );
        Team team = teamRepository.save(Team.builder().teamName("팀A").status(TeamStatus.ACTIVE).build());
        teamMemberRepository.save(TeamMember.builder()
                .team(team).user(user).role(TeamMemberRole.OWNER).status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build());

        UserMeResponse result = userQueryService.getMe(user.getId());

        assertThat(result.teams()).hasSize(1);
        assertThat(result.teams().get(0).teamId()).isEqualTo(team.getId());
        assertThat(result.teams().get(0).teamName()).isEqualTo("팀A");
        assertThat(result.teams().get(0).role()).isEqualTo(TeamMemberRole.OWNER);
    }

    @Test
    void getMe_excludesLeftOrRemovedTeamMemberships() {
        User user = userRepository.save(
                User.builder().displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build()
        );
        Team leftTeam = teamRepository.save(Team.builder().teamName("탈퇴한 팀").status(TeamStatus.ACTIVE).build());
        teamMemberRepository.save(TeamMember.builder()
                .team(leftTeam).user(user).role(TeamMemberRole.MEMBER).status(TeamMemberStatus.LEFT)
                .joinedAt(LocalDateTime.now())
                .build());

        UserMeResponse result = userQueryService.getMe(user.getId());

        // 탈퇴(LEFT)한 팀은 한때 소속이었어도 더 이상 "소속 팀"으로 보이면 안 된다.
        assertThat(result.teams()).isEmpty();
    }
}
