package com.shinhan.klljs.domain.traffic.service;

import com.shinhan.klljs.domain.traffic.client.SeoulPopulationFileClient;
import com.shinhan.klljs.domain.traffic.entity.GridPopulationDaily;
import com.shinhan.klljs.domain.traffic.repository.GridPopulationDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 서울시 250m격자 생활인구를 받아와 {@code grid_population_daily}에 upsert한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PopulationIngestService {

    private final SeoulPopulationFileClient seoulPopulationFileClient;
    private final GridPopulationDailyRepository gridPopulationDailyRepository;

    /**
     * populationDate 하루치 격자 데이터를 내려받아 적재한다. 이미 같은 (격자코드, 날짜) 행이
     * 있으면 값을 갱신한다 - 같은 날짜를 다시 적재해도 안전하다(Swagger 수동 재시도 포함).
     *
     * @return 적재(갱신 포함)된 격자 수
     */
    @Transactional
    public int ingest(LocalDate populationDate) {
        Map<String, Long> totalByGridCode = seoulPopulationFileClient.fetchDailyGridPopulation(populationDate);

        Map<String, GridPopulationDaily> existingByGridCode = gridPopulationDailyRepository
                .findByPopulationDate(populationDate).stream()
                .collect(Collectors.toMap(GridPopulationDaily::getGridCode, Function.identity()));

        List<GridPopulationDaily> toInsert = new ArrayList<>();
        for (Map.Entry<String, Long> entry : totalByGridCode.entrySet()) {
            String gridCode = entry.getKey();
            Long total = entry.getValue();
            GridPopulationDaily existing = existingByGridCode.get(gridCode);
            if (existing != null) {
                existing.updateTotalTrafficCount(total);
            } else {
                toInsert.add(GridPopulationDaily.builder()
                        .gridCode(gridCode)
                        .populationDate(populationDate)
                        .totalTrafficCount(total)
                        .build());
            }
        }
        gridPopulationDailyRepository.saveAll(toInsert);

        log.info("생활인구 격자 데이터 적재 완료: populationDate={}, gridCount={}", populationDate, totalByGridCode.size());
        return totalByGridCode.size();
    }
}
