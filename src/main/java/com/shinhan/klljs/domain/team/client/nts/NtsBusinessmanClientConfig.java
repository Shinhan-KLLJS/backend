package com.shinhan.klljs.domain.team.client.nts;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 국세청 API 전용 RestClient 빈.
 *
 * baseUrl을 걸지 않는 이유: serviceKey가 이미 URL 인코딩된 문자열일 수 있어서, 클라이언트가
 * 절대 URI를 직접 만들어 넘긴다. baseUrl + uriTemplate 조합을 쓰면 Spring이 쿼리 문자열을
 * 한 번 더 인코딩해 이중 인코딩으로 인증에 실패한다.
 *
 * 타임아웃을 반드시 건다. 외부 API가 응답하지 않을 때 톰캣 스레드가 무한정 잡히면
 * 검증과 무관한 대시보드 API까지 함께 느려진다.
 */
@Configuration
public class NtsBusinessmanClientConfig {

    @Bean
    public RestClient ntsRestClient(NtsBusinessmanProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
