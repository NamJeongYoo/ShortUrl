package com.interstellar.shorturl.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "urls", indexes = @Index(name = "idx_short_code", columnList = "shortCode", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false, length = 6, unique = true)
    private String shortCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Url create(String originalUrl, String shortCode) {
        Url url = new Url();
        url.originalUrl = originalUrl;
        url.shortCode = shortCode;
        url.createdAt = LocalDateTime.now();
        return url;
    }
}
