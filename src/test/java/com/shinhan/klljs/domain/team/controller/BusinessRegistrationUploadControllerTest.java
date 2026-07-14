package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.upload.BusinessRegistrationDocumentStorage;
import com.shinhan.klljs.domain.team.upload.BusinessRegistrationOcrClient;
import com.shinhan.klljs.domain.team.upload.OcrResult;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;
import com.shinhan.klljs.domain.user.repository.UserRepository;
import com.shinhan.klljs.global.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import com.shinhan.klljs.domain.team.upload.DocumentFixtures;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 사업자등록증 업로드 엔드포인트의 HTTP 계약.
 *
 * <b>파일 업로드의 오류 경로는 컨트롤러 안에서만 보면 놓친다.</b> Spring이 컨트롤러에 들어오기 전에
 * 예외를 던지는 경우가 있어서, 서비스에 검사를 넣어둬도 실행조차 되지 않고 500이 나갈 수 있다.
 * 실제로 그런 버그가 있었다(파일 미첨부 -> COMMON_500_001). 그래서 여기서 HTTP 레벨로 고정한다.
 */
// local-test-data를 끄는 이유: 이 mock 조합은 새 컨텍스트를 만들므로 켜두면 seed 초기화가
// 다시 돌아 공유 H2에 팀/멤버 행을 남기고, 다른 컨텍스트 테스트의 정리가 FK 위반으로 깨진다.
@SpringBootTest(properties = "app.local-test-data.enabled=false")
@AutoConfigureMockMvc
@Transactional
class BusinessRegistrationUploadControllerTest {

    private static final String PATH = "/api/v1/teams/business-registration";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private BusinessRegistrationDocumentStorage documentStorage;

    @MockitoBean
    private BusinessRegistrationOcrClient ocrClient;

    private String accessToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(
                User.builder().displayName("철수").email("chulsoo@example.com").status(UserStatus.ACTIVE).build());
        this.accessToken = jwtTokenService.generateAccessToken(user.getId());

        given(documentStorage.store(any(), any())).willReturn("team-registrations/doc.png");
        given(ocrClient.extract(anyString())).willReturn(new OcrResult(
                "신한 KLLJS", "이정현", "4959240582", "2024-06-24", "서비스업", "광고대행"));
    }

    @Test
    void upload_returnsOcrFieldsAndASignedToken() throws Exception {
        mockMvc.perform(multipart(PATH)
                        .file(new MockMultipartFile("file", "reg.png", "image/png", png()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.documentStorageKey").exists())
                .andExpect(jsonPath("$.result.ocrResult.companyName").value("신한 KLLJS"))
                .andExpect(jsonPath("$.result.ocrResult.businessOpeningDate").value("2024-06-24"))
                // 업태·종목도 화면에 보여줄 수 있게 응답에 실린다 (저장 시에는 토큰 쪽 값을 쓴다)
                .andExpect(jsonPath("$.result.ocrResult.businessType").value("서비스업"))
                .andExpect(jsonPath("$.result.ocrResult.businessItem").value("광고대행"));
    }

    /**
     * <b>파일을 첨부하지 않으면 400이다.</b> 예전엔 500이 나갔다 -
     * @RequestParam이 required=true라 Spring이 컨트롤러 진입 전에 예외를 던졌고,
     * 서비스의 "파일 없음" 검사가 실행조차 되지 않았다.
     */
    @Test
    void upload_withoutFilePart_returns400NotServerError() throws Exception {
        mockMvc.perform(multipart(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_400_004"));
    }

    @Test
    void upload_withEmptyFile_returns400() throws Exception {
        mockMvc.perform(multipart(PATH)
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_400_004"));
    }

    /** 확장자만 .png로 바꾼 실행 파일. 매직바이트가 실제 내용을 말해준다. */
    @Test
    void upload_withExecutableRenamedToPng_returns400() throws Exception {
        byte[] windowsExecutable = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};

        mockMvc.perform(multipart(PATH)
                        .file(new MockMultipartFile("file", "reg.png", "image/png", windowsExecutable))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_400_002"));
    }

    /** OCR은 호출당 과금이다. 인증만으로 무제한 호출할 수 없다. */
    @Test
    void upload_beyondTheDailyLimit_returns429() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(multipart(PATH)
                            .file(new MockMultipartFile("file", "reg.png", "image/png", png()))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(multipart(PATH)
                        .file(new MockMultipartFile("file", "reg.png", "image/png", png()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BUSINESS_429_001"));
    }

    @Test
    void upload_withoutAccessToken_isRejected() throws Exception {
        mockMvc.perform(multipart(PATH)
                        .file(new MockMultipartFile("file", "reg.png", "image/png", png())))
                .andExpect(status().isUnauthorized());
    }

    /** 실제로 디코딩되는 PNG. 서버가 저장 전에 이미지를 열어보므로 헤더만 흉내 낸 배열은 400이 된다. */
    private static byte[] png() {
        return DocumentFixtures.png();
    }
}
