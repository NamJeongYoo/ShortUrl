package com.interstellar.shorturl.domain;

import com.interstellar.shorturl.support.exception.BusinessException;

public class UrlNotFoundException extends BusinessException {

    public UrlNotFoundException(String shortCode) {
        super("존재하지 않는 단축 코드입니다: " + shortCode);
    }
}
