package com.interstellar.shorturl.domain;

import java.util.Optional;

public interface UrlRepository {

    Url save(Url url);

    Optional<Url> findByShortCode(String shortCode);
}
