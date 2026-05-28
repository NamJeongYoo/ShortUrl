package com.interstellar.shorturl.api;

import com.interstellar.shorturl.application.UrlNotFoundException;
import com.interstellar.shorturl.application.UrlService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest request) {
        String shortCode = urlService.shorten(request.originalUrl());
        String shortUrl = baseUrl + "/" + shortCode;
        return ResponseEntity.status(HttpStatus.CREATED).body(new ShortenResponse(shortUrl, shortCode));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String originalUrl = urlService.getOriginalUrl(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }

    @ExceptionHandler(UrlNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(UrlNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
    }

    record ShortenRequest(String originalUrl) {}
    record ShortenResponse(String shortUrl, String shortCode) {}
    record ErrorResponse(String message) {}
}
