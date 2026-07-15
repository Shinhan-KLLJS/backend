package com.shinhan.klljs.global.security;

import java.net.URI;

/**
 * Referer는 전체 URL(경로 포함)이라 문자열 startsWith로 비교하면
 * "https://loovi.my.evil.com"처럼 허용 origin을 접두사로 갖는 도메인에 우회당한다.
 * scheme+host+port만 재조합해 Origin 헤더와 동일한 기준(정확히 일치)으로 비교한다.
 */
public final class RefererOriginParser {

    private RefererOriginParser() {
    }

    public static String parse(String referer) {
        if (referer == null) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            int port = uri.getPort();
            return port == -1 ? scheme + "://" + host : scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
