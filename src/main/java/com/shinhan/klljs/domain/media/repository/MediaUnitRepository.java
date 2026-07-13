package com.shinhan.klljs.domain.media.repository;

import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MediaUnitRepository extends JpaRepository<MediaUnit, Long> {

    /**
     * 공용 Vision 장비의 board/device 코드에 연결된 모든 ACTIVE 매체를 ID 순서로 가져온다.
     * MVP에서는 여러 매체가 같은 고정 코드를 공유하므로 단건 Optional 조회를 사용하면 안 된다.
     */
    List<MediaUnit> findAllByBoardCodeAndDeviceCodeAndStatusOrderByIdAsc(
            String boardCode,
            String deviceCode,
            MediaUnitStatus status
    );

    List<MediaUnit> findAllByStatusOrderByMediaNameAscIdAsc(MediaUnitStatus status);

    /** 기간 충돌 검사와 저장이 끝날 때까지 같은 매체의 등록 요청을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaUnit m where m.id = :id")
    Optional<MediaUnit> findByIdForUpdate(@Param("id") Long id);
}
