package com.shinhan.klljs.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class JpaConfig {

    /**
     * JVM 기본 타임존 설정(KlljsApplication)에 의존하지 않고, created_at/updated_at이
     * 항상 UTC로 찍히도록 명시적으로 고정한다.
     */
    @Bean
    public DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(ZoneOffset.UTC));
    }
}
