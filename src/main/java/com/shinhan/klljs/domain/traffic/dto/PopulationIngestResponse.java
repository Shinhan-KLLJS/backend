package com.shinhan.klljs.domain.traffic.dto;

import java.time.LocalDate;

public record PopulationIngestResponse(LocalDate populationDate, int gridCount) {
}
