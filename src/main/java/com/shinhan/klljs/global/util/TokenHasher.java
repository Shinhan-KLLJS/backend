package com.shinhan.klljs.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 초대 코드, Refresh Token 등 "원본은 저장하지 않고 해시만 저장"하는 값들에 공통으로 쓰는 해셔.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static byte[] sha256(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
