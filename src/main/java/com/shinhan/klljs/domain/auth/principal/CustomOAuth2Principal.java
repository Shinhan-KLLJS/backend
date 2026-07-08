package com.shinhan.klljs.domain.auth.principal;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 로그인 성공 후 SecurityContext에 담기는 Principal.
 * JPA User 엔티티를 직접 넣지 않고, 인증에 필요한 최소 값만 불변 객체로 보관한다.
 * 팀 OWNER/ADMIN/MEMBER 권한은 여기 담기지 않으며, 매 요청마다 team_members로 별도 확인한다.
 */
public final class CustomOAuth2Principal implements OAuth2User {

    private final Long userId;
    private final String providerUserId;
    private final Map<String, Object> attributes;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomOAuth2Principal(
            Long userId,
            String providerUserId,
            Map<String, Object> attributes
    ) {
        this.userId = userId;
        this.providerUserId = providerUserId;
        this.attributes = Map.copyOf(attributes);
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return providerUserId;
    }
}
