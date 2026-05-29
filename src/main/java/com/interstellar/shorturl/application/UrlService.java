package com.interstellar.shorturl.application;

import com.interstellar.shorturl.domain.Url;
import com.interstellar.shorturl.domain.UrlNotFoundException;
import com.interstellar.shorturl.domain.UrlRepository;
import com.interstellar.shorturl.infrastructure.CodeGenerator;
import com.interstellar.shorturl.infrastructure.UrlCacheRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UrlService {

    private static final int MAX_RETRY = 3;

    private final UrlRepository urlRepository;
    private final UrlCacheRepository urlCacheRepository;
    private final CodeGenerator codeGenerator;

    // @Transactional 없음: UrlRepositoryImpl.save()가 saveAndFlush()로 즉시 커밋하므로
    // 캐시 쓰기는 DB 커밋 이후에 자연스럽게 실행된다. 재시도 시에도 각 save가 독립된 트랜잭션으로 동작한다.
    public String shorten(String originalUrl) {
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                String shortCode = codeGenerator.generate();
                urlRepository.save(Url.create(originalUrl, shortCode));
                urlCacheRepository.save(shortCode, originalUrl);
                return shortCode;
            } catch (DataIntegrityViolationException e) {
                if (!isShortCodeConflict(e)) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("단축 코드 생성에 실패했습니다. 재시도 횟수를 초과했습니다.");
    }

    private boolean isShortCodeConflict(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve) {
            String constraintName = cve.getConstraintName();
            return constraintName != null && constraintName.contains("short_code");
        }
        return false;
    }

    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortCode) {
        return urlCacheRepository.get(shortCode)
                .orElseGet(() -> fetchFromDbAndCache(shortCode));
    }

    private String fetchFromDbAndCache(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        urlCacheRepository.save(shortCode, url.getOriginalUrl());
        return url.getOriginalUrl();
    }
}
