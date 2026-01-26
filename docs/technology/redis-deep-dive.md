# Redis Deep Dive: 역사부터 아키텍처까지

**작성일:** 2026-01-26
**작성자:** Gemini Agent (Concurrency Control PoC Team)

---

## 1. Redis의 기원과 철학

### 1.1 탄생 배경: LLOOGG 프로젝트
Redis(REmote DIctionary Server)는 2009년 **Salvatore Sanfilippo (antirez)**에 의해 탄생했습니다. 당시 그는 'LLOOGG'라는 실시간 웹 로그 분석기를 개발 중이었습니다.

- **문제점:** 전통적인 RDBMS(MySQL)는 디스크 기반이라 실시간으로 쏟아지는 로그 데이터의 쓰기 속도(Write Throughput)와 페이지 뷰 카운트 갱신을 감당하기에 너무 느렸습니다.
- **해결책:** "디스크가 아니라 **메모리(RAM)**에서 데이터를 처리하면 어떨까?"라는 아이디어에서 출발하여, 리스트(List) 구조를 메모리상에서 관리하는 프로토타입을 C언어로 작성했습니다.

### 1.2 Redis의 핵심 철학
> **"Redis is a data structure server."**

Redis는 단순한 Key-Value 저장소가 아니라, **'메모리 상의 자료구조(Data Structure)를 외부에서도 쓸 수 있게 해주는 서버'**입니다. String, List, Set, Hash, Sorted Set 등의 자료구조를 지원하는 이유가 바로 이 "자료구조 서버"라는 정체성 때문입니다.

---

## 2. 왜 Single Thread 인가?

Redis를 처음 접하는 개발자들이 가장 많이 하는 질문입니다. "요즘 같은 멀티 코어 시대에 왜 싱글 스레드인가?"

### 2.1 아키텍처의 비밀: Event Loop
Redis는 **Single Threaded Event Loop** 아키텍처(Reactor Pattern)를 따릅니다. Node.js와 유사합니다.

1.  **CPU는 병목이 아니다:** 메모리 접근 속도는 매우 빠르기 때문에, Redis 성능의 병목은 주로 **네트워크 대역폭(Network Bandwidth)**이나 **메모리 크기**이지 CPU 연산 능력이 아닙니다.
2.  **Context Switching 비용 제거:** 멀티 스레드는 스레드 간 전환(Context Switching)에 비용이 들고, 공유 자원에 대한 락(Lock) 관리가 복잡합니다. Redis는 이를 원천적으로 제거하여 효율을 극대화했습니다.
3.  **단순함(Simplicity):** 경쟁 상태(Race Condition)나 데드락(Deadlock) 같은 동시성 문제에서 (엔진 내부적으로는) 자유롭습니다.

### 2.2 Single Thread의 의미
Redis가 싱글 스레드라는 것은 **"명령어(Command)를 처리하는 핵심 로직이 하나"**라는 뜻입니다. (물론 Redis 6.0부터는 네트워크 I/O 처리에 한해 멀티 스레드를 도입했지만, 명령어 실행 자체는 여전히 단일 스레드입니다.)

---

## 3. 원자성(Atomicity)과 동시성 제어

이 Single Thread 특성이 바로 Redis를 강력한 **동시성 제어 도구**로 만듭니다.

### 3.1 명령어 단위의 원자성
Redis의 모든 명령어(`GET`, `SET`, `INCR`, `DECR`)는 원자적(Atomic)입니다.
- **원리:** 스레드가 하나뿐이므로, **"내가 명령어를 실행하는 동안 다른 누구도 끼어들 수 없음"**이 물리적으로 보장됩니다.
- **예:** `INCR stock:1`을 100명이 동시에 요청해도, Redis는 이를 순서대로 하나씩 처리하여 정확히 100을 증가시킵니다.

### 3.2 트랜잭션과 Lua Script
단일 명령어는 원자적이지만, `GET`하고 `SET`하는 두 명령어 사이에는 틈이 있습니다. 이를 메우기 위해 Lua Script가 사용됩니다.

- **Lua Script의 원자성:** Redis는 Lua Script 전체를 **"하나의 거대한 명령어"**로 취급합니다.
- **Blocking:** 스크립트가 실행되는 동안 Redis는 다른 클라이언트의 요청을 전혀 받지 않고 대기(Block)시킵니다.
- **결과:** 복잡한 로직(조회 -> 비교 -> 차감)을 마치 락(Lock)을 건 것처럼 안전하게 처리할 수 있습니다.

### 3.3 Redis 락과 트랜잭션 격리 수준 (Isolation Level)
**"Redis Distributed Lock은 RDBMS의 어떤 격리 수준에 해당할까?"**

- **결론:** **`Serializable` (직렬화 가능)** 수준에 해당합니다.
- **이유:** Redisson 락은 **상호 배제(Mutual Exclusion)** 락이므로, 락을 획득한 하나의 스레드만 진입을 허용하고 나머지는 대기시킵니다. 즉, 물리적인 **순차 실행(Serial Execution)**을 강제합니다.
- **주의점:** 이는 **'자발적 락(Advisory Lock)'**입니다. 즉, 데이터를 수정하는 쪽에서만 락을 걸고, 읽는 쪽에서 락 없이(`Read Uncommitted`) 접근한다면 격리 수준은 깨질 수 있습니다. 따라서 모든 클라이언트가 락 규약을 준수해야 안전합니다.

---

## 4. 프로젝트 적용 분석

본 PoC 프로젝트(Concurrency Control)에서 사용한 두 가지 Redis 전략을 분석합니다.

### Case 1: Redis Distributed Lock (Redisson)
*   **방식:** `Lock 획득` -> `DB 트랜잭션` -> `Lock 해제`
*   **원리:** Redis의 `Pub/Sub` 기능을 이용해 "락이 풀렸다"는 신호를 대기 중인 스레드들에게 보냅니다. (Spin Lock의 CPU 낭비 방지)
*   **특징:**
    *   동시성 제어의 주체는 애플리케이션(Redisson Client)입니다.
    *   Redis는 락 상태(`lock:key`)를 관리하는 저장소 역할만 합니다.
    *   **안정성 중시:** DB의 정합성을 지키면서 분산 환경을 제어할 때 사용합니다.

### Case 2: Redis Lua Script (Atomic Operation) 🚀
*   **방식:** `Lua Script 실행 (조회+검증+차감)` -> `비동기 DB 반영`
*   **원리:** Redis의 Single Thread 특성을 이용하여 Lock 과정 자체를 없앴습니다(Lock-Free).
*   **특징:**
    *   동시성 제어의 주체가 Redis 엔진 그 자체입니다.
    *   네트워크 왕복(Round-Trip)을 최소화하여 극한의 속도를 냅니다.
    *   **속도 중시:** "선착순 이벤트" 등 폭발적인 트래픽 처리에 특화되었습니다.

---

## 5. 결론 및 시사점

### "Single Thread는 양날의 검이다"

1.  **장점 (무기):** 복잡한 락 없이도 완벽한 **직렬화(Serialization)**를 제공하여 데이터 정합성을 가장 쉽고 빠르게 지켜줍니다. (Lua Script 활용 시)
2.  **단점 (위험):** 하나의 명령어가 오래 걸리면(Slow Query, Long Script), **전체 시스템이 멈춥니다(Blocking).**

### 우리 프로젝트에서의 교훈
- **Lua Script**는 엄청난 성능을 제공하지만, 그 안의 로직은 **반드시 O(1)에 가까운 단순한 연산**이어야 합니다.
- **데이터 분리:** 만약 `Stock` 처리 때문에 `Order` 처리가 늦어지는 현상(Noisy Neighbor)이 발생한다면, Redis 인스턴스를 도메인별로 물리적으로 분리하는 것이 정석입니다.

---

## 6. 확장 논의: 저장소(Persistence Layer)의 변화

**"Redis가 앞단에서 동시성을 완벽하게 제어해준다면, 굳이 RDBMS(MySQL)를 고집할 필요가 있을까?"**

### 6.1 NoSQL(Document DB) 도입 가능성
**가능합니다. 심지어 더 효율적일 수 있습니다.**

1.  **역할의 축소:** 기존 아키텍처에서 RDBMS는 데이터 저장뿐만 아니라 `ACID 트랜잭션`과 `Row Lock`을 통한 동시성 제어까지 담당했습니다. 하지만 Redis(Lua Script)가 이 "Gatekeeper" 역할을 대신 수행하므로, DB는 순수한 **"영속 저장소(Archive)"** 역할로 축소됩니다.
2.  **Write Throughput 증대:** RDBMS의 무거운 제약조건(FK, Transaction Isolation)이 불필요해지므로, 쓰기 성능(Write Scalability)이 뛰어난 **MongoDB**나 **Cassandra** 같은 NoSQL이 오히려 고성능 아키텍처에 적합할 수 있습니다.
3.  **유연한 스키마:** 상품 정보나 재고 이력 같은 비정형 데이터를 저장하기에도 Document DB가 유리합니다.

### 6.2 주의사항 (Trade-off)
하지만 RDBMS를 버릴 때는 다음 사항을 반드시 고려해야 합니다.

1.  **복구의 복잡성:** Redis가 터져서 데이터가 유실되었을 때, RDBMS는 트랜잭션 로그(Binlog) 등을 통해 특정 시점으로의 완벽한 복구(PITR)가 비교적 쉽지만, NoSQL은 제품마다 복구 메커니즘과 일관성 수준이 다릅니다.
2.  **관계형 데이터:** 만약 재고(Stock) 데이터가 주문(Order), 결제(Payment) 등 다른 테이블과 **강력한 참조 무결성(FK)**을 유지해야 한다면, NoSQL로의 전환은 신중해야 합니다. 결국 애플리케이션 레벨에서 조인을 구현해야 하는 비용이 발생할 수 있습니다.

### 6.3 읽기 일관성(Read Consistency)과 성능의 타협
**"데이터 수정 중에도 읽기를 허용해도 될까?" (예: 소셜 미디어 피드)**

Redis는 `Serializable` 같은 강력한 쓰기 제어를 제공하지만, **읽기(Read)**에 대해서는 유연한 전략을 취할 수 있습니다.
- **Strict Consistency (엄격):** 읽기 시에도 락을 걸면 데이터 정합성은 완벽하지만 성능이 급감합니다.
- **Eventual Consistency (최종 일관성):** 소셜 미디어의 '좋아요' 수나 게시글 목록처럼, **"쓰는 동안 잠시 옛날 데이터가 보여도 되는"** 서비스라면 락 없이(Lock-Free) Redis에서 읽게 하여 성능을 극대화할 수 있습니다.
- **전략:** **"쓰기는 직렬화(Redis Lock/Lua)로 엄격하게, 읽기는 비동기(Replica/Cache)로 느슨하게"** 가져가는 것이 고성능 아키텍처의 핵심입니다.

**결론:** Redis 기반 동시성 제어는 **"Polyglot Persistence(다양한 저장소 혼용)"** 전략을 가능하게 하는 강력한 인에이블러(Enabler)입니다.

---
*Reference: Redis Documentation, "Redis in Action" by Josiah L. Carlson*
