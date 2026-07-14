package com.shinhan.klljs.domain.team.repository;

import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamStatus;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * teams 행 잠금 조회.
 *
 * <h3>진짜 동시성은 여기서 검증되지 않는다</h3>
 * 이 테스트는 H2로 도는데, H2는 <b>READ COMMITTED</b>이고 MySQL InnoDB는 <b>REPEATABLE READ</b>가
 * 기본이다. 잠금이 막으려는 버그가 정확히 REPEATABLE READ에서만 나타나므로(스냅샷이 고정돼 상대의
 * 커밋을 못 봄), H2에서 2스레드 경합을 재현해도 <b>잠금을 지운 채로 통과해버린다.</b>
 *
 * 실제 두 스레드 경합 재현은 진짜 MySQL이 필요해 이 스택 범위 밖이다.
 * 여기서 고정하는 것은 "잠금이 실제로 걸려 있는가" 하나다 - 누군가 @Lock을 지우면
 * 조회는 여전히 성공해서 다른 테스트로는 아무것도 잡히지 않는다.
 * teams 잠금(초대·합류·캠페인 등록이 사용)과 users 잠금(팀 생성이 사용) 둘 다 고정한다.
 */
@SpringBootTest
@Transactional
class TeamRepositoryTest {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * <b>잠금이 실제로 걸렸는지</b>를 단언한다. 애노테이션이 붙어 있는지만 보면 부족하다 -
     * Spring Data가 그걸 실제 쿼리에 반영했는지는 별개 문제이기 때문이다.
     * 영속성 컨텍스트에 기록된 잠금 모드를 보면 확실하다.
     *
     * @Lock이 사라지면 findByIdForUpdate는 평범한 SELECT가 되고, 동시 제출은 다시 UNIQUE 위반
     * 500(최초 제출)이나 승인 상태 퇴행(재제출)으로 돌아간다. 그런데 <b>조회 자체는 멀쩡히
     * 성공해서 다른 어떤 테스트도 이 회귀를 잡지 못한다.</b>
     */
    @Test
    void findByIdForUpdate_acquiresPessimisticWriteLock() {
        Team saved = teamRepository.save(Team.builder().teamName("루비 광고").status(TeamStatus.ACTIVE).build());
        // 잠금 조회가 캐시 히트로 끝나지 않도록 비운다. 안 그러면 SELECT ... FOR UPDATE가 나가지 않는다.
        entityManager.flush();
        entityManager.clear();

        Team locked = teamRepository.findByIdForUpdate(saved.getId()).orElseThrow();

        assertThat(locked.getTeamName()).isEqualTo("루비 광고");
        assertThat(entityManager.getLockMode(locked))
                .as("findByIdForUpdate가 팀 행을 잠그지 않으면 동시 제출이 다시 깨진다")
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    /** 잠금 조회여도 없는 팀은 예외가 아니라 빈 값이다 (호출부가 403으로 변환한다). */
    @Test
    void findByIdForUpdate_isEmptyWhenTeamDoesNotExist() {
        assertThat(teamRepository.findByIdForUpdate(999_999L)).isEmpty();
    }

    /**
     * users 행 잠금도 같은 이유로 고정한다 - 팀 생성의 "사용자당 팀 하나" 직렬화가 통째로
     * 이 잠금에 기대고 있는데, @Lock이 사라져도 조회는 멀쩡히 성공해 아무 테스트도 회귀를 못 잡는다.
     */
    @Test
    void userFindByIdForUpdate_acquiresPessimisticWriteLock() {
        User saved = userRepository.save(User.builder()
                .displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build());
        entityManager.flush();
        entityManager.clear();

        User locked = userRepository.findByIdForUpdate(saved.getId()).orElseThrow();

        assertThat(entityManager.getLockMode(locked))
                .as("users 행을 잠그지 않으면 한 사용자의 동시 팀 생성이 팀 두 개를 만든다")
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
