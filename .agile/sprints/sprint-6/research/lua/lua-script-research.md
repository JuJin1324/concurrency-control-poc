# Redis Lua Script 운영 사례 연구

**작성일:** 2026-02-03
**스프린트:** Sprint 6 (US-6.4)
**목적:** 실제 운영 환경에서 Redis Lua Script 사용 사례 조사 및 운영 노하우 수집

---

## Executive Summary

Redis Lua Script는 초고부하 선착순 이벤트에서 가장 높은 성능을 발휘하는 동시성 제어 방식입니다. Lock 획득 없이 원자적 연산으로 처리하므로 TPS가 가장 높지만, Redis와 DB 간 데이터 동기화 전략이 핵심입니다. 본 연구는 티켓팅, 쿠폰 발급 등 실전 사례와 운영 노하우를 정리합니다.

---

## 사례 1: 티켓링크/인터파크 - 콘서트 티켓팅 시스템

### 도메인
초고부하 선착순 티켓 예매 (아이돌 콘서트, 뮤지컬 등)

### 선택 이유
1. **초고속 처리**: 오픈 시 초당 수십만 건 요청 폭주
2. **Lock Contention 회피**: Redis Lock도 병목, Lua Script로 원자성 보장
3. **메모리 기반 속도**: DB I/O 없이 Redis 내에서 모든 처리 완료
4. **품절 후 빠른 응답**: 재고 소진 후에도 즉시 실패 응답 (DB 부하 없음)

### 구현 방식

#### 1. 재고 차감 Lua Script
```lua
-- ticket_decrease.lua
local stock_key = KEYS[1]          -- "ticket:concert:123"
local user_key = KEYS[2]           -- "ticket:user:456"
local decrease_count = tonumber(ARGV[1])  -- 1 (1장)
local user_id = ARGV[2]            -- "user:456"

-- 1. 현재 재고 확인
local current_stock = tonumber(redis.call('GET', stock_key) or "0")

-- 2. 재고 부족 시 실패
if current_stock < decrease_count then
    return {0, "SOLD_OUT", current_stock}
end

-- 3. 중복 구매 방지 (사용자당 1장 제한)
local already_purchased = redis.call('SISMEMBER', user_key, user_id)
if already_purchased == 1 then
    return {0, "DUPLICATE", current_stock}
end

-- 4. 원자적 재고 차감
local new_stock = redis.call('DECRBY', stock_key, decrease_count)

-- 5. 구매 기록 추가
redis.call('SADD', user_key, user_id)

-- 6. 구매 성공
return {1, "SUCCESS", new_stock}
```

#### 2. Script 버전 관리
```java
// ScriptLoader.java
@Component
public class LuaScriptLoader {
    private final RedisTemplate<String, String> redisTemplate;
    private final Map<String, String> scriptShaCache = new ConcurrentHashMap<>();

    public String loadScript(String scriptName) {
        String scriptPath = "scripts/" + scriptName + ".lua";
        String scriptContent = readFile(scriptPath);

        // SHA-1 해시로 스크립트 등록
        String sha = redisTemplate.execute(
            (RedisCallback<String>) connection ->
                connection.scriptLoad(scriptContent.getBytes())
        );

        scriptShaCache.put(scriptName, sha);
        return sha;
    }

    public Object executeScript(String scriptName, List<String> keys, Object... args) {
        String sha = scriptShaCache.get(scriptName);
        if (sha == null) {
            sha = loadScript(scriptName);
        }

        return redisTemplate.execute(
            (RedisCallback<Object>) connection ->
                connection.evalSha(sha, ReturnType.MULTI, keys.size(),
                    concat(keys, Arrays.asList(args)))
        );
    }
}
```

#### 3. Redis → DB 동기화 전략

**방법 1: 동기 동기화 (Synchronous)**
```java
// 티켓 구매 API
public TicketPurchaseResult purchaseTicket(Long concertId, Long userId) {
    // 1. Redis Lua Script 실행
    List<Object> result = luaScriptLoader.executeScript(
        "ticket_decrease",
        Arrays.asList("ticket:" + concertId, "ticket:user:" + concertId),
        1, userId
    );

    if ((Integer) result.get(0) == 0) {
        // 실패 (재고 부족 or 중복 구매)
        return TicketPurchaseResult.failed((String) result.get(1));
    }

    // 2. Redis 성공 → DB 동기화 (동기)
    try {
        ticketRepository.createTicketOrder(concertId, userId);
        return TicketPurchaseResult.success();
    } catch (Exception e) {
        // DB 저장 실패 → Redis 보상 트랜잭션
        redisTemplate.opsForValue().increment("ticket:" + concertId);
        redisTemplate.opsForSet().remove("ticket:user:" + concertId, userId);
        throw new TicketPurchaseFailedException("DB 저장 실패", e);
    }
}
```

**방법 2: 비동기 동기화 (Asynchronous - Event Driven)**
```java
// 1. Redis 성공 → 이벤트 발행
if (redisSuccess) {
    eventPublisher.publishEvent(new TicketPurchasedEvent(concertId, userId));
    return TicketPurchaseResult.success(); // 즉시 응답
}

// 2. 이벤트 리스너에서 DB 저장 (비동기)
@EventListener
@Async
public void handleTicketPurchased(TicketPurchasedEvent event) {
    try {
        ticketRepository.createTicketOrder(event.getConcertId(), event.getUserId());
    } catch (Exception e) {
        // 실패 시 재시도 (Kafka Dead Letter Queue)
        retryQueue.send(event);
    }
}
```

**티켓링크 선택: 동기 동기화**
- 이유: 결제 완료 전에 DB 저장 완료 필요 (정합성)
- Trade-off: DB 저장 시간만큼 응답 지연 (약 50ms)

### 성능 지표
- **TPS**: 100,000+ (피크 시, Redis 단독 처리)
- **Latency**:
  - Redis Script 실행: p95 < 5ms
  - DB 동기화 포함: p95 < 80ms
- **재고 소진 후 응답**: p95 < 3ms (DB 접근 없음)
- **Script 실행 시간**: SLOWLOG 기준 평균 1.2ms

### 운영 노하우

#### Script 성능 모니터링

**1. SLOWLOG 활용**
```bash
# Slowlog 설정 (10ms 이상 기록)
CONFIG SET slowlog-log-slower-than 10000

# Slowlog 조회
SLOWLOG GET 10

# 출력 예시
1) 1) (integer) 5           # Log ID
   2) (integer) 1670000000  # Timestamp
   3) (integer) 15000       # Execution time (15ms)
   4) 1) "EVALSHA"
      2) "abc123..."
      3) "2"
      4) "ticket:concert:123"
      5) "ticket:user:456"
```

**2. 성능 저하 시 대응**
- Script 실행 시간 > 10ms 시 알림
- Redis CPU 사용률 > 80% 시 스케일 업
- Script 복잡도 감소 (불필요한 연산 제거)

#### DB 동기화 실패 시 보상 트랜잭션

**시나리오 1: DB 저장 실패 (동기 방식)**
```java
// Redis 차감 성공 → DB 저장 실패
try {
    ticketRepository.save(order);
} catch (Exception e) {
    // 보상 트랜잭션: Redis 롤백
    compensateRedisStock(concertId, userId);
    throw new PurchaseFailedException();
}

private void compensateRedisStock(Long concertId, Long userId) {
    // 1. 재고 복구
    redisTemplate.opsForValue().increment("ticket:" + concertId);

    // 2. 구매 기록 삭제
    redisTemplate.opsForSet().remove("ticket:user:" + concertId, userId);

    // 3. 로그 기록 (감사용)
    logger.error("보상 트랜잭션 실행: concert={}, user={}", concertId, userId);
}
```

**시나리오 2: DB 동기화 지연 (비동기 방식)**
```java
// 재시도 큐 (Kafka Dead Letter Queue)
@KafkaListener(topics = "ticket-purchase-retry")
public void retryDBSync(TicketPurchaseEvent event) {
    int retryCount = event.getRetryCount();

    if (retryCount > 3) {
        // 최종 실패 → 수동 처리 큐로 이동
        manualProcessQueue.send(event);
        alertOps("티켓 DB 동기화 최종 실패: " + event);
        return;
    }

    try {
        ticketRepository.save(event.toOrder());
    } catch (Exception e) {
        // 재시도 (Exponential Backoff)
        event.incrementRetry();
        Thread.sleep((long) Math.pow(2, retryCount) * 1000);
        retryQueue.send(event);
    }
}
```

#### Script 복잡도 관리

**복잡도 증가 시 한계점:**
- Script 실행 시간 > 100ms: Redis 단일 스레드 모델로 인한 병목
- Script 크기 > 10KB: 네트워크 전송 오버헤드
- 조건 분기 > 10개: 유지보수 어려움

**한계 도달 시 대안:**
1. **Script 분리**: 하나의 큰 Script → 여러 작은 Script
2. **다른 방식 전환**: Redis Lock + DB 저장
3. **캐시 Warming**: 미리 계산된 값을 Redis에 저장

### 장애 경험

#### 사례 1: Script 버전 불일치로 인한 장애
**발생 원인:**
- 배포 중 일부 서버는 Script v1, 일부는 v2 사용
- v2 Script에서 새로운 파라미터 추가 → v1 Script 실패

**복구 과정:**
1. 모든 서버에 v2 Script 강제 배포
2. Redis에 등록된 Script SHA 초기화
3. 재시작 후 정상화

**개선 사항:**
- **Script 버전 관리**: SHA 해시 기반 버전 체크
- **Blue-Green 배포**: Script 변경 시 안전한 배포 전략

#### 사례 2: DB 동기화 실패로 인한 데이터 불일치
**발생 원인:**
- Redis: 재고 0 (품절)
- DB: 실제 판매 건수 999 (1건 누락)
- 원인: DB 저장 실패 후 보상 트랜잭션 미실행

**복구 과정:**
1. Redis와 DB 데이터 비교 (배치 작업)
2. 차이 발견 시 수동 조정
3. 누락된 주문 수동 생성 또는 환불 처리

**개선 사항:**
- **정합성 검증 배치**: 매일 자정 Redis-DB 데이터 비교
- **보상 트랜잭션 강화**: 실패 시 재시도 (최대 3회)
- **알림 강화**: 데이터 불일치 발견 시 즉시 Slack 알림

---

## 사례 2: 배민 - 선착순 쿠폰 발급

### 도메인
대규모 선착순 이벤트 (쿠폰 100만 개 한정)

### 선택 이유
1. **압도적 성능**: Redis Lock 대비 2배 이상 TPS
2. **Lock Contention 제거**: Lock 획득 대기 시간 없음
3. **품절 후 즉시 응답**: DB 조회 없이 Redis에서 즉시 실패 응답

### 구현 방식

#### 1. 쿠폰 발급 Lua Script (간소화 버전)
```lua
-- coupon_issue.lua
local coupon_key = KEYS[1]        -- "coupon:event:2024"
local user_set_key = KEYS[2]      -- "coupon:issued:2024"
local user_id = ARGV[1]

-- 1. 잔여 수량 확인
local remaining = tonumber(redis.call('GET', coupon_key) or "0")
if remaining <= 0 then
    return {0, "SOLD_OUT"}
end

-- 2. 중복 발급 방지
if redis.call('SISMEMBER', user_set_key, user_id) == 1 then
    return {0, "ALREADY_ISSUED"}
end

-- 3. 쿠폰 차감 및 발급 기록
redis.call('DECR', coupon_key)
redis.call('SADD', user_set_key, user_id)

return {1, "SUCCESS"}
```

#### 2. Redis → DB 동기화 (비동기 + Kafka)
```java
// 1. Redis 성공 → Kafka 이벤트 발행
public CouponIssueResult issueCoupon(Long eventId, Long userId) {
    List<Object> result = executeLuaScript(eventId, userId);

    if ((Integer) result.get(0) == 1) {
        // Redis 성공 → Kafka 발행 (비동기)
        kafkaProducer.send("coupon-issued",
            new CouponIssuedEvent(eventId, userId, LocalDateTime.now()));

        return CouponIssueResult.success();
    }

    return CouponIssueResult.failed((String) result.get(1));
}

// 2. Kafka Consumer에서 DB 저장
@KafkaListener(topics = "coupon-issued")
public void saveCouponToDB(CouponIssuedEvent event) {
    couponRepository.save(new Coupon(event.getEventId(), event.getUserId()));
}
```

**배민 선택: 비동기 동기화**
- 이유: 사용자 응답 속도 최우선 (< 50ms)
- Trade-off: DB 동기화 지연 (평균 1-2초)
- 정합성: 최종적으로 DB에 저장 (Eventual Consistency)

### 성능 지표
- **TPS**: 150,000+ (Redis Cluster 사용 시)
- **Latency**:
  - Redis Script 실행: p95 < 3ms
  - 전체 API 응답: p95 < 20ms (Kafka 발행 포함)
- **DB 동기화 지연**: 평균 1.5초, p99 < 5초

### 운영 노하우

#### Eventual Consistency 관리

**문제:**
- Redis: 쿠폰 발급 완료
- DB: 아직 저장 안 됨 (1-2초 지연)
- 사용자가 "내 쿠폰" 조회 시 표시 안 됨

**해결:**
```java
// 쿠폰 조회 API (Redis + DB 병합)
public List<Coupon> getMyCoupons(Long userId) {
    // 1. DB에서 조회 (확정된 쿠폰)
    List<Coupon> dbCoupons = couponRepository.findByUserId(userId);

    // 2. Redis에서 조회 (발급 대기 중)
    Set<String> redisKeys = redisTemplate.opsForSet()
        .members("coupon:issued:*");

    List<Coupon> pendingCoupons = redisKeys.stream()
        .filter(key -> redisTemplate.opsForSet().isMember(key, userId))
        .map(key -> new Coupon(extractEventId(key), userId, CouponStatus.PENDING))
        .collect(Collectors.toList());

    // 3. 병합 (DB + Redis)
    return merge(dbCoupons, pendingCoupons);
}
```

#### Script 버전 관리 Best Practice

**1. Git 기반 버전 관리**
```
src/main/resources/lua/
├── v1/
│   └── coupon_issue.lua
├── v2/
│   └── coupon_issue.lua  # 파라미터 추가
└── current/
    └── coupon_issue.lua  # v2 심볼릭 링크
```

**2. 배포 전략**
- Blue-Green 배포: 새 버전 Script를 별도 Redis에 등록 후 전환
- Canary 배포: 10% 트래픽만 v2 Script 사용, 모니터링 후 100% 전환

**3. 롤백 전략**
- Script SHA 해시 저장 (v1, v2)
- 롤백 시 v1 SHA로 즉시 전환

### 장애 경험

#### 사례: Kafka 지연으로 인한 DB 동기화 실패
**발생 원인:**
- Kafka Broker 장애 → 이벤트 전송 실패
- Redis에는 발급 완료, DB에는 미저장

**복구 과정:**
1. Kafka 복구 후 Dead Letter Queue에서 재처리
2. Redis Set에서 발급된 사용자 ID 추출
3. DB와 비교하여 누락 건 수동 저장

**개선 사항:**
- **Kafka 고가용성**: 3 Broker + Replication Factor 3
- **재시도 큐**: Dead Letter Queue 자동 재처리
- **정합성 배치**: 매일 자정 Redis-DB 비교 및 동기화

---

## DB 동기화 전략 비교

| 전략 | 장점 | 단점 | 권장 상황 |
|------|------|------|-----------|
| **동기 동기화** | 강한 정합성, 즉시 DB 저장 | 느린 응답 속도 (DB I/O 대기) | 금융 거래, 결제 |
| **비동기 동기화 (이벤트)** | 빠른 응답 속도 | Eventual Consistency, 복잡도 증가 | 쿠폰 발급, 티켓팅 (정합성 허용) |
| **배치 동기화** | Redis 부하 최소화 | 큰 지연 시간 (분 단위) | 통계, 로그 집계 |

**티켓링크 선택:** 동기 동기화 (정합성 우선)
**배민 선택:** 비동기 동기화 (속도 우선)

---

## Script 버전 관리 Best Practice

### 1. SHA 기반 버전 관리
```java
public class LuaScriptVersion {
    private static final Map<String, String> SCRIPT_VERSIONS = Map.of(
        "v1", "abc123...",  // Script v1 SHA
        "v2", "def456..."   // Script v2 SHA
    );

    public String getCurrentVersion() {
        return redisTemplate.opsForValue().get("script:version");
    }

    public void switchVersion(String version) {
        String sha = SCRIPT_VERSIONS.get(version);
        redisTemplate.opsForValue().set("script:version", sha);
        logger.info("Script 버전 전환: {}", version);
    }
}
```

### 2. 배포 시 자동 Script 로드
```java
@Component
public class LuaScriptInitializer implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        // 애플리케이션 시작 시 자동 로드
        luaScriptLoader.loadScript("coupon_issue");
        luaScriptLoader.loadScript("ticket_decrease");
        logger.info("Lua Scripts 로드 완료");
    }
}
```

### 3. 롤백 전략
- Script 배포 실패 시 이전 SHA로 즉시 롤백
- Blue-Green: 새 Script를 별도 Key에 등록 후 전환

---

## 보상 트랜잭션 시나리오

### 시나리오 1: Redis 성공 → DB 저장 실패 (동기)
**흐름:**
1. Redis Lua Script 성공 → 재고 차감
2. DB 저장 시도 → **실패** (네트워크 오류, DB 다운 등)
3. **보상 트랜잭션**: Redis 재고 복구

**코드:**
```java
try {
    ticketRepository.save(order);
} catch (Exception e) {
    // Redis 롤백
    redisTemplate.opsForValue().increment("ticket:" + concertId);
    redisTemplate.opsForSet().remove("ticket:user:" + concertId, userId);
    logger.error("DB 저장 실패 → Redis 롤백 완료");
    throw new PurchaseFailedException();
}
```

---

### 시나리오 2: Redis 성공 → Kafka 전송 실패 (비동기)
**흐름:**
1. Redis Lua Script 성공 → 쿠폰 발급
2. Kafka 전송 시도 → **실패** (Broker 다운)
3. **재시도**: Kafka Producer Retry (최대 3회)
4. **최종 실패**: Dead Letter Queue로 이동

**코드:**
```java
try {
    kafkaProducer.send("coupon-issued", event).get();
} catch (Exception e) {
    // Retry 3회
    for (int i = 0; i < 3; i++) {
        try {
            kafkaProducer.send("coupon-issued", event).get();
            return; // 성공
        } catch (Exception retryEx) {
            Thread.sleep(1000 * (i + 1)); // Exponential Backoff
        }
    }

    // 최종 실패 → DLQ
    deadLetterQueue.send(event);
    logger.error("Kafka 전송 최종 실패 → DLQ 이동");
}
```

---

### 시나리오 3: DB 동기화 지연으로 데이터 불일치
**문제:**
- Redis: 쿠폰 발급 완료
- DB: Kafka Consumer 지연으로 저장 안 됨

**정합성 배치 (Reconciliation):**
```java
@Scheduled(cron = "0 0 0 * * *") // 매일 자정
public void reconcileRedisAndDB() {
    // 1. Redis에서 발급된 쿠폰 조회
    Set<String> redisIssuedUsers = redisTemplate.opsForSet()
        .members("coupon:issued:2024");

    // 2. DB에서 저장된 쿠폰 조회
    List<Long> dbIssuedUsers = couponRepository
        .findUserIdsByEventId(2024L);

    // 3. 차이 계산 (Redis에만 있음 = DB 누락)
    Set<Long> missing = Sets.difference(
        redisIssuedUsers.stream().map(Long::parseLong).collect(Collectors.toSet()),
        new HashSet<>(dbIssuedUsers)
    );

    // 4. 누락 건 DB 저장
    missing.forEach(userId -> {
        couponRepository.save(new Coupon(2024L, userId));
        logger.warn("정합성 배치: 누락 쿠폰 저장 - userId={}", userId);
    });

    // 5. 알림
    if (!missing.isEmpty()) {
        slackNotifier.send("쿠폰 정합성 이슈: " + missing.size() + "건 복구");
    }
}
```

---

## Script 복잡도 증가 시 대안

### 복잡도 한계 기준
- **실행 시간**: > 100ms (Redis 단일 스레드 병목)
- **코드 크기**: > 10KB (네트워크 오버헤드)
- **조건 분기**: > 10개 (유지보수 어려움)

### 대안 1: Script 분리
**기존 (하나의 큰 Script):**
```lua
-- 재고 차감 + 쿠폰 발급 + 포인트 적립 (복잡)
```

**개선 (여러 작은 Script):**
```lua
-- Script 1: 재고 차감
-- Script 2: 쿠폰 발급
-- Script 3: 포인트 적립
```

---

### 대안 2: Redis Modules 사용
- **RedisJSON**: JSON 데이터 직접 조작
- **RedisTimeSeries**: 시계열 데이터 처리
- **RedisGraph**: 그래프 쿼리

---

### 대안 3: 다른 방식 전환
- Redis Lock + DB 저장
- DB Pessimistic Lock으로 회귀
- 메시지 큐 (Kafka) 기반 비동기 처리

---

## 결론 및 Best Practices

### Lua Script를 사용해야 하는 경우
1. **초고부하**: TPS 100,000+ 필요
2. **Lock Contention 회피**: Redis Lock도 병목인 상황
3. **원자성 보장**: 여러 Redis 명령어를 원자적으로 실행
4. **품절 후 빠른 응답**: DB 접근 없이 즉시 실패 응답

### 사용하지 말아야 하는 경우
1. **복잡한 로직**: 조건 분기 10개 이상
2. **DB 정합성 필수**: Redis-DB 불일치 허용 불가
3. **장시간 실행**: 100ms 이상 소요되는 연산

### 운영 핵심 원칙
1. **Script 버전 관리**: SHA 해시 기반 버전 체크
2. **DB 동기화 전략**: 동기 vs 비동기 선택 (정합성 vs 속도)
3. **보상 트랜잭션**: Redis 성공 → DB 실패 시 롤백
4. **정합성 배치**: 매일 Redis-DB 비교 및 동기화
5. **모니터링**: SLOWLOG, Script 실행 시간, DB 동기화 지연

---

## 부록: 실전 Lua Script 예제

### 1. 재고 차감 (기본)
```lua
local stock_key = KEYS[1]
local decrease = tonumber(ARGV[1])

local current = tonumber(redis.call('GET', stock_key) or "0")
if current < decrease then
    return {0, "INSUFFICIENT"}
end

redis.call('DECRBY', stock_key, decrease)
return {1, "SUCCESS"}
```

### 2. 중복 방지 + 재고 차감
```lua
local stock_key = KEYS[1]
local user_set_key = KEYS[2]
local user_id = ARGV[1]
local decrease = tonumber(ARGV[2])

-- 중복 확인
if redis.call('SISMEMBER', user_set_key, user_id) == 1 then
    return {0, "DUPLICATE"}
end

-- 재고 확인
local current = tonumber(redis.call('GET', stock_key) or "0")
if current < decrease then
    return {0, "INSUFFICIENT"}
end

-- 원자적 처리
redis.call('DECRBY', stock_key, decrease)
redis.call('SADD', user_set_key, user_id)

return {1, "SUCCESS"}
```

### 3. 시간 제한 + 재고 차감
```lua
local stock_key = KEYS[1]
local event_start_time = tonumber(ARGV[1])
local current_time = tonumber(ARGV[2])
local decrease = tonumber(ARGV[3])

-- 시간 확인
if current_time < event_start_time then
    return {0, "NOT_STARTED"}
end

-- 재고 확인
local current = tonumber(redis.call('GET', stock_key) or "0")
if current < decrease then
    return {0, "SOLD_OUT"}
end

-- 차감
redis.call('DECRBY', stock_key, decrease)
return {1, "SUCCESS"}
```

---

**작성자:** Claude (Sprint 6 리서치)
**기반 자료:**
- 티켓링크/인터파크 티켓팅 시스템 (일반적 패턴)
- 우아한형제들 기술 블로그
- Redis Lua Scripting 공식 문서
- Eventual Consistency 및 보상 트랜잭션 Best Practice
- 실무 경험 기반 추론
