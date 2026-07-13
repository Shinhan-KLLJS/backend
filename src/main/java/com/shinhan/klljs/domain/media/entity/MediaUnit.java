package com.shinhan.klljs.domain.media.entity;

import com.shinhan.klljs.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vision 장비(카메라)는 매체에 내장된 것으로 간주 (board_code, device_code를 매체가 직접 가짐).
 * 카메라가 물리적으로 교체돼도 새 행을 만들지 않고 deviceCode를 갱신해서 과거 데이터 연결을 유지한다.
 */
@Entity
@Table(name = "media_units")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaUnit extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Vision 메시지의 board_id
    @Column(name = "board_code", nullable = false, length = 100)
    private String boardCode;

    // Vision 메시지의 device_id
    @Column(name = "device_code", nullable = false, length = 100)
    private String deviceCode;

    @Column(name = "media_name", nullable = false, length = 200)
    private String mediaName;

    @Column(name = "photo_url", nullable = false, length = 2048)
    private String photoUrl;

    @Column(name = "location_address", nullable = false, length = 500)
    private String locationAddress;

    // 시/도. 표시용 locationAddress와 별개로 지역 필터링/검색을 위해 구조화해서 저장한다
    // (주소 검색 API 응답을 그대로 넣는다 - locationAddress를 파싱해서 채우지 않는다).
    @Column(name = "sido", nullable = false, length = 20)
    private String sido;

    @Column(name = "sigungu", nullable = false, length = 50)
    private String sigungu;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude; // 위도

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude; // 경도

    @Column(name = "width_mm", nullable = false)
    private Integer widthMm;

    @Column(name = "height_mm", nullable = false)
    private Integer heightMm;

    @Column(name = "resolution_width_px", nullable = false)
    private Integer resolutionWidthPx;

    @Column(name = "resolution_height_px", nullable = false)
    private Integer resolutionHeightPx;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shape_types", nullable = false)
    private List<MediaUnitShapeType> shapeTypes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MediaUnitStatus status;

    @Builder
    public MediaUnit(String boardCode, String deviceCode, String mediaName, String photoUrl,
                      String locationAddress, String sido, String sigungu, BigDecimal latitude, BigDecimal longitude,
                      Integer widthMm, Integer heightMm, Integer resolutionWidthPx, Integer resolutionHeightPx,
                      List<MediaUnitShapeType> shapeTypes, MediaUnitStatus status) {
        this.boardCode = boardCode;
        this.deviceCode = deviceCode;
        this.mediaName = mediaName;
        this.photoUrl = photoUrl;
        this.locationAddress = locationAddress;
        this.sido = sido;
        this.sigungu = sigungu;
        this.latitude = latitude;
        this.longitude = longitude;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.resolutionWidthPx = resolutionWidthPx;
        this.resolutionHeightPx = resolutionHeightPx;
        this.shapeTypes = shapeTypes;
        this.status = status;
    }

    /** 카메라 교체 시 사용 — 새 media_unit 행을 만들지 않는다. */
    public void replaceDevice(String newDeviceCode) {
        this.deviceCode = newDeviceCode;
    }

    public void changeStatus(MediaUnitStatus status) {
        this.status = status;
    }
}