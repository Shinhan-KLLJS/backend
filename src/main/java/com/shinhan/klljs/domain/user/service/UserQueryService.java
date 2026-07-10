package com.shinhan.klljs.domain.user.service;

import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.user.dto.UserMeResponse;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.exception.UserErrorCode;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;

    /**
     * 내 정보 조회. hasTeam/teamId도 같이 내려준다 - 프론트가 로그인 직후 이 API 하나로
     * "팀 생성/합류 온보딩 화면을 보여줘야 하는지"를 바로 판단할 수 있게 하기 위함이다.
     */
    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        List<Long> teamIds = teamMemberRepository.findTeamIdsByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE);

        return UserMeResponse.from(user, teamIds);
    }
}
