package com.interstellar.shorturl.application;

import com.interstellar.shorturl.domain.Url;
import com.interstellar.shorturl.domain.UrlNotFoundException;
import com.interstellar.shorturl.domain.UrlRepository;
import com.interstellar.shorturl.infrastructure.CodeGenerator;
import com.interstellar.shorturl.infrastructure.UrlCacheRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private UrlCacheRepository urlCacheRepository;

    @Mock
    private CodeGenerator codeGenerator;

    @InjectMocks
    private UrlService urlService;

    @Test
    void 단축_URL_생성_성공() {
        // given
        given(codeGenerator.generate()).willReturn("abc123");
        given(urlRepository.save(any())).willReturn(Url.create("https://example.com", "abc123"));

        // when
        String shortCode = urlService.shorten("https://example.com");

        // then
        assertThat(shortCode).isEqualTo("abc123");
        verify(urlCacheRepository).save("abc123", "https://example.com");
    }

    @Test
    void 단축코드_충돌시_재시도_후_성공() {
        // given
        given(codeGenerator.generate()).willReturn("abc123");
        given(urlRepository.save(any()))
                .willThrow(shortCodeConflict())
                .willReturn(Url.create("https://example.com", "abc123"));

        // when
        String shortCode = urlService.shorten("https://example.com");

        // then
        assertThat(shortCode).isEqualTo("abc123");
    }

    @Test
    void 최대_재시도_횟수_초과시_예외발생() {
        // given
        given(codeGenerator.generate()).willReturn("abc123");
        given(urlRepository.save(any())).willThrow(shortCodeConflict());

        // when & then
        assertThatThrownBy(() -> urlService.shorten("https://example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재시도 횟수를 초과");
    }

    @Test
    void shortCode_외_제약_위반은_재시도_없이_즉시_전파된다() {
        // given - shortCode가 아닌 다른 컬럼의 제약 위반 (constraint name에 short_code 없음)
        ConstraintViolationException cause = new ConstraintViolationException(
                "not null constraint", new SQLException(), "urls_original_url_not_null");
        DataIntegrityViolationException otherViolation =
                new DataIntegrityViolationException("constraint violation", cause);

        given(codeGenerator.generate()).willReturn("abc123");
        given(urlRepository.save(any())).willThrow(otherViolation);

        // when & then - 재시도 없이 즉시 DataIntegrityViolationException 전파
        assertThatThrownBy(() -> urlService.shorten("https://example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 캐시_히트시_DB_조회_없이_반환() {
        // given
        given(urlCacheRepository.get("abc123")).willReturn(Optional.of("https://example.com"));

        // when
        String result = urlService.getOriginalUrl("abc123");

        // then
        assertThat(result).isEqualTo("https://example.com");
        verify(urlRepository, never()).findByShortCode(any());
    }

    @Test
    void 캐시_미스시_DB_조회_후_캐시_저장() {
        // given
        given(urlCacheRepository.get("abc123")).willReturn(Optional.empty());
        given(urlRepository.findByShortCode("abc123"))
                .willReturn(Optional.of(Url.create("https://example.com", "abc123")));

        // when
        String result = urlService.getOriginalUrl("abc123");

        // then
        assertThat(result).isEqualTo("https://example.com");
        verify(urlCacheRepository).save("abc123", "https://example.com");
    }

    @Test
    void 존재하지_않는_단축코드_조회시_예외발생() {
        // given
        given(urlCacheRepository.get("notexist")).willReturn(Optional.empty());
        given(urlRepository.findByShortCode("notexist")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> urlService.getOriginalUrl("notexist"))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessageContaining("notexist");
    }

    private static DataIntegrityViolationException shortCodeConflict() {
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate key value violates unique constraint \"idx_short_code\"",
                new SQLException(),
                "idx_short_code"
        );
        return new DataIntegrityViolationException("constraint violation", cause);
    }
}
