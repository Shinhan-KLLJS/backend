package com.shinhan.klljs.global.config;

import com.shinhan.klljs.domain.auth.handler.OAuth2LoginFailureHandler;
import com.shinhan.klljs.domain.auth.handler.OAuth2LoginSuccessHandler;
import com.shinhan.klljs.domain.auth.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity // Spring Security 설정을 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler successHandler;
    private final OAuth2LoginFailureHandler failureHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. REST API 환경이므로 기본 제공되는 UI성 설정들을 비활성화합니다.
                .formLogin(AbstractHttpConfigurer::disable) // 기본 로그인 폼 화면 비활성화
                .httpBasic(AbstractHttpConfigurer::disable) // HTTP Basic 인증 비활성화

                // MVP 선택지 A: Refresh Token 쿠키를 SameSite=Lax로 두고
                // Refresh/Logout은 TrustedOriginValidator로 Origin을 직접 검증하므로,
                // 이 auth endpoint들은 CSRF 예외로 둔다.
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/oauth2/authorization/kakao",
                                "/login/oauth2/code/kakao",
                                "/api/v1/auth/token/refresh",
                                "/api/v1/auth/logout"
                        )
                )

                .cors(cors -> cors.configurationSource(corsConfigurationSource))

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

                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                )

                .oauth2ResourceServer(resourceServer ->
                        resourceServer.jwt(Customizer.withDefaults())
                );

        return http.build();
    }
}
