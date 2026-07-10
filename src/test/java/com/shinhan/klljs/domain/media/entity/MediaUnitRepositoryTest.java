package com.shinhan.klljs.domain.media.entity;

import com.shinhan.klljs.domain.media.repository.MediaUnitRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MediaUnitRepositoryTest {

    @Autowired
    private MediaUnitRepository mediaUnitRepository;
    @Autowired
    private EntityManager entityManager;

    /**
     * shape_types는 @JdbcTypeCode(SqlTypes.JSON)으로 매핑된 List<MediaUnitShapeType> 컬럼이다.
     * 저장은 컴파일만 되면 별문제 없이 되지만, 다시 읽어올 때 Hibernate가 JSON 배열을
     * List<Enum>으로 정확히 역직렬화하는지는 별도로 검증이 필요해서 만든 테스트다.
     */
    @Test
    void shapeTypes_roundTripsThroughJsonColumn() {
        MediaUnit mediaUnit = MediaUnit.builder()
                .boardCode("board-json-1").deviceCode("device-json-1").mediaName("매체JSON")
                .photoUrl("https://example.com/1.png")
                .locationAddress("서울시 어딘가")
                .widthMm(1200).heightMm(800)
                .resolutionWidthPx(1920).resolutionHeightPx(1080)
                .shapeTypes(List.of(MediaUnitShapeType.FLAT, MediaUnitShapeType.CORNER))
                .status(MediaUnitStatus.ACTIVE)
                .build();
        Long id = mediaUnitRepository.save(mediaUnit).getId();

        // 1차 캐시가 값을 그대로 돌려주면 실제 DB 왕복(직렬화->역직렬화)을 검증한 게 아니므로
        // 영속성 컨텍스트를 비워서 findById가 진짜 DB에서 다시 읽어오게 만든다.
        entityManager.flush();
        entityManager.clear();

        MediaUnit reloaded = mediaUnitRepository.findById(id).orElseThrow();

        assertThat(reloaded.getShapeTypes())
                .containsExactly(MediaUnitShapeType.FLAT, MediaUnitShapeType.CORNER);
    }
}
