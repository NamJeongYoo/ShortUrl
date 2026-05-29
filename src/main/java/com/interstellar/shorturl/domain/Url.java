package com.interstellar.shorturl.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Url {

    private String originalUrl;
    private String shortCode;
    private LocalDateTime createdAt;

    private Url() {}

    public static Url create(String originalUrl, String shortCode) {
        Url url = new Url();
        url.originalUrl = originalUrl;
        url.shortCode = shortCode;
        url.createdAt = LocalDateTime.now();
        return url;
    }

    public static Url of(String originalUrl, String shortCode, LocalDateTime createdAt) {
        Url url = new Url();
        url.originalUrl = originalUrl;
        url.shortCode = shortCode;
        url.createdAt = createdAt;
        return url;
    }
}
