package com.shinhan.klljs.domain.traffic.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 서울 열린데이터광장의 "서울특별시 250M격자 생활인구(내국인)"(OA-22784) 일별 원본 ZIP을 내려받는다.
 *
 * <p>OpenAPI({@code Se250MSpopLocalResd})는 매일 갱신되는 "오늘-4일" 데이터 한 건만 제공하고
 * 날짜를 지정한 과거 조회를 지원하지 않는다(직접 호출로 확인함) - 6일 지연 매핑({@link
 * com.shinhan.klljs.domain.traffic.util.PopulationDataLag})을 구현하려면 날짜별로 계속 남아있는
 * 파일내려받기(ZIP) 쪽을 써야 한다. 이 다운로드 엔드포인트는 로그인 세션 없이도 동작한다
 * (쿠키 없이 직접 호출해 확인함).</p>
 */
@Slf4j
@Component
public class SeoulPopulationFileClient {

    private static final String DOWNLOAD_URL = "https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do?&useCache=false";
    private static final String INF_ID = "OA-22784";
    // 파일 다운로드 요청의 seq 파라미터는 연도 앞 두 자리를 뺀 6자리(yyMMdd)다 (예: 2026-07-11 -> "260711").
    private static final DateTimeFormatter SEQ_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final Charset CSV_CHARSET = Charset.forName("CP949");

    private static final int GRID_CODE_COLUMN_INDEX = 3; // 250m격자
    private static final int POPULATION_COLUMN_INDEX = 4; // 생활인구합계
    private static final int MIN_EXPECTED_COLUMNS = 5;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(timeoutRequestFactory())
            .build();

    /**
     * 지정한 날짜(서울시 데이터상의 실제 일자)의 250m 격자별 하루 생활인구 합계를 반환한다.
     * 원본은 격자×시간(0~23시)별 행이라, 같은 격자코드의 24개 행을 합산해서 돌려준다.
     */
    public Map<String, Long> fetchDailyGridPopulation(LocalDate date) {
        byte[] zipBytes = downloadZip(date);
        return parseCsvFromZip(zipBytes);
    }

    private byte[] downloadZip(LocalDate date) {
        String seq = date.format(SEQ_DATE_FORMAT);
        String body = "infId=" + INF_ID + "&seqNo=&seq=" + seq + "&infSeq=1";

        return restClient.post()
                .uri(DOWNLOAD_URL)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header(HttpHeaders.ORIGIN, "https://data.seoul.go.kr")
                .header(HttpHeaders.REFERER, "https://data.seoul.go.kr/")
                .body(body)
                .retrieve()
                .body(byte[].class);
    }

    private Map<String, Long> parseCsvFromZip(byte[] zipBytes) {
        Map<String, Long> totalByGridCode = new HashMap<>();

        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zipIn.getNextEntry();
            if (entry == null) {
                throw new IllegalStateException("생활인구 ZIP 파일 안에 항목이 없습니다.");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(zipIn, CSV_CHARSET));
            String line = reader.readLine(); // 헤더 행("일자","시간",...) 스킵
            int rowCount = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = splitQuotedCsvLine(line);
                if (columns.length < MIN_EXPECTED_COLUMNS) {
                    continue;
                }
                String gridCode = columns[GRID_CODE_COLUMN_INDEX];
                long population = parsePopulation(columns[POPULATION_COLUMN_INDEX]);
                totalByGridCode.merge(gridCode, population, Long::sum);
                rowCount++;
            }
            log.info("생활인구 CSV 파싱 완료: rowCount={}, gridCount={}", rowCount, totalByGridCode.size());
        } catch (IOException e) {
            throw new IllegalStateException("생활인구 ZIP 파일을 읽는 데 실패했습니다.", e);
        }

        return totalByGridCode;
    }

    /** 모든 필드가 큰따옴표로 감싸인 단순 CSV라 임베디드 쉼표/따옴표를 고려하지 않는다 (실제 데이터로 확인함). */
    private String[] splitQuotedCsvLine(String line) {
        String[] rawColumns = line.split(",", -1);
        String[] columns = new String[rawColumns.length];
        for (int i = 0; i < rawColumns.length; i++) {
            String value = rawColumns[i].trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            columns[i] = value;
        }
        return columns;
    }

    /** "*"는 3명 이하 비식별화 마스킹이라 정확한 값을 알 수 없다 - 0으로 취급한다(과소추정을 감수). */
    private long parsePopulation(String rawValue) {
        if (rawValue.isBlank() || rawValue.equals("*")) {
            return 0L;
        }
        return Math.round(Double.parseDouble(rawValue));
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        return factory;
    }
}
