package com.shinhan.klljs.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 서비스 코드에서 LocalDateTime.now()를 직접 호출하지 않고 이 빈을 주입받아 사용한다.
 * 테스트에서 Clock.fixed(...)로 교체해 시간을 고정할 수 있고, UTC로 통일하는 프로젝트 원칙과도 일치한다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
