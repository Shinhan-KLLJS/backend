package com.shinhan.klljs.domain.team.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class InviteCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int CODE_LENGTH = 7;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        char[] code = new char[CODE_LENGTH];
        for (int i = 0; i < code.length; i++) {
            code[i] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}