package com.interstellar.shorturl.application;

public class UrlNotFoundException extends RuntimeException {

    public UrlNotFoundException(String shortCode) {
        super("존재하지 않는 단축 코드입니다: " + shortCode);
    }
}
