# [Deep Dive] Redis Distributed Lock Implementation Guide

**Parent Document:** [Redis Distributed Lock 운영 가이드](../redis-lock-ops.md)

이 문서는 Redis 락을 구현하는 두 가지 주요 방법(Redisson, Lettuce)의 표준 패턴과, 실무에서 반드시 알아야 할 설정 튜닝 포인트를 다룹니다.

---

## 1. Redisson 구현 패턴 (Recommended)

대부분의 경우 **Redisson** 사용을 강력히 권장합니다. 복잡한 락 갱신(Watchdog)과 Pub/Sub 대기를 라이브러리 내부에서 알아서 처리해주기 때문입니다.

### 1.1 기본 구현 템플릿
```java
RLock lock = redissonClient.getLock("product:123");

try {
    // tryLock(대기시간, 점유시간, 시간단위)
    // - waitTime (3s): 락을 얻기 위해 최대 3초까지 기다림. 3초 지나면 false 반환. (스레드 보호)
    // - leaseTime (-1): 락을 잡고 있는 시간. -1로 설정하면 'Watchdog'이 켜져서, 
    //                   작업이 길어지면 알아서 락 시간을 연장해줌. (가장 안전함)
    boolean acquired = lock.tryLock(3, -1, TimeUnit.SECONDS);

    if (acquired) {
        // [비즈니스 로직 수행]
        // 재고 차감, 결제 등...
    } else {
        // 락 획득 실패 시 처리 (Fail-fast)
        throw new CustomLockTimeoutException("현재 사용자가 많아 대기 시간이 초과되었습니다.");
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
} finally {
    // 중요: 락을 해제할 때는 '내가 잡은 락인지' 확인해야 함.
    // 타임아웃으로 이미 락이 풀려서 남이 잡고 있는데, 내가 억지로 풀면 안 되니까.
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

---

## 2. Lettuce 경량 구현 패턴 (Low-Level)

라이브러리 의존성을 최소화하고 싶거나, 아주 단순한 락이 필요할 때 사용합니다. 하지만 **스핀 락(Spin Lock) 방식**이므로 주의가 필요합니다.

### 2.1 SET NX EX 직접 구현
```java
// 락의 주인(Owner)을 식별하기 위한 고유 ID 생성
String lockKey = "lock:product:123";
String lockValue = UUID.randomUUID().toString();

// 1. 락 획득 시도 (SET NX)
// "키가 없을 때만(NX) 저장하고, 10초 뒤에 자동 삭제해(EX 10)"
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);

if (Boolean.TRUE.equals(acquired)) {
    try {
        // [비즈니스 로직 수행]
    } finally {
        // 2. 안전한 언락 (Safe Unlock)
        // 그냥 delete() 하면 안 됨! (타임아웃 이슈)
        // Lua Script를 써서 "내 lockValue랑 똑같을 때만 삭제"해야 함.
        String script = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";
            
        redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), 
                             Collections.singletonList(lockKey), // KEYS[1]
                             lockValue);                         // ARGV[1]
    }
} else {
    // 락 획득 실패 (재시도 로직은 별도 구현 필요)
}
```

---

## 3. 설정 튜닝 (Configuration)

### 3.1 Sentinel 설정: "빨리 복구하라"
Redis 마스터가 죽었을 때, 슬레이브가 마스터로 승격되는 시간(Failover)이 길면 서비스 장애로 이어집니다.
```yaml
spring:
  data:
    redis:
      sentinel:
        # 마스터가 응답 없으면 5초 뒤에 죽은 걸로 간주 (기본 30초는 너무 김)
        down-after-milliseconds: 5000
        # 페일오버 작업 자체의 타임아웃
        failover-timeout: 10000
```

### 3.2 Connection Pool: "많다고 좋은 게 아님"
Redis는 **싱글 스레드**로 동작합니다. 커넥션이 수백 개라도 Redis는 한 번에 하나씩만 처리합니다.
*   **Netty (비동기):** 커넥션 1개만 있어도 수천 개의 요청을 처리할 수 있습니다. (Multiplexing)
*   **Jedis/Lettuce (동기):** 트랜잭션(`MULTI/EXEC`)이나 `Blocking` 명령어를 쓸 때만 풀이 필요합니다.
*   **권장:** `pool-size`를 무작정 늘리지 말고, 모니터링하며 적절히 조절하세요.

---

## 4. Lua Script Advanced Patterns

단순한 락 획득/해제 외에도, **"락을 걸지 않고도(Lock-free) 원자적 처리가 가능한"** Lua Script 패턴입니다.

### 4.1 Atomic Check & Decrease (재고 차감)
`GET`(조회) 후 `DECR`(차감) 사이에 다른 요청이 끼어들 수 없도록 스크립트로 묶습니다.

```java
String script = 
    "local stock = tonumber(redis.call('GET', KEYS[1])); " + // 1. 재고 조회
    "if stock == nil then return -1; end; " +                // 2. 키 없음
    "if stock < tonumber(ARGV[1]) then return 0; end; " +    // 3. 재고 부족 (요청량보다 적음)
    "return redis.call('DECRBY', KEYS[1], ARGV[1]);";        // 4. 차감 성공 (남은 재고 반환)

// 실행 (네트워크 1번만 탐)
Long result = redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), 
                                   Collections.singletonList("product:stock:1"), // KEYS[1]
                                   5);                                           // ARGV[1] (차감할 수량)
```
*   **장점:** 락을 획득하고 반납하는 네트워크 왕복 비용(RTT)을 아끼고, Redis 내부에서 1회 실행으로 끝내므로 성능이 압도적입니다.