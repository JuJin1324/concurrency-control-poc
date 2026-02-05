# [Deep Dive] Redis Distributed Lock Mechanics

**Parent Document:** [Redis Distributed Lock 운영 가이드](../redis-lock-ops.md)

이 문서는 Redis 분산 락의 기술적 동작 원리와 주요 클라이언트 구현체의 차이, 그리고 학계와 실무 사이에서 뜨거웠던 Redlock 논쟁을 심층 분석합니다.

---

## 1. 분산 락의 핵심 원리: `SET NX`

Redis 분산 락의 가장 기초가 되는 메커니즘은 **원자적 점유(Atomic Acquisition)**입니다.

### 1.1 `SET resource_key owner_id NX PX 30000`
Redis 2.6.12 버전부터 제공되는 이 단일 명령어는 분산 락의 3대 요소를 모두 충족합니다.

*   **`NX` (Not eXists):** "키가 존재하지 않을 때만 저장하라."
    *   이는 **상호 배제(Mutual Exclusion)**를 보장합니다. 여러 클라이언트가 동시에 요청을 보내도, Redis는 싱글 스레드로 동작하므로 오직 하나의 요청만 성공(Return OK)하고 나머지는 실패(Return Null)합니다.
*   **`PX 30000` (Expire):** "30,000ms 후에 키를 자동 삭제하라."
    *   이는 **데드락 방지(Deadlock Free)**를 보장합니다. 락을 획득한 클라이언트가 장애로 멈추더라도, 일정 시간이 지나면 락이 자동으로 해제됩니다.
*   **`owner_id` (Value):** "누가 락을 잡았는지 식별하라."
    *   이는 **안전한 해제(Safe Release)**를 보장합니다. 락을 해제할 때 자신이 만든 락인지 확인하기 위한 식별자(UUID 등)로 사용됩니다.

---

## 2. 구현 모델 비교: Spin Lock vs Pub/Sub

락 획득에 실패했을 때, 클라이언트는 어떻게 대기해야 할까요?

### 2.1 스핀 락 (Spin Lock) 모델 - Lettuce 방식
*   **개념:** **Busy Waiting**. 락을 얻을 때까지 클라이언트가 `SET NX` 명령어를 반복적으로 전송하는 방식입니다.
*   **동작:** `while (!acquire()) { sleep(100ms); }`
*   **문제점 (Thundering Herd):**
    *   수천 개의 스레드가 동시에 락을 대기하면, Redis에는 초당 수만 건의 `SET NX` 요청이 유입됩니다.
    *   이는 Redis의 CPU 자원을 소모하여 정작 중요한 다른 명령어 처리를 지연시킵니다.

### 2.2 Pub/Sub 기반 이벤트 모델 - Redisson 방식
*   **개념:** **Event Subscription**. 락이 해제되었다는 신호(Signal)를 받을 때까지 대기하는 방식입니다.
*   **동작:**
    1.  락 획득 실패 시, Redis의 Pub/Sub 채널을 **구독(Subscribe)**하고 스레드는 대기 상태(Wait)로 전환됩니다.
    2.  락을 점유하던 클라이언트가 `UNLOCK`을 수행하면, Redis는 채널을 통해 메시지를 발행(Publish)합니다.
    3.  대기 중이던 클라이언트들은 신호를 받고 깨어나 락 획득을 재시도합니다.
*   **장점:** 불필요한 네트워크 트래픽과 CPU 소모를 최소화하여 시스템의 안정성을 높입니다.

---

## 3. Redlock 알고리즘: 이론적 이상과 현실적 제약

Redis 창시자 Antirez가 제안한 **Redlock**은 "Redis가 한 대 죽어도 락을 유지하자"는 고가용성 알고리즘입니다. 하지만 치명적인 논쟁이 있었습니다.

### 3.1 동작 방식 (Quorum)
1.  N개(보통 5개)의 독립적인 Redis 마스터 노드에 순차적으로 락 획득을 시도합니다.
2.  **과반수(N/2 + 1)** 이상의 노드에서 락을 획득하고, 소요 시간이 TTL보다 짧을 때 성공으로 간주합니다.

### 3.2 Martin Kleppmann의 비판: "분산 환경의 불확실성"
분산 시스템의 거장인 Martin Kleppmann은 Redlock이 **"시간(Time)과 동기화"**에 의존하기 때문에 위험하다고 지적했습니다.

*   **Clock Skew (시각 편향):** 각 Redis 노드의 시스템 시간이 미세하게 다를 수 있습니다. 이로 인해 락 만료 시점이 달라져 정합성이 깨질 수 있습니다.
*   **Process Pause (GC 등):**
    1.  클라이언트 A가 락을 획득함.
    2.  Java **GC(Garbage Collection)** 등으로 인해 애플리케이션이 수 초간 멈춤.
    3.  그 사이 Redis에서는 TTL 만료로 락이 해제됨.
    4.  클라이언트 B가 락을 획득하고 데이터를 수정함.
    5.  깨어난 클라이언트 A는 자신이 여전히 락을 보유했다고 착각하고 데이터를 덮어씀. (**데이터 오염**)

### 3.3 실무의 선택
대부분의 기업은 Redlock의 복잡도와 성능 저하 비용 대비 이득이 적다고 판단하여 사용하지 않습니다.
*   **대안:** **단일 Redis(Sentinel/Cluster) + DB 낙관적 락(Version)** 조합을 사용합니다. Redis 장애로 락이 유실되더라도, DB 레벨에서 최종 정합성을 검증하는 것이 훨씬 실용적입니다.

---

## 4. Lua Script를 통한 원자성 보장

Redis는 단일 명령어에 대해서만 원자성을 보장합니다. 여러 명령어를 조합해야 하는 경우(예: 확인 후 삭제), 그 사이의 틈(Gap)을 없애기 위해 Lua Script가 필수적입니다.

### 4.1 안전한 언락 (Safe Unlock) 시나리오
*   **상황:** 락을 해제하기 위해 `DEL key`를 전송하려는 찰나, 타임아웃으로 락이 만료되고 다른 클라이언트가 락을 획득함.
*   **문제:** 내가 보낸 `DEL` 명령어가 **다른 클라이언트의 락을 삭제**해버리는 사고 발생.
*   **해결 (Lua Script):**
    ```lua
    -- "내 ID와 일치하는지 확인하고(GET), 맞을 때만 삭제하라(DEL)"
    if redis.call("get", KEYS[1]) == ARGV[1] then
        return redis.call("del", KEYS[1]) 
    else
        return 0
    end
    ```
    *   Redis 내부에서 이 스크립트는 하나의 트랜잭션처럼 원자적으로 실행되므로, 중간에 다른 명령어가 끼어들 수 없습니다.
