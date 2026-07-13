package com.shinhan.klljs.domain.media.repository;

import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaUnitRepository extends JpaRepository<MediaUnit, Long> {

    /**
     * Vision 메시지의 board_id/device_id로 매체를 찾는다 (SQS consumer의 매체 매핑 단계).
     * board_code/device_code 둘 다 DB에서 각각 unique라, 원래는 board_code 하나만으로도
     * 유일하게 식별되지만 메시지에 device_id도 같이 오니 둘 다 일치하는지 확인해서
     * "이 매체에 이 카메라가 실제로 연결돼 있는가"까지 검증한다 (카메라 교체 시
     * MediaUnit.replaceDevice()로 device_code를 갱신하므로, 옛 device_id로 오는 메시지는
     * 이 조건에서 걸러진다 - 교체 전 카메라가 계속 잘못된 데이터를 보내는 상황 방지).
     */
    Optional<MediaUnit> findFirstByBoardCodeAndDeviceCodeOrderByIdAsc(String boardCode, String deviceCode);

    List<MediaUnit> findAllByStatusOrderByMediaNameAscIdAsc(MediaUnitStatus status);
}
