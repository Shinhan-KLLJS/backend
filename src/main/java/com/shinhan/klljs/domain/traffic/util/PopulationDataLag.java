package com.shinhan.klljs.domain.traffic.util;

/**
 * 서울시 250m격자 생활인구는 발행까지 약 4일이 걸린다(공공데이터포털 안내). 여유를 더해 "오늘"을
 * 이 값만큼 이전 날짜로 매핑해서 조회한다 - {@link com.shinhan.klljs.domain.vision.service.FunnelService}의
 * 전체 유동인구 조회와 수동 적재 API({@code PopulationIngestController}) 양쪽이 이 값을 공유한다.
 */
public final class PopulationDataLag {

    public static final int DAYS = 6;

    private PopulationDataLag() {
    }
}
