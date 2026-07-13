package com.shinhan.klljs.domain.team.repository;

import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 홈 대시보드의 팀 기반 접근 제어에 쓰는 리포지토리.
 * 두 메서드 모두 status 파라미터를 반드시 TeamMemberStatus.ACTIVE로 넘겨서 호출한다 -
 * LEFT(팀 탈퇴)나 REMOVED(강퇴)된 멤버는 예전에 그 팀에 있었더라도 더 이상 접근 권한이 없어야 하기 때문이다.
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
     */
    boolean existsByUserIdAndTeamIdAndStatus(Long userId, Long teamId, TeamMemberStatus status);

    Optional<TeamMember> findByUserIdAndTeamIdAndStatus(Long userId, Long teamId, TeamMemberStatus status);

    @Query("select tm from TeamMember tm join fetch tm.user where tm.team.id = :teamId and tm.status = :status")
    List<TeamMember> findAllWithUserByTeamIdAndStatus(
            @Param("teamId") Long teamId,
            @Param("status") TeamMemberStatus status
    );
}
