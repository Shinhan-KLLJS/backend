package com.shinhan.klljs.domain.team.service;

import com.shinhan.klljs.domain.team.dto.TeamInviteCodeResponse;
import com.shinhan.klljs.domain.team.exception.InviteCodeCollisionException;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamInviteCodeServiceTest {

    @Mock
    private TeamInviteCodeTransactionService transactionService;

    @InjectMocks
    private TeamInviteCodeService service;

    @Test
    void issue_retriesTokenCollisionWithNewTransactionCall() {
        TeamInviteCodeResponse expected = new TeamInviteCodeResponse(
                "ABC1234", OffsetDateTime.parse("2027-07-10T09:00:00+09:00"));
        when(transactionService.issueOnce(1L, 2L))
                .thenThrow(new InviteCodeCollisionException(new RuntimeException()))
                .thenThrow(new InviteCodeCollisionException(new RuntimeException()))
                .thenReturn(expected);

        TeamInviteCodeResponse result = service.issue(1L, 2L);

        assertThat(result).isEqualTo(expected);
        verify(transactionService, times(3)).issueOnce(1L, 2L);
    }

    @Test
    void issue_throwsDomainErrorAfterThreeCollisions() {
        when(transactionService.issueOnce(1L, 2L))
                .thenThrow(new InviteCodeCollisionException(new RuntimeException()));

        assertThatThrownBy(() -> service.issue(1L, 2L))
                .isInstanceOf(GeneralException.class)
                .satisfies(exception -> assertThat(((GeneralException) exception).getErrorCode())
                        .isEqualTo(TeamErrorCode.INVITE_CODE_GENERATION_FAILED));
        verify(transactionService, times(3)).issueOnce(1L, 2L);
    }
}