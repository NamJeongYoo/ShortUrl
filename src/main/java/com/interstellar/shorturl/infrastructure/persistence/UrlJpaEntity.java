package com.interstellar.shorturl.infrastructure.persistence;

import com.interstellar.shorturl.domain.Url;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "urls", indexes = @Index(name = "idx_short_code", columnList = "short_code", unique = true))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UrlJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false, length = 6)
    private String shortCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static UrlJpaEntity from(Url url) {
        UrlJpaEntity entity = new UrlJpaEntity();
        entity.originalUrl = url.getOriginalUrl();
        entity.shortCode = url.getShortCode();
        entity.createdAt = url.getCreatedAt();
        return entity;
    }

    public Url toDomain() {
        return Url.of(originalUrl, shortCode, createdAt);
    }
}
