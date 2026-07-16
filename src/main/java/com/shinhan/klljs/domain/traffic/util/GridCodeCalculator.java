package com.shinhan.klljs.domain.traffic.util;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

import java.math.BigDecimal;

/**
 * 매체 위경도(WGS84) -&gt; 서울시 250m격자 생활인구의 격자코드("다사NNNNNNNN") 변환.
 *
 * <p>서울시 생활인구는 국토교통부 국토격자체계(국가지점번호) 기준이다. UTM-K(EPSG:5179) 좌표계에서
 * 100km 단위 구역을 한글 두 글자로, 그 안의 위치를 250m 단위로 내림한 좌표의 10m 단위 오프셋
 * 8자리 숫자로 나타낸다. 서울 전역(위경도로 약 37.4~37.7, 126.7~127.2)은 UTM-K로 변환하면
 * X:900,000~1,000,000m, Y:1,900,000~2,000,000m 범위에 들어가 "다사" 구역 하나를 벗어나지 않으므로,
 * 이 프로젝트는 매체가 서울에만 있다는 전제로 그 구역 하나만 상수로 둔다 (다른 지역으로 확장되면
 * 100km 구역 전체 매핑표가 필요하다).</p>
 *
 * <p>공식은 실제 위경도를 EPSG:5179로 변환한 좌표 및 국토정보맵 250m 격자 셰이프파일의 geometry와
 * 대조해 검증했다 (같은 행정동 기준으로 세 소스가 동일한 250m 범위로 수렴함을 확인).</p>
 */
public final class GridCodeCalculator {

    private static final String SEOUL_ZONE_PREFIX = "다사";
    private static final long BASE_X = 900_000L;
    private static final long BASE_Y = 1_900_000L;
    private static final int GRID_SIZE_METER = 250;
    private static final int OFFSET_UNIT_METER = 10;

    private static final CRSFactory CRS_FACTORY = new CRSFactory();
    // EPSG 조회 테이블에 의존하지 않고, 국토정보맵 셰이프파일의 .prj에서 직접 확인한 파라미터로
    // 정의한다 (proj4j 코어 배포판에 EPSG:5179가 없을 수 있어 자체 정의가 더 안전하다).
    private static final CoordinateReferenceSystem WGS84 =
            CRS_FACTORY.createFromParameters("WGS84", "+proj=longlat +datum=WGS84 +no_defs");
    private static final CoordinateReferenceSystem UTMK =
            CRS_FACTORY.createFromParameters("EPSG:5179",
                    "+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 +x_0=1000000 +y_0=2000000 +ellps=GRS80 +units=m +no_defs");
    private static final CoordinateTransformFactory TRANSFORM_FACTORY = new CoordinateTransformFactory();

    private GridCodeCalculator() {
    }

    public static String calculate(BigDecimal latitude, BigDecimal longitude) {
        ProjCoordinate wgs84Coordinate = new ProjCoordinate(longitude.doubleValue(), latitude.doubleValue());
        ProjCoordinate utmkCoordinate = new ProjCoordinate();
        TRANSFORM_FACTORY.createTransform(WGS84, UTMK).transform(wgs84Coordinate, utmkCoordinate);

        long xMin = floorToGrid(utmkCoordinate.x);
        long yMin = floorToGrid(utmkCoordinate.y);

        long offsetX = (xMin - BASE_X) / OFFSET_UNIT_METER;
        long offsetY = (yMin - BASE_Y) / OFFSET_UNIT_METER;

        return SEOUL_ZONE_PREFIX + pad4(offsetX) + pad4(offsetY);
    }

    private static long floorToGrid(double meter) {
        return Math.floorDiv((long) Math.floor(meter), GRID_SIZE_METER) * GRID_SIZE_METER;
    }

    private static String pad4(long value) {
        return String.format("%04d", value);
    }
}
