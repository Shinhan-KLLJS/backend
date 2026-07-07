package com.shinhan.klljs.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
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

// TODO: 카카오 로그인 추가 후 주석 해제
//                .csrf(csrf -> csrf
//                        .ignoringRequestMatchers(
//                                "/oauth2/authorization/kakao",
//                                "/login/oauth2/code/kakao",
//                                "/api/v1/auth/token/refresh",
//                                "/api/v1/auth/logout"
//                        )
//                )

                // 2. oauth2Login()은 카카오로 갔다가 돌아오는 왕복 요청 사이에 state 값과 원래 요청 정보를 어딘가에 보관해야함
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                .headers(headers ->
                        headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )

                // 3. URL별 권한 설정 (인가/Authorization)
                .authorizeHttpRequests(auth -> auth
                        // 앞서 설정한 Swagger 관련 URL은 로그인 없이 누구나 접근 가능하도록 허용
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/h2-console/**",
                                "/api/v1/auth/**", // 회원가입, 로그인 같은 인증 관련 API도 접근 허용
                                "/oauth2/authorization/kakao",
                                "/login/oauth2/code/kakao"
                        ).permitAll()
                        // 그 외 모든 요청은 인증(로그인)을 거쳐야만 접근 가능
                        .anyRequest().authenticated()
                )
                    // TODO: customOAuth2UserService, successHandler, failureHandler 구현
//                .oauth2Login(oauth2 -> oauth2
//                        .userInfoEndpoint(userInfo -> userInfo
//                                .userService(customOAuth2UserService)
//                        )
//                        .successHandler(successHandler)
//                        .failureHandler(failureHandler)
//                )

        // TODO: JwtDecoder 빈을 먼저 등록한 뒤에 이 줄을 켜야 함
//                    .oauth2ResourceServer(resourceServer ->
//                            resourceServer.jwt(
//                                    Customizer.withDefaults()
//                            )
//                    );
        ;

        return http.build();
    }

//    // 4. 비밀번호 암호화를 위한 Encoder 빈 등록 (BCrypt 해시 알고리즘 사용)
//    @Bean
//    public PasswordEncoder passwordEncoder() {
//        return new BCryptPasswordEncoder();
//    }
}