package com.interstellar.shorturl.api;

import com.interstellar.shorturl.support.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UrlApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void URL_단축_생성_성공시_201과_shortCode_반환() throws Exception {
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.data.shortUrl").isNotEmpty())
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void 단축_URL로_리다이렉트시_302와_원본_URL_반환() throws Exception {
        // given
        String shortCode = shorten("https://example.com");

        // when & then
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void URL_단축_후_Redis_캐시에_저장됨() throws Exception {
        // when
        String shortCode = shorten("https://example.com");

        // then
        String cached = redisTemplate.opsForValue().get("shorturl:" + shortCode);
        assertThat(cached).isEqualTo("https://example.com");
    }

    @Test
    void 캐시_삭제_후_재조회시_DB에서_반환되고_재캐싱됨() throws Exception {
        // given
        String shortCode = shorten("https://example.com");
        redisTemplate.delete("shorturl:" + shortCode);

        // when - DB fallback 조회
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com"));

        // then - 재캐싱 검증
        String recached = redisTemplate.opsForValue().get("shorturl:" + shortCode);
        assertThat(recached).isEqualTo("https://example.com");
    }

    @Test
    void 동일한_URL을_두_번_단축하면_서로_다른_코드_생성() throws Exception {
        // when
        String shortCode1 = shorten("https://example.com");
        String shortCode2 = shorten("https://example.com");

        // then
        assertThat(shortCode1).isNotEqualTo(shortCode2);
    }

    @Test
    void 존재하지_않는_코드로_리다이렉트시_404_반환() throws Exception {
        mockMvc.perform(get("/notexist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void 잘못된_URL_형식으로_요청시_400_반환() throws Exception {
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("올바른 URL 형식이 아닙니다."));
    }

    private String shorten(String originalUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"" + originalUrl + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.shortCode");
    }
}
