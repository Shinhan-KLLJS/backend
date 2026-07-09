package com.shinhan.klljs.domain.campaign.repository;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
