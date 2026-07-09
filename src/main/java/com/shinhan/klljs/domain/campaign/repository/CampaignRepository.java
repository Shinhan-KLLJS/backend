package com.shinhan.klljs.domain.campaign.repository;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /**
     * 캠페인 목록 조회용. 팀 ID 여러 개를 한 번에 받아서(사용자가 여러 팀에 속할 수 있으므로)
     * 그 팀들 소유의 캠페인을 전부 가져온다. Spring Data JPA가 메서드 이름만 보고
     * "Campaign.team.id in (:teamIds)" 쿼리를 자동으로 만들어준다 (Campaign 엔티티의
     * team 연관관계를 타고 들어가는 파생 쿼리라 별도 @Query 없이도 동작한다).
     *
     * keyword/status 필터링과 정렬은 여기서 하지 않고 서비스 계층에서 처리한다 -
     * 팀당 캠페인 개수가 많지 않을 걸로 예상되어(수십 건 단위), DB 쿼리를 필터 조합별로
     * 여러 개 만드는 것보다 일단 다 가져와서 메모리에서 거르는 편이 코드가 단순하다.
     */
    List<Campaign> findByTeamIdIn(List<Long> teamIds);

    /**
     * SQS consumer가 Vision 메시지 하나를 어느 캠페인에 귀속시킬지 찾을 때 쓴다
     * (스펙 5-1절 "매체와 event_time 기준으로 현재 송출 중인 캠페인을 연결한다").
     * event_time의 KST 날짜가 캠페인 집행 기간(execution_start_date ~ execution_end_date)
     * 안에 들어오는 캠페인을 찾는다. 정상적인 상황이면 한 매체에 같은 시점 캠페인은
     * 하나뿐이어야 하지만(캠페인 확정 시 겹침 검증 예정, 아직 미구현), 방어적으로 List로 받는다.
     */
    @Query("""
            select c from Campaign c
            where c.mediaUnit.id = :mediaUnitId
              and c.executionStartDate <= :eventDateKst
              and c.executionEndDate >= :eventDateKst
            """)
    List<Campaign> findActiveCampaignsForMediaUnit(
            @Param("mediaUnitId") Long mediaUnitId,
            @Param("eventDateKst") LocalDate eventDateKst
    );
}
