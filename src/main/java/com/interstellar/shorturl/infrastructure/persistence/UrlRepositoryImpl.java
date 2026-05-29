package com.interstellar.shorturl.infrastructure.persistence;

import com.interstellar.shorturl.domain.Url;
import com.interstellar.shorturl.domain.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UrlRepositoryImpl implements UrlRepository {

    private final UrlJpaRepository jpaRepository;

    @Override
    public Url save(Url url) {
        UrlJpaEntity saved = jpaRepository.saveAndFlush(UrlJpaEntity.from(url));
        return saved.toDomain();
    }

    @Override
    public Optional<Url> findByShortCode(String shortCode) {
        return jpaRepository.findByShortCode(shortCode)
                .map(UrlJpaEntity::toDomain);
    }
}
