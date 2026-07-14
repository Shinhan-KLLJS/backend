package com.shinhan.klljs.domain.campaign.entity;

/**
 * 캠페인에 등록된 소재의 표시 유형이다.
 *
 * <p>MVP에서는 실제 파일 바이트를 분석하지 않으므로 이 값은 업로드 요청자가 선언한 유형이다.
 * 파일 검증이나 트랜스코딩이 도입되기 전까지는 IMAGE와 VIDEO만 저장한다.</p>
 */
public enum CampaignCreativeType {
    IMAGE,
    VIDEO
}
