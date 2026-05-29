package com.interstellar.shorturl.api;

import com.interstellar.shorturl.application.UrlService;
import com.interstellar.shorturl.support.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    @Value("${app.base-url}")
    private String baseUrl;

    @PostMapping("/api/shorten")
    public ResponseEntity<ApiResponse<ShortenResponse>> shorten(@Valid @RequestBody ShortenRequest request) {
        String shortCode = urlService.shorten(request.originalUrl());
        String shortUrl = baseUrl + "/" + shortCode;
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(new ShortenResponse(shortUrl, shortCode)));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String originalUrl = urlService.getOriginalUrl(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }

    record ShortenRequest(
            @NotBlank(message = "URL을 입력해주세요.")
            @URL(message = "올바른 URL 형식이 아닙니다.")
            String originalUrl
    ) {}
    record ShortenResponse(String shortUrl, String shortCode) {}
}
