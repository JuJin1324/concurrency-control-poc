# Iteration 2 Summary: Redis Lua Script

**Sprint:** Sprint 2 - Redis 구현
**Iteration:** 2/3
**완료일:** 2026-01-26
**상태:** ✅ 완료

---

## 구현 내용

### 1. Redis 데이터 구조 설계

**Key Naming:**
- Format: `stock:{stockId}` (예: `stock:1`)
- Type: `String` (Integer)
- Value: `quantity`

**설계 이유:**
- `Hashes`보다 `String`이 메모리 효율적이며, `DECRBY` 명령어 사용에 최적화됨.
- Lua Script에서 `tonumber()` 변환 및 연산이 간편함.

---

### 2. Lua Script 구현

**파일:** `src/main/resources/scripts/stock-decrease.lua`

**로직:**
1. 재고 조회 (`GET`)
2. 재고 존재 여부 확인 (없으면 `-1` 반환)
3. 재고 부족 확인 (요청량보다 적으면 `-2` 반환)
4. 재고 차감 (`DECRBY`) 및 남은 재고 반환

**장점:**
- **원자성 보장:** Redis는 단일 스레드로 동작하므로 스크립트 실행 중 다른 요청이 개입할 수 없음.
- **Lock Free:** 별도의 분산 락(`RLock`) 획득/해제 과정이 없어 오버헤드가 매우 적음.
- **Network 최적화:** 여러 번의 Redis 통신(GET, SET 등)을 한 번의 스크립트 실행으로 줄임.

---

### 3. LuaScriptStockService 구현

**위치:** `src/main/java/com/concurrency/poc/service/LuaScriptStockService.java`

**핵심 기능:**
- `decreaseStock()`: Lua Script 실행 및 결과 코드(`-1`, `-2`)에 따른 예외 처리.
- `syncStockToRedis()`: DB 데이터를 Redis로 초기화하는 헬퍼 메서드 (테스트 및 운영 초기화용).
- `getStock()`: Redis 우선 조회 (Cache Hit) -> 없으면 DB 조회 및 적재 (Cache Miss).

---

## 성능 비교 (중간 점검)

동일 조건(100명 동시 요청)에서의 테스트 결과 비교입니다.

| 구분 | Pessimistic Lock | Redis Distributed Lock | **Redis Lua Script** |
|------|------------------|------------------------|----------------------|
| **실행 시간** | 176ms | 353ms | **47ms** 🚀 |
| **Success Rate** | 100% | 100% | **100%** |
| **특징** | DB 부하 큼 | 네트워크 오버헤드 있음 | **가장 빠름** |

> **분석:**
> Lua Script 방식이 압도적인 성능(약 4~8배 빠름)을 보였습니다.
> Lock 획득 대기 시간이나 DB 트랜잭션 오버헤드가 없기 때문에, 초고속 처리가 필요한 "선착순 이벤트"에 가장 적합한 모델임을 입증했습니다.

---

## 생성된 파일

| 파일 | 설명 |
|------|------|
| `stock-decrease.lua` | 재고 차감 Lua Script |
| `LuaScriptStockService.java` | Lua Script 실행 서비스 |
| `LuaScriptStockServiceTest.java` | 통합 성능 테스트 |

---

## 다음 Iteration 예고

**Iteration 3: API 통합 및 비교**
- REST API 확장 (`POST /api/stock/decrease?method=lua-script`)
- 4가지 방법 모두 API로 노출
- 최종 비교 리포트 작성

---

## 사용자 피드백

> 이 섹션은 Iteration 완료 후 사용자가 작성합니다.

### 코드 리뷰 의견
- **Q:** Redis는 단일 스레드인데, Stock 서비스가 Lua Script를 실행하는 동안 다른 도메인(예: Order)의 성능에 영향을 주는가?
- **A:** **그렇습니다.** Redis는 Lua Script 실행 중 다른 모든 요청을 차단(Block)합니다.
    - 따라서 Lua Script는 **반드시 매우 짧은 실행 시간(수 µs)**을 가져야 합니다.
    - 만약 복잡한 연산이 필요하다면 Redis 인스턴스를 도메인별로 분리(Sharding)하는 것을 고려해야 합니다.

- **Q:** DB 데이터를 Redis로 동기화할 때 CDC나 배치가 필요한 이유가 클러스터링 때문인가? 그리고 성능의 비결이 MQ를 통한 비동기 처리인가?
- **A:** **정확합니다.** 
    - **동기화(CDC/Batch):** 단순히 서버 클러스터링뿐만 아니라, 어드민이나 타 시스템에 의한 "DB 직접 수정" 발생 시 Redis와 DB 간의 데이터 정합성을 맞추기 위해 외부 파이프라인(CDC 등)이 필수적입니다.
    - **성능의 비결 (Write-Back 전략):** 압도적인 속도는 **"DB 트랜잭션이라는 무거운 I/O를 기다리지 않기 때문"**입니다.
        - Redis에서 즉시 차감 후 사용자에게 응답을 주고, DB 반영은 MQ 등을 통해 비동기로 처리함으로써 전체 시스템의 Throughput을 극대화합니다.
        - 단, 이 방식은 **최종 일관성(Eventual Consistency)**을 수용할 수 있는 비동기 아키텍처 설계가 전제되어야 합니다.

- **Q:** Redis가 다운되면 어떻게 되는가? (Chaos Engineering 관점) 현재 아키텍처는 안정성을 포기한 것이 아닌가?
- **A:** **맞습니다. 현재 구조는 High Risk, High Return 전략입니다.**
    - **SPOF (Single Point of Failure):** Redis가 죽으면 재고 차감 기능이 전면 중단되거나, 복구 시 데이터 유실(Data Loss) 가능성이 있습니다.
    - **비즈니스적 적합성:** 결제 원장 같은 중요 데이터에는 부적합합니다. 하지만 **"짧은 시간 동안 폭발적인 트래픽이 몰리는 선착순 이벤트"**에서는 DB 락으로 인한 전체 서비스 장애보다, Redis의 고성능으로 트래픽을 처리하고 0.1%의 장애 리스크(및 사후 보상)를 감수하는 것이 더 나은 비즈니스 선택일 수 있습니다.
    - **보완책:** 실제 운영 시에는 **Redis AOF(Persistence) 설정**을 켜거나, Redis 장애 시 **DB 락으로 전환하는 Fallback 로직**을 구현해야 합니다.

### 아키텍처 수정 요청
- (피드백 입력)

### 기타 의견
- (피드백 입력)
