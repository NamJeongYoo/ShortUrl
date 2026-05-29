package com.interstellar.shorturl.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface UrlJpaRepository extends JpaRepository<UrlJpaEntity, Long> {

    Optional<UrlJpaEntity> findByShortCode(String shortCode);
}
