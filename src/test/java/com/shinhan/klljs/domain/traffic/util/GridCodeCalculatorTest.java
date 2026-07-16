package com.shinhan.klljs.domain.traffic.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기대값은 이 클래스와 독립적으로(pyproj로 WGS84 -&gt; EPSG:5179 변환 후 수기 계산) 검증한 값이다.
 * 예: 경복궁(37.5796, 126.9770) -&gt; pyproj 변환 X=953820.9, Y=1953486.0 -&gt; 250m 내림 -&gt;
 * X_min=953750, Y_min=1953250 -&gt; Nx=(953750-900000)/10=5375, Ny=(1953250-1900000)/10=5325.
 */
class GridCodeCalculatorTest {

    @Test
    void calculate_gyeongbokgung_matchesIndependentlyComputedGridCode() {
        String gridCode = GridCodeCalculator.calculate(new BigDecimal("37.5796"), new BigDecimal("126.9770"));

        assertThat(gridCode).isEqualTo("다사53755325");
    }

    @Test
    void calculate_cheonguhyojaDongArea_matchesIndependentlyComputedGridCode() {
        String gridCode = GridCodeCalculator.calculate(new BigDecimal("37.5880"), new BigDecimal("126.9680"));

        assertThat(gridCode).isEqualTo("다사53005425");
    }

    @Test
    void calculate_isGridAligned_offsetsAreMultiplesOf25() {
        // 250m 격자는 10m 단위 오프셋이 반드시 25의 배수(250/10)여야 한다.
        String gridCode = GridCodeCalculator.calculate(new BigDecimal("37.5665"), new BigDecimal("126.9780"));

        String offsetXPart = gridCode.substring(2, 6);
        String offsetYPart = gridCode.substring(6, 10);
        assertThat(Integer.parseInt(offsetXPart) % 25).isZero();
        assertThat(Integer.parseInt(offsetYPart) % 25).isZero();
    }
}
