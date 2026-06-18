package com.shinhan.klljs.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity // Spring Security 설정을 활성화
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. REST API 환경이므로 기본 제공되는 UI성 설정들을 비활성화합니다.
                .csrf(AbstractHttpConfigurer::disable) // CSRF 보호 비활성화 (기본적으로 세션 기반이 아닌 JWT/토큰 기반일 때 필수)
                .formLogin(AbstractHttpConfigurer::disable) // 기본 로그인 폼 화면 비활성화
                .httpBasic(AbstractHttpConfigurer::disable) // HTTP Basic 인증 비활성화

                // 2. 세션을 사용하지 않도록 설정 (Stateless 설정)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 3. URL별 권한 설정 (인가/Authorization)
                .authorizeHttpRequests(auth -> auth
                        // 앞서 설정한 Swagger 관련 URL은 로그인 없이 누구나 접근 가능하도록 허용
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health"
                        ).permitAll()

                        // 회원가입, 로그인 같은 인증 관련 API도 접근 허용 (예시 경로)
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()

                        // 그 외 모든 요청은 인증(로그인)을 거쳐야만 접근 가능
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    // 4. 비밀번호 암호화를 위한 Encoder 빈 등록 (BCrypt 해시 알고리즘 사용)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}