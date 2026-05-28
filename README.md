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
| Utilities | Lombok |

## 프로젝트 구조

```
src/main/java/com/interstellar/shorturl/
├── ShortUrlApplication.java
├── api/
│   └── UrlController.java          # REST 엔드포인트
├── application/
│   ├── UrlService.java             # 비즈니스 로직
│   └── UrlNotFoundException.java   # 404 예외
├── domain/
│   ├── Url.java                    # JPA 엔티티
│   └── UrlRepository.java          # DB 접근
├── infrastructure/
│   ├── CodeGenerator.java          # 단축 코드 생성
│   └── UrlCacheRepository.java     # Redis 캐시
└── config/
    └── RedisConfig.java            # Redis 설정
```

## API 명세

### POST /api/shorten — URL 단축

**Request**
```json
{
  "originalUrl": "https://www.example.com/very/long/url/path"
}
```

**Response** `201 Created`
```json
{
  "shortUrl": "http://localhost:8080/aB3xYz",
  "shortCode": "aB3xYz"
}
```

---

### GET /{code} — 원본 URL로 리다이렉트

**Response**
- `302 Found` — `Location` 헤더에 원본 URL
- `404 Not Found` — 존재하지 않는 코드

```json
{
  "message": "존재하지 않는 단축 코드입니다: aB3xYz"
}
```

## 동작 흐름

### 단축 URL 생성
```
POST /api/shorten
  → SecureRandom + Base62로 6자리 코드 생성
  → PostgreSQL 저장 (saveAndFlush)
    └─ shortCode 유니크 제약 위반 시 재시도 (최대 3회)
  → Redis에 선제 캐싱 (TTL 24h)
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

## 코드 생성 방식

`SecureRandom`으로 Base62 문자셋(`a-z A-Z 0-9`, 총 62자)에서 6자리를 무작위 선택합니다.

- 경우의 수: 62⁶ = **56,800,235,584** (약 568억)
- 충돌 발생 시 최대 3회 재시도

## 동시성 처리

### 단축 코드 충돌 (TOCTOU Race Condition)

DB 조회 후 저장하는 Check-Then-Act 방식은 동시 요청 환경에서 두 스레드가 동시에 같은 코드를 사용 가능하다고 판단한 뒤 저장을 시도하는 race condition이 발생합니다.

**해결 방법**: `existsByShortCode` 사전 체크를 제거하고, `saveAndFlush`로 즉시 DB에 반영한 뒤 유니크 제약 위반(`DataIntegrityViolationException`)을 catch하여 재시도합니다. DB 유니크 제약이 실질적인 충돌 방어선이 됩니다.

```java
for (int i = 0; i < MAX_RETRY; i++) {
    try {
        String shortCode = codeGenerator.generate();
        urlRepository.saveAndFlush(Url.create(originalUrl, shortCode));
        urlCacheRepository.save(shortCode, originalUrl);
        return shortCode;
    } catch (DataIntegrityViolationException e) {
        // shortCode 충돌 시 재시도
    }
}
```

### Redis-DB 정합성

`saveAndFlush`로 DB 커밋을 먼저 확정한 뒤 Redis에 씁니다. Redis 쓰기 실패 시에도 조회 시 DB에서 가져와 재캐싱하므로 데이터 손실은 없습니다.

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

`src/main/resources/application.properties`에서 DB 접속 정보를 수정합니다.

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

### 테스트

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
