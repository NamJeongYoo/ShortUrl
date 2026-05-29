package com.interstellar.shorturl.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGeneratorTest {

    private final CodeGenerator codeGenerator = new CodeGenerator();

    @Test
    void 생성된_코드는_6자리이다() {
        // when
        String code = codeGenerator.generate();

        // then
        assertThat(code).hasSize(6);
    }

    @Test
    void 생성된_코드는_BASE62_문자만_포함한다() {
        // when
        String code = codeGenerator.generate();

        // then
        assertThat(code).matches("[a-zA-Z0-9]+");
    }

    @Test
    void 반복_생성시_고유한_코드를_생성한다() {
        // given
        int count = 1000;
        Set<String> codes = new HashSet<>();

        // when
        for (int i = 0; i < count; i++) {
            codes.add(codeGenerator.generate());
        }

        // then — 62^6 ≈ 56억 조합 대비 1000개 충돌 확률은 무시할 수준
        assertThat(codes).hasSizeGreaterThan(990);
    }
}
