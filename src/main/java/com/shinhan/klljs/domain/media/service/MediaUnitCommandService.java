package com.shinhan.klljs.domain.media.service;

import com.shinhan.klljs.domain.media.dto.MediaUnitCreateRequest;
import com.shinhan.klljs.domain.media.dto.MediaUnitCreateResponse;
import com.shinhan.klljs.domain.media.entity.MediaUnit;
import com.shinhan.klljs.domain.media.entity.MediaUnitShapeType;
import com.shinhan.klljs.domain.media.entity.MediaUnitStatus;
import com.shinhan.klljs.domain.media.repository.MediaUnitRepository;
import com.shinhan.klljs.global.apiPayload.code.GeneralErrorCode;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 인증 없는 MVP 관리자 API가 전달한 매체 마스터 데이터를 저장한다. */
@Service
@RequiredArgsConstructor
public class MediaUnitCommandService {

    public static final String MVP_BOARD_CODE = "board_gangnam_01";
    public static final String MVP_DEVICE_CODE = "adscope-cam-01";

    private final MediaUnitRepository mediaUnitRepository;

    @Transactional
    public MediaUnitCreateResponse create(MediaUnitCreateRequest request) {
        List<MediaUnitShapeType> shapeTypes = List.copyOf(request.shapeTypes());
        if (shapeTypes.stream().distinct().count() != shapeTypes.size()) {
            throw new GeneralException(
                    GeneralErrorCode.VALIDATION_ERROR,
                    "shapeTypes에는 같은 형태를 중복해서 입력할 수 없습니다."
            );
        }

        // board/device/status는 외부 입력을 신뢰하지 않고 MVP 고정값을 서버가 직접 주입한다.
        MediaUnit mediaUnit = MediaUnit.builder()
                .boardCode(MVP_BOARD_CODE)
                .deviceCode(MVP_DEVICE_CODE)
                .mediaName(normalize(request.mediaName()))
                .photoUrl(normalize(request.photoUrl()))
                .locationAddress(normalize(request.locationAddress()))
                .sido(normalize(request.sido()))
                .sigungu(normalize(request.sigungu()))
                .latitude(request.latitude())
                .longitude(request.longitude())
                .widthMm(request.widthMm())
                .heightMm(request.heightMm())
                .resolutionWidthPx(request.resolutionWidthPx())
                .resolutionHeightPx(request.resolutionHeightPx())
                .shapeTypes(shapeTypes)
                .status(MediaUnitStatus.ACTIVE)
                .build();

        return MediaUnitCreateResponse.from(mediaUnitRepository.save(mediaUnit));
    }

    private String normalize(String value) {
        return value.trim();
    }
}
