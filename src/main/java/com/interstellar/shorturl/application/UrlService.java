package com.interstellar.shorturl.application;

import com.interstellar.shorturl.domain.Url;
import com.interstellar.shorturl.domain.UrlRepository;
import com.interstellar.shorturl.infrastructure.CodeGenerator;
import com.interstellar.shorturl.infrastructure.UrlCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UrlService {

    private static final int MAX_RETRY = 3;

    private final UrlRepository urlRepository;
    private final UrlCacheRepository urlCacheRepository;
    private final CodeGenerator codeGenerator;

    @Transactional
    public String shorten(String originalUrl) {
        String shortCode = generateUniqueCode();
        Url url = Url.create(originalUrl, shortCode);
        urlRepository.save(url);
        urlCacheRepository.save(shortCode, originalUrl);
        return shortCode;
    }

    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortCode) {
        return urlCacheRepository.get(shortCode)
                .orElseGet(() -> fetchFromDbAndCache(shortCode));
    }

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_RETRY; i++) {
            String code = codeGenerator.generate();
            if (!urlRepository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("단축 코드 생성에 실패했습니다. 재시도 횟수를 초과했습니다.");
    }

    private String fetchFromDbAndCache(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        urlCacheRepository.save(shortCode, url.getOriginalUrl());
        return url.getOriginalUrl();
    }
}
