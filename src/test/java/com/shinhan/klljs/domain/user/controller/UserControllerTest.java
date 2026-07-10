package com.shinhan.klljs.domain.user.controller;

import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Test
    void me_withValidAccessToken_returnsCurrentUserInfo() throws Exception {
        User user = userRepository.save(
                User.builder().displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build()
        );
        String accessToken = jwtTokenService.generateAccessToken(user.getId());

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(user.getId()))
                .andExpect(jsonPath("$.result.displayName").value("철수"))
                .andExpect(jsonPath("$.result.email").value("chulsoo@example.com"))
                .andExpect(jsonPath("$.result.hasTeam").value(false))
                .andExpect(jsonPath("$.result.teamId").doesNotExist());
    }

    @Test
    void me_withActiveTeamMembership_includesTeamInfoInResponse() throws Exception {
        User user = userRepository.save(
                User.builder().displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build()
        );
        Team team = teamRepository.save(Team.builder().teamName("팀A").status(TeamStatus.ACTIVE).build());
        teamMemberRepository.save(TeamMember.builder()
                .team(team).user(user).role(TeamMemberRole.OWNER).status(TeamMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build());
        String accessToken = jwtTokenService.generateAccessToken(user.getId());

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.hasTeam").value(true))
                .andExpect(jsonPath("$.result.teamId").value(team.getId()));
    }

    @Test
    void me_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withGarbageToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage-token"))
                .andExpect(status().isUnauthorized());
    }
}
