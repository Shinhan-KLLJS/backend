package com.shinhan.klljs.domain.media.entity;

/** 관리자 입력과 캠페인 매체 목록에서 사용하는 화면 형태 분류다. */
public enum MediaUnitShapeType {
    FLAT,
    VERTICAL,
    /** MVP에서는 곡선형과 코너형을 같은 형태로 취급한다. */
    CORNER
}
