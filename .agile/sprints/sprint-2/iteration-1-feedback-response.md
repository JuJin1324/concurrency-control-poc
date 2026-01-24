# Iteration 1 피드백 답변

**작성일:** 2026-01-24

---

## Q1. spring-boot-starter-data-redis vs Redisson

### spring-boot-starter-data-redis
- 기본 Redis 연산 제공 (RedisTemplate, StringRedisTemplate)
- GET, SET, INCR 등 단순 명령어 실행
- **분산 락 기능 없음**

### Redisson
- 고수준 추상화 제공
- **RLock (분산 락)** - 이번에 사용한 기능
- 분산 자료구조 (Map, Queue, Set 등)
- Pub/Sub 기반 효율적인 락 대기

### 요약
```
spring-data-redis: "Redis 명령어를 실행하는 클라이언트"
Redisson: "Redis 위에 구축된 분산 시스템 프레임워크"
```

---

## Q2. 분산 락(Distributed Lock)이란?

**분산 DB가 아니라, 분산된 애플리케이션 인스턴스** 간의 동기화입니다.

### 시나리오 비교

**단일 인스턴스:**
```
App Instance → MySQL (DB Lock으로 충분)
```

**다중 인스턴스 (분산 환경):**
```
App Instance 1 ─┐
App Instance 2 ─┼→ MySQL
App Instance 3 ─┘

문제: DB 연결 전에 이미 여러 인스턴스가 동시에 접근
해결: Redis를 중앙 조정자로 사용
```

### 분산 락의 핵심
- 여러 서버(인스턴스)가 **하나의 Redis**를 바라봄
- Redis가 "누가 락을 가지고 있는지" 중앙에서 관리
- DB에 접근하기 **전에** 락 획득

---

## Q3. TransactionTemplate 대신 더 깔끔한 방법

### 현재 방식의 이유

```java
// @Transactional은 프록시 기반 → 메서드 시작 시 트랜잭션 시작
// Lock 획득 전에 트랜잭션이 시작되어버림

@Transactional  // ← 여기서 이미 트랜잭션 시작
public void decreaseStock(...) {
    lock.tryLock();  // ← 너무 늦음!
}
```

### 대안 1: 커스텀 어노테이션 + AOP

```java
@RedisLock(key = "'stock:' + #stockId")
@Transactional
public void decreaseStock(Long stockId, int amount) {
    // 락과 트랜잭션 순서가 AOP에서 관리됨
}
```

### 대안 2: Facade 패턴으로 분리

```java
// LockFacade - 락 관리만 담당
public void decreaseWithLock(Long stockId, int amount) {
    RLock lock = redissonClient.getLock(key);
    try {
        lock.lock();
        stockService.decreaseStock(stockId, amount);  // 여기서 @Transactional
    } finally {
        lock.unlock();
    }
}
```

### 현재 선택 이유

> Iteration 2 이후에 리팩토링으로 AOP 방식을 적용할 수 있습니다.
> 지금은 명시적인 TransactionTemplate이 동작 원리를 이해하기 좋습니다.

---

## Q4. LOCK_PREFIX 네이밍 컨벤션

### Redis 키 네이밍 컨벤션 (일반적)

```
{resource-type}:{identifier}:{sub-identifier}
```

### 분산 락 관련 일반적인 패턴

```
lock:stock:123              ← 현재 사용 중
distributed-lock:stock:123
stock:lock:123
locks:inventory:123
```

### 권장 사항
- 콜론(`:`)으로 구분 (Redis 표준 관행)
- 소문자 사용
- 의미 있는 prefix (`lock:`, `cache:`, `session:` 등)

**결론:** 현재 `lock:stock:{id}` 패턴은 **적절한 컨벤션**입니다.

---

## Q5. Pessimistic Lock이 Redis Lock보다 빠른 이유

### 테스트 결과

| 방식 | 시간 |
|------|------|
| Pessimistic Lock | 176ms |
| Redis Lock | 353ms |
| Optimistic Lock | 7,092ms |

### 원인 분석

#### 1. 네트워크 홉(Hop) 차이

```
Pessimistic: App → MySQL (1 hop, 커넥션 풀 재사용)
Redis Lock:  App → Redis → App → MySQL (2 hops)
```

#### 2. Lock 획득 오버헤드

```java
// Redis Lock은 매 요청마다:
lock.tryLock(5, 3, TimeUnit.SECONDS);  // Redis 네트워크 왕복
// ... 작업 ...
lock.unlock();  // Redis 네트워크 왕복
```

#### 3. 테스트 환경 특성

- 로컬 환경: MySQL과 Redis 모두 같은 머신
- **단일 인스턴스** 테스트: 분산 락의 이점이 발휘되지 않음
- 커넥션 풀: MySQL은 HikariCP로 최적화, Redis는 매번 연결

#### 4. Pessimistic Lock의 장점이 발휘되는 환경

```
로컬 테스트: MySQL 커넥션 풀 + 같은 머신 = 매우 빠름
```

### 환경별 비교

| 환경 | 더 빠른 방식 |
|------|--------------|
| 단일 인스턴스 + 로컬 DB | Pessimistic Lock |
| **다중 인스턴스 + 분산 환경** | **Redis Lock** |
| 초고성능 필요 | Lua Script (Iteration 2) |

### 결론

> **실무에서 Redis Lock을 선택하는 이유**는 성능이 아니라 **확장성**입니다.
> 서버를 3대, 10대로 늘려도 동작합니다.

---

## 다음 단계

Iteration 2: Redis Lua Script 구현으로 진행 예정
