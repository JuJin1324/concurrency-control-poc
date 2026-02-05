# [Deep Dive] Redis Lua Script Troubleshooting & Checklists

**Parent Document:** [Lua Script 운영 가이드](../lua-script-ops.md)

Lua Script는 Redis 전체를 멈출 수 있는 강력한 권한을 가집니다. 따라서 운영자는 스크립트가 초래할 수 있는 최악의 시나리오를 이해하고, 이를 모니터링하고 대응할 수 있는 매뉴얼을 갖춰야 합니다.

---

## 1. 성능 모니터링: "1ms도 길다"

스크립트 하나가 Redis를 점유하면 그 시간 동안 다른 모든 요청(로그인, 결제 등)이 대기 상태에 빠집니다.

### 1.1 SLOWLOG 설정 (Redis Server Config)
SLOWLOG는 Redis 서버의 메모리 내부(Ring Buffer)에 느린 쿼리를 기록하는 기능입니다. 디스크 I/O가 없으므로 성능 영향이 거의 없습니다.

*   **설정:** `CONFIG SET slowlog-log-slower-than 1000` (1ms = 1,000µs)
    *   **Why 1ms?** Redis는 초당 10만 건 이상을 처리해야 합니다. 1ms가 걸린다는 건 초당 처리량이 1,000건으로 1/100 토막 난다는 뜻입니다. 이는 장애의 전조 증상입니다.
*   **확인:** `redis-cli`에서 `SLOWLOG GET 10` 명령어로 최근 10개의 느린 쿼리를 조회합니다.
*   **주의:** 메모리에 저장되므로 재시작하면 사라집니다. 실시간 모니터링 용도입니다.

### 1.2 핵심 지표 (Metrics)
*   **`redis_script_execution_time`:** 스크립트 실행 시간. p99가 5ms를 넘어가면 위험 신호입니다.
*   **`used_cpu_user`:** Redis 프로세스가 순수하게 연산에 쓴 CPU 시간. 스크립트 루프가 돌면 이 수치가 급증합니다.

---

## 2. 장애 대응: NOSCRIPT Error (Cache Miss)

Redis는 인메모리 기반이므로, 서버가 **재시작되거나 Failover**가 발생하면 메모리에 적재된 스크립트(SHA 캐시)가 모두 휘발됩니다. 또한 메모리가 부족하면 LRU 정책에 의해 자주 안 쓰는 스크립트가 삭제될 수도 있습니다.

### 2.1 증상
*   애플리케이션은 여전히 기존 SHA 해시값을 가지고 `EVALSHA`를 호출합니다.
*   Redis는 해당 해시를 찾지 못해 **`NOSCRIPT No matching script...`** 에러를 반환합니다.

### 2.2 해결책: 클라이언트 자동 복구 (Auto-Recovery)
이 에러는 운영자가 수동으로 해결할 수 없습니다. 반드시 **클라이언트 코드 레벨에서 자동 복구**되도록 구현해야 합니다.

1.  **Catch:** `EVALSHA` 실행 중 `NOSCRIPT` 에러를 잡습니다.
2.  **Reload:** 즉시 `SCRIPT LOAD` 명령어로 해당 스크립트 본문을 다시 Redis에 등록합니다.
3.  **Retry:** 등록된 새 SHA(사실 기존과 동일)로 `EVALSHA`를 재시도합니다.

```java
try {
    return evalSha(sha, keys, args);
} catch (RedisSystemException e) {
    if (e.getMessage().contains("NOSCRIPT")) {
        // 스크립트가 없으면 다시 로드하고 재실행 (투명한 복구)
        String newSha = scriptLoad(scriptContent);
        return evalSha(newSha, keys, args);
    }
    throw e;
}
```

---

## 3. 물리적 한계와 Hot Key 문제

"Redis는 싱글 스레드다." 이 사실은 변하지 않습니다.

### 3.1 한계점: 1차선 고속도로
*   Redis는 아무리 빨라도 **1차선 고속도로**입니다.
*   앞차(스크립트)가 1초 동안 천천히 가면, 뒤따라오는 슈퍼카(단순 조회)들도 모두 1초를 기다려야 합니다.
*   따라서 스크립트는 무조건 **짧고 간결하게(Short & Simple)** 유지해야 합니다.

### 3.2 Hot Key 문제 (샤딩의 배신)
*   **상황:** Redis Cluster를 써서 서버를 100대로 늘렸습니다. 하지만 모든 유저가 **"아이유 콘서트 A석(단일 키)"**만 예매하려고 합니다.
*   **결과:** 해당 키가 저장된 **1번 노드만 죽어나고**, 나머지 99대 노드는 놉니다. 샤딩 효과가 0이 됩니다.

### 3.3 해결책 (Advanced)
1.  **Upstream Caching:** 읽기 요청(`GET`)이라도 Redis까지 오지 않게, WAS의 로컬 캐시(Ehcache, Caffeine)에서 먼저 막아야 합니다.
2.  **Key Splitting (키 쪼개기):**
    *   `stock` 키 하나에 100개를 넣지 말고, `stock:1` (10개), `stock:2` (10개) ... `stock:10` (10개)로 쪼갭니다.
    *   Lua Script는 랜덤하게 `stock:N` 중 하나를 골라 차감합니다.
    *   이렇게 하면 트래픽이 여러 노드로 분산(Sharding)되어 처리량이 선형적으로 증가합니다.

### 3.4 아키텍처 격리 전략 (Bulkhead Pattern)
Lua 스크립트를 사용하는 Redis는 단순 저장소가 아니라 **"연산 장치(Compute Node)"**로 취급해야 합니다. 따라서 캐시용 Redis와 물리적으로 분리하는 것이 필수입니다. 이를 **벌크헤드(Bulkhead, 격벽) 패턴**이라고 합니다.

*   **벌크헤드란?:** 선박 내부를 여러 개의 칸막이(격벽)로 나누어, 한 칸에 구멍이 나도 배 전체가 침몰하지 않게 막는 기술에서 유래했습니다.
*   **목적:** 성능보다는 **안정성(Stability)**과 **장애 전파 방지** 때문입니다.
*   **시나리오:** 선착순 이벤트로 인해 `Event Redis`가 과부하로 멈췄다고 가정해 봅시다.
    *   **통합 사용 시:** 로그인, 상품 조회 등 메인 기능까지 전면 마비됩니다. (전면 장애)
    *   **격리 사용 시:** 이벤트만 실패하고, 결제나 상품 조회는 정상 동작합니다. (부분 장애)
*   **권장:** 리스크가 큰 기능(선착순, Lua 헤비 유저)은 반드시 **별도의 Redis 클러스터**로 격리하여 장애를 특정 구역 내로 가두십시오.

---

## 4. 긴급 대응 매뉴얼 (Emergency)

스크립트가 무한 루프에 빠지거나 멈추지 않을 때 사용하는 최후의 수단입니다.

### 4.1 무한 루프 탈출: `SCRIPT KILL`
*   **상황:** 개발자의 실수로 `while(true)` 루프가 포함된 스크립트가 실행됨.
*   **증상:** Redis가 응답하지 않고 CPU 100% 유지.
*   **대응:** 다른 터미널에서 `redis-cli`로 접속하여 `SCRIPT KILL`을 입력합니다.
    *   이 명령은 현재 실행 중인 스크립트가 **"쓰기(Write) 작업을 하지 않았을 때만"** 안전하게 강제 종료시킬 수 있습니다.

### 4.2 최후의 수단: `SHUTDOWN NOSAVE`
*   **상황:** 스크립트가 이미 데이터를 변경(`SET` 등)한 후 루프에 빠짐. `SCRIPT KILL`이 먹히지 않음.
*   **대응:** `SHUTDOWN NOSAVE`로 Redis를 강제 종료하고 재시작해야 합니다. (데이터 일부 유실 감수)

### 4.3 개발 단계 디버깅: Lua Debugger
*   **도구:** `redis-cli --ldb --eval script.lua key1 , arg1`
*   **기능:** 스텝 바이 스텝 실행, 변수 값 확인 등 IDE 수준의 디버깅이 가능합니다. 프로덕션 배포 전 반드시 사용해 보세요.