package com.shinhan.klljs.domain.user.repository;

import com.shinhan.klljs.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 사용자 행을 {@code SELECT ... FOR UPDATE}로 잠근 채 조회한다.
     *
     * <h3>왜 users 행을 잠그나</h3>
     * "사용자는 팀 하나에만 속한다"를 지키기 위해서다. 이 규칙을 강제하는 DB 제약이 없어서
     * (team_members의 UNIQUE는 팀 단위라 여러 팀에 걸친 중복은 못 막는다) 애플리케이션이 세야 하는데,
     * 두 요청이 동시에 "소속 팀 없음"을 읽고 각자 팀을 만들면 팀이 두 개 생긴다.
     * 팀 생성 시점엔 잠글 team_members 행이 아직 없으므로, <b>항상 존재하는</b> users 행을 대신 잠가
     * 한 사용자의 팀 생성을 한 줄로 세운다 (TeamRepository.findByIdForUpdate와 같은 패턴).
     *
     * <b>⚠️ 트랜잭션의 첫 DB 읽기여야 한다.</b> 잠금 읽기는 REPEATABLE READ 스냅샷을 만들지 않으므로,
     * 블록이 풀린 뒤 처음 하는 일반 SELECT가 그 시점 스냅샷을 떠야 상대의 커밋이 보인다.
     * 자세한 이유는 {@code TeamRepository.findByIdForUpdate} 참고.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
