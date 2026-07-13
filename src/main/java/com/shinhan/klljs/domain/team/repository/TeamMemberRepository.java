package com.shinhan.klljs.domain.team.repository;

import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 팀 기반 접근 제어와 팀원 관리에 쓰는 리포지토리 (홈 대시보드, 사업자등록증, 초대 코드, 팀원 관리).
 *
 * status를 받는 메서드는 {@code findByUserIdAndTeamId} 하나를 빼면 전부 TeamMemberStatus.ACTIVE로 넘겨 호출한다 -
 * LEFT(팀 탈퇴)나 REMOVED(강퇴)된 멤버는 예전에 그 팀에 있었더라도 더 이상 접근 권한이 없어야 하기 때문이다.
 * {@code findByUserIdAndTeamId}만 예외인데, 탈퇴했던 사람이 다시 합류하는 경우를 판별해야 해서 status를 가리지 않는다.
 */
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    /**
     * 이 사용자가 ACTIVE로 속한 모든 team_id 목록.
     * 캠페인 목록 조회에서 "내가 볼 수 있는 캠페인 전체"를 구하기 위해 사용한다 -
     * 사용자가 여러 팀에 속해 있으면 그 팀들의 캠페인을 전부 합쳐서 보여준다.
     * JPQL에서 tm.team.id로 연관관계를 타고 들어가 team_id만 뽑아오므로,
     * Team/TeamMember 엔티티 전체를 로딩하지 않고 ID 목록만 가볍게 조회한다.
     */
    @Query("select tm.team.id from TeamMember tm where tm.user.id = :userId and tm.status = :status")
    List<Long> findTeamIdsByUserIdAndStatus(@Param("userId") Long userId, @Param("status") TeamMemberStatus status);

    /**
     * 이 사용자가 특정 팀 하나에 ACTIVE로 속해 있는지만 확인한다 (true/false).
     * campaign_id 하나를 다루는 API(상세조회, 송출정보 등)에서 접근 권한만 빠르게 체크할 때 쓴다 -
     * findTeamIdsByUserIdAndStatus처럼 전체 팀 목록을 다 가져올 필요가 없어 더 가볍다.
     *
     * <b>역할까지 봐야 하는 곳에서는 쓸 수 없다</b> - 아래 findRoleByUserIdAndTeamIdAndStatus를 쓴다.
     */
    boolean existsByUserIdAndTeamIdAndStatus(Long userId, Long teamId, TeamMemberStatus status);

    /**
     * 이 사용자가 그 팀에서 가진 역할. ACTIVE 멤버가 아니면(또는 팀이 없으면) 빈 값이다.
     *
     * 역할별로 되는 일이 다른 API에서 쓴다 (mvp-database-erd.md 4절 "권한 기준"):
     * 사업자등록증은 OWNER만, 초대 코드 발급은 OWNER/ADMIN, 캠페인 조회는 MEMBER까지.
     * <b>허용 역할 판정은 각 서비스가 한다</b> - 이 메서드는 역할을 알려주기만 해서
     * 정책이 다른 API들이 한 조회를 함께 쓸 수 있다.
     *
     * 빈 값을 "팀 없음"과 "멤버 아님"으로 구분하지 않는 것은 의도적이다. 구분해서 404/403으로
     * 나눠 응답하면 팀의 존재 여부가 외부에 새어 나간다 - 둘 다 같은 403으로 응답한다.
     */
    @Query("select tm.role from TeamMember tm "
            + "where tm.user.id = :userId and tm.team.id = :teamId and tm.status = :status")
    Optional<TeamMemberRole> findRoleByUserIdAndTeamIdAndStatus(
            @Param("userId") Long userId, @Param("teamId") Long teamId,
            @Param("status") TeamMemberStatus status);

    /** 멤버 엔티티 자체가 필요할 때 쓴다 (역할 변경·강퇴 등 상태를 바꾸는 경우). */
    Optional<TeamMember> findByUserIdAndTeamIdAndStatus(Long userId, Long teamId, TeamMemberStatus status);

    /** status를 가리지 않고 조회한다 - 이미 LEFT/REMOVED된 멤버의 재합류를 판별할 때 쓴다. */
    Optional<TeamMember> findByUserIdAndTeamId(Long userId, Long teamId);

    /** 팀원 목록 조회용. user를 join fetch해서 멤버 수만큼 추가 쿼리가 나가는 N+1을 막는다. */
    @Query("select tm from TeamMember tm join fetch tm.user where tm.team.id = :teamId and tm.status = :status")
    List<TeamMember> findAllWithUserByTeamIdAndStatus(
            @Param("teamId") Long teamId,
            @Param("status") TeamMemberStatus status
    );
}
