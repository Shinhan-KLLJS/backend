package com.shinhan.klljs.domain.campaign.controller;

import com.shinhan.klljs.domain.campaign.dto.CampaignCreativeUploadRequest;
import com.shinhan.klljs.domain.campaign.dto.CampaignCreativeUploadResponse;
import com.shinhan.klljs.domain.campaign.service.CampaignCreativeUploadService;
import com.shinhan.klljs.global.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CampaignCreativeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CampaignCreativeUploadService uploadService;

    @Test
    void issueUploadUrl_withAccessTokenReturnsUploadContract() throws Exception {
        CampaignCreativeUploadResponse response = new CampaignCreativeUploadResponse(
                "https://s3.example.com/presigned",
                "https://cdn.example.com/campaign-creatives/42/object-id",
                "PUT",
                Map.of("Content-Type", "video/mp4"),
                "v1.payload.signature",
                OffsetDateTime.parse("2026-07-13T15:00:00+09:00")
        );
        when(uploadService.issue(eq(42L), any(CampaignCreativeUploadRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/campaign-creatives/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenService.generateAccessToken(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "creativeType": "VIDEO",
                                  "originalFilename": "summer.mp4",
                                  "contentType": "video/mp4"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.method").value("PUT"))
                .andExpect(jsonPath("$.result.creativeUrl")
                        .value("https://cdn.example.com/campaign-creatives/42/object-id"))
                .andExpect(jsonPath("$.result.creativeToken").value("v1.payload.signature"));
    }

    @Test
    void issueUploadUrl_withoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/campaign-creatives/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issueUploadUrl_withBlankMetadataReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/campaign-creatives/upload-url")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenService.generateAccessToken(42L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "creativeType": "IMAGE",
                                  "originalFilename": " ",
                                  "contentType": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));

        verifyNoInteractions(uploadService);
    }
}
