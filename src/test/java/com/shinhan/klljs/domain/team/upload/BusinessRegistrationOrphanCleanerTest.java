package com.shinhan.klljs.domain.team.upload;

import com.shinhan.klljs.domain.team.repository.TeamBusinessRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * orphan 정리 배치 (team-creation-api-spec.md 9절).
 *
 * <b>가장 중요한 계약: 참조되고 있는 파일은 절대 지우지 않는다.</b> 여기가 깨지면 정상적으로 등록된
 * 팀의 사업자등록증 원본이 사라진다 - 복구할 수 없는 사고다.
 */
@ExtendWith(MockitoExtension.class)
class BusinessRegistrationOrphanCleanerTest {

    private static final Instant NOW = Instant.parse("2026-07-13T04:30:00Z");
    private static final int RETENTION_DAYS = 2;

    @Mock
    private BusinessRegistrationDocumentStorage storage;

    @Mock
    private TeamBusinessRegistrationRepository registrationRepository;

    private BusinessRegistrationOrphanCleaner cleaner() {
        return new BusinessRegistrationOrphanCleaner(
                storage, registrationRepository, Clock.fixed(NOW, ZoneOffset.UTC), RETENTION_DAYS);
    }

    /** DB가 참조하는 키는 남기고, 참조가 없는 키만 지운다. */
    @Test
    void cleanUp_deletesOnlyTheKeysNoRegistrationRefersTo() {
        String referenced = "team-registrations/live.pdf";
        String orphan = "team-registrations/abandoned.pdf";
        givenS3Page(List.of(referenced, orphan));
        given(registrationRepository.findReferencedKeys(anyList())).willReturn(Set.of(referenced));

        cleaner().cleanUpOrphans();

        ArgumentCaptor<List<String>> deleted = ArgumentCaptor.captor();
        verify(storage).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(orphan);
    }

    /** 페이지 전체가 참조되고 있으면 S3 삭제를 아예 호출하지 않는다 (쓸데없는 API 호출도 비용이다). */
    @Test
    void cleanUp_doesNotCallS3WhenEveryKeyIsReferenced() {
        List<String> keys = List.of("team-registrations/a.pdf", "team-registrations/b.pdf");
        givenS3Page(keys);
        given(registrationRepository.findReferencedKeys(anyList())).willReturn(Set.copyOf(keys));

        cleaner().cleanUpOrphans();

        verify(storage, never()).deleteAll(anyList());
    }

    /**
     * 보관 기간(2일)을 기준으로 cutoff를 계산해 S3에 넘긴다.
     *
     * 이 값이 짧아지면 <b>팀 생성을 진행 중인 사용자의 파일이 지워진다</b> - 업로드 토큰은 1시간
     * 유효한데, 그 안에 파일이 사라지면 "서명이 유효하다 = 객체가 존재한다"는 전제가 깨진다.
     */
    @Test
    void cleanUp_asksS3ForObjectsOlderThanTheRetentionPeriod() {
        givenS3Page(List.of());

        cleaner().cleanUpOrphans();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.captor();
        verify(storage).forEachKeyOlderThan(cutoff.capture(), any());
        assertThat(cutoff.getValue()).isEqualTo(NOW.minus(RETENTION_DAYS, ChronoUnit.DAYS));
    }

    /** S3가 터져도 배치가 애플리케이션을 죽이지 않는다. 다음 회차가 같은 대상을 다시 잡는다. */
    @Test
    void cleanUp_swallowsStorageFailureSoTheSchedulerKeepsRunning() {
        willAnswer(invocation -> {
            throw new RuntimeException("S3 불통");
        }).given(storage).forEachKeyOlderThan(any(), any());

        cleaner().cleanUpOrphans();

        verify(storage, never()).deleteAll(anyList());
    }

    /** S3가 키 한 페이지를 돌려주는 상황을 흉내 낸다. */
    @SuppressWarnings("unchecked")
    private void givenS3Page(List<String> keys) {
        willAnswer(invocation -> {
            if (!keys.isEmpty()) {
                ((Consumer<List<String>>) invocation.getArgument(1)).accept(keys);
            }
            return null;
        }).given(storage).forEachKeyOlderThan(any(), any());
    }
}
