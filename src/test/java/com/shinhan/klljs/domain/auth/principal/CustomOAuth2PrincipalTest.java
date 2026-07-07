package com.shinhan.klljs.domain.auth.principal;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomOAuth2PrincipalTest {

    @Test
    void exposesUserIdAndNameAndSingleRoleUserAuthority() {
        Map<String, Object> attributes = Map.of("id", 123456789L);

        CustomOAuth2Principal principal =
                new CustomOAuth2Principal(1L, "123456789", attributes);

        assertThat(principal.getUserId()).isEqualTo(1L);
        assertThat(principal.getName()).isEqualTo("123456789");
        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        assertThat(principal.getAttributes()).isEqualTo(attributes);
    }

    @Test
    void getAttributesIsImmutable() {
        CustomOAuth2Principal principal =
                new CustomOAuth2Principal(1L, "123456789", Map.of("id", 123456789L));

        assertThatThrownBy(() -> principal.getAttributes().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mutatingOriginalMapAfterConstructionDoesNotAffectPrincipal() {
        Map<String, Object> original = new HashMap<>();
        original.put("id", 123456789L);

        CustomOAuth2Principal principal =
                new CustomOAuth2Principal(1L, "123456789", original);

        original.put("id", 999L);

        assertThat(principal.getAttributes()).containsEntry("id", 123456789L);
    }
}
