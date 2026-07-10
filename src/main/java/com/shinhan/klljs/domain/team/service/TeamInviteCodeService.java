package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamInviteCodeResponse;
import com.shinhan.klljs.domain.team.exception.InviteCodeCollisionException;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeamInviteCodeService {

    private static final int MAX_GENERATION_ATTEMPTS = 3;

    private final TeamInviteCodeTransactionService transactionService;

    public TeamInviteCodeResponse issue(Long requesterId, Long teamId) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            try {
                return transactionService.issueOnce(requesterId, teamId);
            } catch (InviteCodeCollisionException ignored) {
                // Each call crosses a separate transactional proxy boundary.
            }
        }
        throw new GeneralException(TeamErrorCode.INVITE_CODE_GENERATION_FAILED);
    }
}