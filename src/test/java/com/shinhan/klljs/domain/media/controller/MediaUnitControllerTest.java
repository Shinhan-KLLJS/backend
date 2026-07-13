package com.shinhan.klljs.domain.media.controller;

import com.shinhan.klljs.domain.media.repository.MediaUnitRepository;
import com.shinhan.klljs.global.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.local-test-data.enabled=false")
@AutoConfigureMockMvc
@Transactional
class MediaUnitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private MediaUnitRepository mediaUnitRepository;

    @Test
    void create_withoutAuthenticationStoresServerControlledVisionCodes() throws Exception {
        mockMvc.perform(post("/api/v1/admin/media-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("[\"FLAT\", \"VERTICAL\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("COMMON_201_001"))
                .andExpect(jsonPath("$.result.boardCode").value("board_gangnam_01"))
                .andExpect(jsonPath("$.result.deviceCode").value("adscope-cam-01"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"));

        assertThat(mediaUnitRepository.findAll()).singleElement().satisfies(media -> {
            assertThat(media.getMediaName()).isEqualTo("삼성동 전광판");
            assertThat(media.getSido()).isEqualTo("서울특별시");
            assertThat(media.getShapeTypes()).hasSize(2);
        });
    }

    @Test
    void create_rejectsDuplicateShapeTypes() throws Exception {
        mockMvc.perform(post("/api/v1/admin/media-units")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("[\"FLAT\", \"FLAT\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));
    }

    @Test
    void getMediaUnits_requiresAuthenticationAndCampaignDates() throws Exception {
        mockMvc.perform(get("/api/v1/media-units")
                        .param("executionStartDate", "2026-07-11")
                        .param("executionEndDate", "2026-07-12"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/media-units")
                        .header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_400_001"));

        mockMvc.perform(get("/api/v1/media-units")
                        .header(HttpHeaders.AUTHORIZATION, accessToken())
                        .param("executionStartDate", "2026/07/11")
                        .param("executionEndDate", "2026-07-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_400_001"));
    }

    private String validRequest(String shapeTypes) {
        return """
                {
                  "mediaName": "  삼성동 전광판  ",
                  "photoUrl": "https://cdn.example.com/media.jpg",
                  "locationAddress": "서울 강남구 영동대로 506",
                  "sido": "서울특별시",
                  "sigungu": "강남구",
                  "latitude": 37.5090123,
                  "longitude": 127.0631145,
                  "widthMm": 81000,
                  "heightMm": 20000,
                  "resolutionWidthPx": 1312,
                  "resolutionHeightPx": 1664,
                  "shapeTypes": %s
                }
                """.formatted(shapeTypes);
    }

    private String accessToken() {
        return "Bearer " + jwtTokenService.generateAccessToken(42L);
    }
}
