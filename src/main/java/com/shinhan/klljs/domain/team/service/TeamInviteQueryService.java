package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.entity.TeamInviteLink;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamInviteLinkRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import com.shinhan.klljs.global.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 로그인 시작 시점의 1차 검증만 담당한다 (사용자 경험용 빠른 실패).
 * 잠금을 동반한 최종 검증은 로그인 완료 후 초대 수락 트랜잭션에서 별도로 수행한다.
 */
@Service
@RequiredArgsConstructor
public class TeamInviteQueryService {

    private final TeamInviteLinkRepository teamInviteLinkRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Long validateAndGetId(String inviteToken) {
        byte[] tokenHash = TokenHasher.sha256(inviteToken);

        TeamInviteLink invite = teamInviteLinkRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.INVALID_INVITE));

        if (!invite.isUsable(LocalDateTime.now(clock))) {
            throw new GeneralException(TeamErrorCode.INVALID_INVITE);
        }

        return invite.getId();
    }
}
