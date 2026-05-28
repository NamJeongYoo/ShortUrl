package com.interstellar.shorturl.infrastructure;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class CodeGenerator {

    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        return sb.toString();
    }
}
