package com.interstellar.shorturl.api;

import com.interstellar.shorturl.application.UrlService;
import com.interstellar.shorturl.domain.UrlNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UrlControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UrlService urlService;

    @BeforeEach
    void setUp() {
        UrlController controller = new UrlController(urlService);
        ReflectionTestUtils.setField(controller, "baseUrl", "http://localhost:8080");

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void URL_단축_성공시_201과_ApiResponse_반환() throws Exception {
        // given
        given(urlService.shorten("https://example.com")).willReturn("abc123");

        // when & then
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shortCode").value("abc123"))
                .andExpect(jsonPath("$.data.shortUrl").value("http://localhost:8080/abc123"))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void 유효한_코드로_리다이렉트시_302_반환() throws Exception {
        // given
        given(urlService.getOriginalUrl("abc123")).willReturn("https://example.com");

        // when & then
        mockMvc.perform(get("/abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void 존재하지_않는_코드로_리다이렉트시_404와_ApiResponse_반환() throws Exception {
        // given
        given(urlService.getOriginalUrl("notexist")).willThrow(new UrlNotFoundException("notexist"));

        // when & then
        mockMvc.perform(get("/notexist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void 빈_URL로_요청시_400과_ApiResponse_반환() throws Exception {
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("URL을 입력해주세요."));
    }

    @Test
    void URL_형식이_아닌_값으로_요청시_400과_ApiResponse_반환() throws Exception {
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("올바른 URL 형식이 아닙니다."));
    }

    @Test
    void 최대_재시도_초과시_500과_ApiResponse_반환() throws Exception {
        // given
        given(urlService.shorten("https://example.com"))
                .willThrow(new IllegalStateException("단축 코드 생성에 실패했습니다. 재시도 횟수를 초과했습니다."));

        // when & then
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }
}
