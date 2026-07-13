package com.shinhan.klljs.domain.team.repository;

import com.shinhan.klljs.domain.team.entity.Team;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /**
     * 팀 행을 {@code SELECT ... FOR UPDATE}로 잠근 채 조회한다.
     *
     * <h3>왜 teams 행을 잠그나 (정작 지키려는 건 다른 테이블인데)</h3>
     * 팀에 딸린 "팀당 하나"짜리 행들 - 사업자등록({@code team_business_registrations.team_id} UNIQUE),
     * 활성 초대 코드({@code team_invite_links.active_code_marker} UNIQUE) - 을 만들 때,
     * <b>최초 생성 시점에는 잠글 행 자체가 없다.</b> 두 요청이 동시에 "없네"를 읽고 각자 INSERT하면
     * UNIQUE 제약에 걸려 한쪽이 예외로 죽는다(사용자에겐 500).
     * 그래서 <b>항상 존재하는</b> teams 행을 대신 잠가 같은 팀에 대한 요청을 한 줄로 세운다.
     *
     * <h3>⚠️ 트랜잭션의 첫 DB 읽기여야 한다</h3>
     * 잠금 읽기(locking read)는 MySQL의 REPEATABLE READ 스냅샷을 만들지 않는다. 앞선 트랜잭션이
     * 커밋할 때까지 여기서 블록됐다가, 풀려난 뒤 <b>처음 수행하는 일반 SELECT</b>가 그 시점의
     * 스냅샷을 뜬다 - 그래야 상대가 방금 커밋한 값을 볼 수 있다.
     * 이 메서드보다 먼저 다른 조회를 하면 스냅샷이 그 시점에 굳어버려, 블록이 풀려도 상대의 커밋을
     * 못 보고 잠금이 무의미해진다.
     *
     * 사용처: {@code BusinessRegistrationWriteService.save()}, {@code TeamInviteCodeTransactionService.issueOnce()},
     * {@code TeamJoinService}, {@code TeamMemberManagementService}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id = :teamId")
    Optional<Team> findByIdForUpdate(@Param("teamId") Long teamId);
}
