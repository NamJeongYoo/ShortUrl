# ShortUrl

긴 URL을 6자리 단축 코드로 변환하고, 단축 코드로 원본 URL에 리다이렉트하는 URL 단축 서비스입니다.

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Database | PostgreSQL |
| Cache | Redis |
| ORM | Spring Data JPA (Hibernate) |
| Utilities | Lombok, Bean Validation |

## 프로젝트 구조

```
src/main/java/com/interstellar/shorturl/
├── ShortUrlApplication.java
├── api/
│   ├── UrlController.java          # REST 엔드포인트, 입력 검증
│   └── GlobalExceptionHandler.java # 전역 예외 처리
├── application/
│   └── UrlService.java             # 비즈니스 로직
├── domain/
│   ├── Url.java                    # 순수 도메인 객체 (JPA 의존 없음)
│   ├── UrlRepository.java          # 도메인 Repository 인터페이스
│   └── UrlNotFoundException.java   # 도메인 예외
├── infrastructure/
│   ├── CodeGenerator.java          # 단축 코드 생성
│   ├── UrlCacheRepository.java     # Redis 캐시
│   └── persistence/
│       ├── UrlJpaEntity.java       # JPA 엔티티
│       ├── UrlJpaRepository.java   # Spring Data JPA
│       └── UrlRepositoryImpl.java  # UrlRepository 구현체
├── support/
│   ├── exception/
│   │   └── BusinessException.java  # 도메인 예외 베이스
│   └── response/
│       └── ApiResponse.java        # 공통 응답 래퍼
└── config/
    └── RedisConfig.java            # Redis 설정
```

## API 명세

### 공통 응답 형식

모든 응답은 `ApiResponse<T>` 래퍼로 반환됩니다.

**성공**
```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "timestamp": "2025-05-29T10:00:00Z"
}
```

**실패**
```json
{
  "success": false,
  "data": null,
  "error": "에러 메시지",
  "timestamp": "2025-05-29T10:00:00Z"
}
```

---

### POST /api/shorten — URL 단축

**Request**
```json
{
  "originalUrl": "https://www.example.com/very/long/url/path"
}
```

| 필드 | 제약 |
|---|---|
| `originalUrl` | 필수, 유효한 URL 형식 |

**Response** `201 Created`
```json
{
  "success": true,
  "data": {
    "shortUrl": "http://localhost:8080/aB3xYz",
    "shortCode": "aB3xYz"
  },
  "error": null,
  "timestamp": "2025-05-29T10:00:00Z"
}
```

**Error Response**

| 상태 코드 | 상황 |
|---|---|
| `400 Bad Request` | `originalUrl`이 비어 있거나 URL 형식이 아닐 때 |
| `500 Internal Server Error` | 단축 코드 생성 재시도 횟수 초과 |

---

### GET /{code} — 원본 URL로 리다이렉트

**Response**
- `302 Found` — `Location` 헤더에 원본 URL
- `404 Not Found` — 존재하지 않는 코드

```json
{
  "success": false,
  "data": null,
  "error": "존재하지 않는 단축 코드입니다: aB3xYz",
  "timestamp": "2025-05-29T10:00:00Z"
}
```

## 아키텍처

### 레이어 의존 방향

```
api → application → domain ← infrastructure
                       ↑
                    support
```

`domain`은 Spring, JPA 등 프레임워크에 의존하지 않는 순수 Java 객체입니다. `UrlJpaEntity`가 JPA 관심사를 담당하고, `UrlRepositoryImpl`이 도메인 `UrlRepository` 인터페이스를 구현하는 어댑터 역할을 합니다.

## 동작 흐름

### 단축 URL 생성

```
POST /api/shorten
  → 입력 검증 (@NotBlank, @URL)
  → SecureRandom + Base62로 6자리 코드 생성
  → PostgreSQL 저장 + 즉시 커밋 (saveAndFlush)
    └─ shortCode 유니크 제약 위반 시 재시도 (최대 3회)
  → Redis에 캐싱 (TTL 24h)
  → 단축 URL 반환
```

### 리다이렉트

```
GET /{code}
  → Redis 조회 (캐시 히트 시 즉시 302)
  → 캐시 미스 시 PostgreSQL 조회
  → DB 히트 시 Redis 재캐싱 후 302
  → 없으면 404
```

## 설계 결정

### 동시성 — 단축 코드 충돌 방지

`existsByShortCode` 사전 체크 후 저장하는 Check-Then-Act 방식은 동시 요청 시 두 스레드가 동시에 같은 코드를 사용 가능하다고 판단하는 race condition이 발생합니다.

**해결:** 사전 체크 없이 바로 저장하고, DB 유니크 제약 위반(`DataIntegrityViolationException`)을 catch하여 재시도합니다. DB 유니크 제약이 충돌 방어선 역할을 합니다.

```java
for (int i = 0; i < MAX_RETRY; i++) {
    try {
        String shortCode = codeGenerator.generate();
        urlRepository.save(Url.create(originalUrl, shortCode)); // saveAndFlush → 즉시 커밋
        urlCacheRepository.save(shortCode, originalUrl);
        return shortCode;
    } catch (DataIntegrityViolationException e) {
        // 충돌 시 재시도
    }
}
```

### 캐시-DB 순서 보장

`UrlService.shorten()`에 `@Transactional`을 걸면 DB 커밋은 메서드 반환 시점에 일어나지만 Redis 쓰기는 그 전에 실행됩니다. DB 커밋 전에 캐시가 외부에 노출되면 정합성 문제가 발생합니다.

**해결:** `shorten()`에서 `@Transactional`을 제거합니다. `UrlRepositoryImpl.save()`가 내부적으로 `saveAndFlush()`로 즉시 커밋하므로, `save()` 반환 시점 = DB 커밋 시점입니다. 이후 Redis 쓰기가 실행되어 순서가 보장됩니다.

```
[이전] DB write → Redis write → 메서드 반환 → DB commit  ← 캐시가 커밋 전에 노출
[현재] DB write + commit → Redis write                    ← 순서 보장
```

재시도 루프도 이 방식에서 안전합니다. 첫 번째 save가 실패하면 해당 트랜잭션만 롤백되고, 다음 반복에서 새 트랜잭션으로 재시도합니다.

### 캐시 전략 (Cache-Aside + Write-Through)

- **읽기**: 캐시 미스 시 DB 조회 후 캐시에 저장 (Cache-Aside)
- **쓰기**: DB 저장 후 즉시 캐시에도 저장 (Write-Through)
- **TTL**: 24시간 — 단축 URL 특성상 하루 이상 미접근 시 재조회 비용이 낮음

### 코드 생성 방식

`SecureRandom`으로 Base62 문자셋(`a-z A-Z 0-9`, 총 62자)에서 6자리를 무작위 선택합니다.

- 경우의 수: 62⁶ = **56,800,235,584** (약 568억)
- 충돌 발생 시 최대 3회 재시도

## 실행 방법

### 사전 요구사항

- Java 21
- PostgreSQL (port 5432)
- Redis (port 6379)

### DB 생성

```bash
psql -U postgres -c "CREATE DATABASE shorturl;"
```

### 환경 설정

`src/main/resources/application.properties`에서 접속 정보를 수정합니다.

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/shorturl
spring.datasource.username=postgres
spring.datasource.password=postgres

spring.data.redis.host=localhost
spring.data.redis.port=6379

app.base-url=http://localhost:8080
```

### 앱 실행

```bash
./gradlew bootRun
```

### curl 예시

```bash
# URL 단축
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://www.example.com/very/long/url"}'

# 리다이렉트 (302 따라가기)
curl -L http://localhost:8080/{code}

# 리다이렉트 확인만 (302 헤더 확인)
curl -I http://localhost:8080/{code}
```

### 테스트 실행

```bash
./gradlew test
```
