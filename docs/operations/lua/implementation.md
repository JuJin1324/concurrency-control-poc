# [Deep Dive] Redis Lua Script Implementation Guide

**Parent Document:** [Lua Script 운영 가이드](../lua-script-ops.md)

---

## 1. Java 구현 가이드 (ScriptLoader Pattern)

Redis Lua 스크립트를 효율적으로 실행하려면 **ScriptLoader** 패턴이 필요합니다. 이 패턴의 핵심 역할은 두 가지입니다.
1.  **네트워크 최적화:** 스크립트 본문 대신 해시(SHA)만 전송 (`EVALSHA`).
2.  **자동 복구:** Redis 재시작으로 해시가 사라지면(`NOSCRIPT`), 자동으로 본문을 다시 전송 (`SCRIPT LOAD`).

### 1.1 Spring Data Redis 사용자 (Recommended)
Spring을 사용한다면 `DefaultRedisScript` 클래스가 이 **ScriptLoader 패턴을 이미 내장**하고 있습니다. 별도의 로더를 구현할 필요 없이 바로 사용하면 됩니다.

```java
@Configuration
public class LuaScriptConfig {
    @Bean
    public RedisScript<Long> couponScript() {
        // 이 객체가 내부적으로 SHA 캐싱과 자동 복구를 담당함
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/coupon_issue.lua"));
        script.setResultType(Long.class);
        return script;
    }
}

// Service에서 사용 시
public void issueCoupon(String userId) {
    // execute() 호출 시 Spring이 알아서 EVALSHA를 시도하고, 
    // 실패하면 EVAL로 재시도함.
    redisTemplate.execute(couponScript, 
                          Collections.singletonList("coupon:users"), // KEYS[1]
                          "1000", userId);                           // ARGV[1], ARGV[2]
}
```

### 1.2 Under the Hood: 원리 이해 (직접 구현 시)
만약 Spring을 쓰지 않거나 동작 원리가 궁금하다면, 아래와 같은 로직을 직접 구현해야 합니다.

```java
public <T> T execute(String scriptName, List<String> keys, Object... args) {
    // 1. 메모리에 캐싱된 SHA 해시 가져오기
    String sha = scriptShaCache.get(scriptName);
    
    try {
        // 2. 해시로 실행 시도 (EVALSHA) -> 네트워크 절약
        return redis.evalSha(sha, keys, args);
    } catch (NoScriptException e) {
        // 3. 실패 시(NOSCRIPT): Redis가 재시작되어 캐시가 날아간 상황
        // 원본 스크립트를 다시 전송(SCRIPT LOAD)하고 재시도
        String newSha = redis.scriptLoad(loadScriptFile(scriptName));
        scriptShaCache.put(scriptName, newSha);
        return redis.evalSha(newSha, keys, args);
    }
}
```

---

## 2. 실전 스크립트 예제 (Detailed)

### 2.1 선착순 쿠폰 발급 (중복 방지 + 수량 제한)
*   **목적:** "1,000명 한정, 1인 1매" 규칙을 원자적으로 보장.
*   **사용되는 Redis 명령어:**
    *   **`SISMEMBER`**: 특정 값이 Set에 포함되어 있는지 확인합니다. (중복 체크용)
    *   **`SCARD`**: Set에 포함된 전체 요소의 개수를 반환합니다. (현재 수량 확인용)
    *   **`SADD`**: Set에 새로운 값을 추가합니다. (발급 완료 기록용)
    *   **[특징]** 위 세 명령어는 모두 **O(1)** 시간 복잡도를 가져 데이터 양과 상관없이 극도로 빠릅니다.
*   **매핑:**
    *   `KEYS[1]`: `coupon:users` (참여자 명단 Set)
    *   `ARGV[1]`: `1000` (최대 수량)
    *   `ARGV[2]`: `user:123` (유저 ID)

```lua
-- 1. 중복 확인 (이미 Set에 있는지?)
if redis.call('SISMEMBER', KEYS[1], ARGV[2]) == 1 then
    return -1 -- "이미 발급되었습니다"
end

-- 2. 수량 확인 (1000명 찼는지?)
local count = redis.call('SCARD', KEYS[1])
if tonumber(count) >= tonumber(ARGV[1]) then
    return 0 -- "선착순 마감되었습니다"
end

-- 3. 발급 (Set에 추가)
redis.call('SADD', KEYS[1], ARGV[2])
return 1 -- "성공!"
```

### 2.2 기간 한정 이벤트 (시간 체크)
*   **목적:** "오후 2시부터 3시까지만" 참여 가능.
*   **매핑:**
    *   `ARGV[3]`: `1700000000` (현재 서버 시간 - Unix Timestamp)
    *   `ARGV[4]`: `1700003600` (종료 시간)

```lua
-- [중요] 스크립트 안에서 os.time()을 쓰면 안 됨! (복제 시 값 달라짐)
-- 반드시 Java 애플리케이션에서 시간을 구해서 ARGV로 넘겨줘야 함.

local current_time = tonumber(ARGV[3])
local end_time = tonumber(ARGV[4])

-- 1. 시간 체크
if current_time > end_time then
    return -2 -- "이벤트가 종료되었습니다"
end

-- 2. 이후 로직 (재고 차감 등)
return redis.call('DECRBY', KEYS[1], 1)
```

---

## 3. 버전 관리 (Versioning)

스크립트는 코드입니다. DB 프로시저처럼 관리하면 안 되고, **애플리케이션 코드(Git)**로 관리해야 합니다.

```text
src/main/resources/scripts/
├── v1/
│   └── coupon_issue.lua
├── v2/
│   └── coupon_issue.lua  # 로직 변경됨 (파라미터 추가 등)
└── current -> v2  # 심볼릭 링크 또는 설정 파일로 제어
```
*   **배포:** 새로운 버전의 스크립트는 내용이 다르므로 **SHA 해시값도 달라집니다.** 따라서 구버전 앱(v1 호출)과 신버전 앱(v2 호출)이 동시에 떠 있어도(Blue/Green 배포), 서로 다른 캐시를 쓰므로 충돌하지 않습니다.
