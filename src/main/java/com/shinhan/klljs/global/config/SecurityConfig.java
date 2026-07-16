package com.shinhan.klljs.global.config;

import com.shinhan.klljs.domain.auth.filter.OAuth2RedirectOriginFilter;
import com.shinhan.klljs.domain.auth.handler.OAuth2LoginFailureHandler;
import com.shinhan.klljs.domain.auth.handler.OAuth2LoginSuccessHandler;
import com.shinhan.klljs.domain.auth.service.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity // Spring Security 설정을 활성화
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler successHandler;
    private final OAuth2LoginFailureHandler failureHandler;
    private final CorsConfigurationSource corsConfigurationSource;
    private final OAuth2RedirectOriginFilter oauth2RedirectOriginFilter;

    public SecurityConfig(
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2LoginSuccessHandler successHandler,
            OAuth2LoginFailureHandler failureHandler,
            CorsConfigurationSource corsConfigurationSource,
            OAuth2RedirectOriginFilter oauth2RedirectOriginFilter
    ) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.corsConfigurationSource = corsConfigurationSource;
        this.oauth2RedirectOriginFilter = oauth2RedirectOriginFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. REST API 환경이므로 기본 제공되는 UI성 설정들을 비활성화합니다.
                .formLogin(AbstractHttpConfigurer::disable) // 기본 로그인 폼 화면 비활성화
                .httpBasic(AbstractHttpConfigurer::disable) // HTTP Basic 인증 비활성화

                // 인증이 전부 Bearer JWT(Authorization 헤더) 기반이라 브라우저가 자동으로 붙이는
                // 쿠키 기반 ambient credential이 없다 - CSRF가 막으려는 위협 자체가 성립하지 않는다.
                // Refresh Token 쿠키(SameSite=Lax)는 TrustedOriginValidator가 Origin을 직접 검증한다.
                .csrf(AbstractHttpConfigurer::disable)

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
                                // MVP 운영자가 Swagger/내부 도구에서 직접 등록하는 공개 관리 API.
                                "/api/v1/admin/media-units",
                                // 서울시 생활인구 격자 데이터 수동 적재 API. 최종적으로는 스케줄러가 대신
                                // 호출할 내부 배치용이라 media-units 등록 API와 동일하게 인증 없이 허용한다.
                                "/api/v1/admin/traffic/population-ingest",
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
                )

                // 로그인 시작 요청이 어느 프론트에서 왔는지, Kakao로 리다이렉트되기 전에 세션에 남긴다
                // (issue #41 - 로컬/배포 프론트 동시 지원). 반드시 그 리다이렉트를 실행하는
                // OAuth2AuthorizationRequestRedirectFilter보다 먼저 실행돼야 한다.
                .addFilterBefore(oauth2RedirectOriginFilter, OAuth2AuthorizationRequestRedirectFilter.class);

        return http.build();
    }
}
