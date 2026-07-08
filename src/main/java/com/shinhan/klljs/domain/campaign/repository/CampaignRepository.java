package com.shinhan.klljs.domain.campaign.repository;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByTeamIdIn(List<Long> teamIds);
}
