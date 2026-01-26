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

## 7. Redis의 실전 활용 사례 (Production Use Cases)

Redis는 단순한 캐싱 도구를 넘어, 다양한 실전 시나리오에서 핵심 인프라로 활용됩니다. 2026년 현재, 프로덕션 환경에서 가장 널리 사용되는 8가지 활용 사례를 살펴봅니다.

---

### 7.1 Caching (캐싱) - 가장 기본이자 핵심

**개념:**
자주 조회되는 데이터를 메모리에 저장하여, 반복적이고 비싼 데이터베이스 호출을 피하고 응답 시간을 개선합니다.

**실전 예시:**
```java
// 상품 정보 캐싱
String productKey = "product:" + productId;
String cachedProduct = redisTemplate.opsForValue().get(productKey);

if (cachedProduct == null) {
    // Cache Miss: DB에서 조회
    Product product = productRepository.findById(productId);
    redisTemplate.opsForValue().set(productKey, product, Duration.ofMinutes(30));
    return product;
}
// Cache Hit: Redis에서 바로 반환
return deserialize(cachedProduct);
```

**Redis가 적합한 이유:**
- 메모리 기반이라 디스크 I/O 대비 100~1000배 빠름
- TTL (Time To Live) 자동 만료로 오래된 데이터 정리
- LRU (Least Recently Used) 정책으로 메모리 효율적 관리

**실무 팁:**
- Cache Stampede 방지: 동시에 여러 요청이 DB를 치지 않도록 Lock 사용
- Cache Warming: 서버 시작 시 미리 자주 쓰는 데이터를 Redis에 적재

---

### 7.2 Distributed Lock (분산 락) - ✅ 프로젝트 적용됨

**개념:**
여러 서버(분산 환경)에서 공유 자원에 대한 접근을 상호 배제(Mutual Exclusion)하여 동시성 문제를 해결합니다.

**실전 예시:**
```java
// Redisson을 사용한 분산 락 (본 프로젝트에서 사용)
RLock lock = redissonClient.getLock("stock:lock:" + stockId);
try {
    boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
    if (acquired) {
        // 임계 영역 (Critical Section)
        decreaseStock(stockId, quantity);
    }
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

**Redis가 적합한 이유:**
- SETNX (SET if Not eXists) 명령어로 원자적 락 획득
- Pub/Sub 기능으로 Spin Lock의 CPU 낭비 방지 (Lock 해제 알림)
- 분산 환경에서도 단일 Redis가 락 상태의 Single Source of Truth

**실무 활용:**
- 재고 차감 (본 프로젝트)
- 결제 중복 방지
- 선착순 이벤트 (선물 지급 등)
- 배치 작업의 중복 실행 방지

---

### 7.3 Message Queue (메시지 큐) - Pub/Sub vs Streams

Redis는 두 가지 메시징 패턴을 제공합니다.

#### 7.3.1 Redis Pub/Sub - 실시간 브로드캐스팅

**개념:**
발행자(Publisher)가 메시지를 발행하면, 구독자(Subscriber)들이 실시간으로 수신합니다.

**실전 예시:**
```java
// Publisher
redisTemplate.convertAndSend("chat:room:1", "Hello, World!");

// Subscriber
@Component
public class ChatSubscriber {
    @RedisMessageListener(topic = "chat:room:1")
    public void onMessage(String message) {
        System.out.println("New message: " + message);
    }
}
```

**특징:**
- **장점:** 매우 빠름, 낮은 레이턴시
- **단점:** 메시지가 영속되지 않음 (구독자가 오프라인이면 메시지 유실)
- **적합한 경우:** 채팅, 실시간 알림, 이벤트 브로드캐스팅

#### 7.3.2 Redis Streams - 영속적 메시지 스트림

**개념:**
Append-Only 로그 구조로, 메시지를 영속화하고 소비자 그룹(Consumer Group)을 지원합니다.

**실전 예시:**
```java
// Producer
StreamRecords.newRecord()
    .ofObject("delivery-update")
    .withStreamKey("logistics:stream");

// Consumer Group
redisTemplate.opsForStream()
    .read(Consumer.from("logistics-group", "worker-1"),
          StreamOffset.create("logistics:stream", ReadOffset.lastConsumed()));
```

**Pub/Sub와의 차이:**

| 특성 | Pub/Sub | Streams | 
|------|---------|---------| 
| 메시지 영속성 | ❌ 없음 | ✅ 있음 | 
| 오프라인 소비 | ❌ 불가능 | ✅ 가능 | 
| 소비자 그룹 | ❌ 없음 | ✅ 있음 | 
| 적합한 경우 | 실시간 알림 | 이벤트 소싱, 마이크로서비스 간 통신 |

**실무 사례:**
- **Harness:** 마이크로서비스 아키텍처에서 이벤트 기반 통신에 Redis Streams 활용
- **물류 플랫폼:** 배송 업데이트를 실시간 처리하면서 성능 저하 없이 처리

---

### 7.4 Session Storage (세션 저장소)

**개념:**
웹 애플리케이션의 사용자 세션 데이터를 Redis에 저장하여 빠른 읽기/쓰기 제공 및 수평 확장 가능.

**실전 예시:**
```java
// Spring Session with Redis
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfig {
    // 자동으로 HttpSession이 Redis에 저장됨
}

// Controller에서 사용
public String handleRequest(HttpSession session) {
    session.setAttribute("user", currentUser);
    return "Welcome!";
}
```

**기존 DB 대비 장점:**
- **속도:** 메모리 기반이라 세션 조회가 밀리초 단위
- **수평 확장:** 여러 서버가 동일한 Redis를 바라보므로 세션 공유 쉬움
- **자동 만료:** TTL로 오래된 세션 자동 정리

**실무 활용:**
- 로그인 세션 관리
- 장바구니 (비로그인 사용자)
- 임시 데이터 (폼 작성 중 데이터)

---

### 7.5 Rate Limiting (속도 제한)

**개념:**
특정 시간 동안 사용자/IP의 요청 횟수를 제한하여 API 남용을 방지합니다.

**실전 예시:**
```java
// Sliding Window Rate Limiter
String key = "rate:limit:" + userId;
Long count = redisTemplate.opsForValue().increment(key);

if (count == 1) {
    redisTemplate.expire(key, Duration.ofMinutes(1));
}

if (count > 100) {
    throw new RateLimitExceededException("Too many requests");
}
```

**Redis가 적합한 이유:**
- INCR 명령어가 원자적(Atomic)이므로 동시 요청에도 정확한 카운팅
- EXPIRE로 시간 윈도우 자동 관리
- 분산 환경에서 모든 서버가 동일한 Redis를 보므로 일관된 제한

**고급 패턴:**
- **Fixed Window:** 1분마다 카운터 초기화 (구현 간단, 정확도 낮음)
- **Sliding Window:** 정확한 시간 단위 추적 (Sorted Set 사용)
- **Token Bucket:** 일정 속도로 토큰 충전, 버스트 트래픽 허용

**실무 활용:**
- API Gateway에서 요청 제한
- 로그인 실패 횟수 제한 (Brute Force 방지)
- SMS/이메일 발송 횟수 제한

---

### 7.6 Leaderboard (순위표) - 게임, 랭킹 시스템

**개념:**
실시간으로 순위를 계산하고 조회해야 하는 경우, Redis의 Sorted Set을 활용합니다.

**실전 예시:**
```java
// 점수 추가/업데이트
redisTemplate.opsForZSet().add("game:leaderboard", "player:123", 9500);

// Top 10 조회
Set<TypedTuple<String>> top10 =
    redisTemplate.opsForZSet().reverseRangeWithScores("game:leaderboard", 0, 9);

// 특정 플레이어 순위 조회
Long rank = redisTemplate.opsForZSet().reverseRank("game:leaderboard", "player:123");
```

**Redis가 적합한 이유:**
- Sorted Set의 ZADD/ZRANGE는 O(log N) 시간 복잡도
- RDBMS의 `ORDER BY score DESC`는 전체 스캔 + 정렬이라 느림
- 실시간 순위 업데이트가 빈번해도 성능 저하 없음

**실무 활용:**
- 게임 리더보드
- 소셜 미디어 "좋아요" Top 게시글
- 실시간 트렌딩 키워드

---

### 7.7 Geospatial (지리 공간 인덱싱) - 위치 기반 서비스

**개념:**
위도/경도 좌표를 저장하고, 특정 위치 근처의 장소를 빠르게 검색합니다.

**실전 예시:**
```java
// 위치 저장
redisTemplate.opsForGeo().add("restaurants",
    new Point(127.027619, 37.497942), "restaurant:1");

// 반경 5km 내 식당 검색
Circle circle = new Circle(new Point(127.0, 37.5), new Distance(5, Metrics.KILOMETERS));
GeoResults<GeoLocation<String>> results =
    redisTemplate.opsForGeo().radius("restaurants", circle);
```

**Redis가 적합한 이유:**
- Geohash 알고리즘으로 효율적인 공간 인덱싱
- GEORADIUS 명령어로 반경 검색이 밀리초 단위
- GEODIST로 두 지점 간 거리 계산

**실무 활용:**
- 배달 앱: 근처 음식점 검색
- 택시 앱: 가까운 기사 매칭
- 부동산 앱: 특정 위치 근처 매물

---

### 7.8 Atomic Operations with Lua Script - ✅ 프로젝트 적용됨

**개념:**
여러 Redis 명령어를 묶어서 원자적으로 실행하여, Lock 없이도 동시성 문제를 해결합니다.

**실전 예시 (본 프로젝트):**
```lua
-- 재고 차감 Lua Script
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock < tonumber(ARGV[1]) then
    return -1  -- 재고 부족
end
return redis.call('DECRBY', KEYS[1], ARGV[1])
```

**Redis가 적합한 이유:**
- Lua Script 전체를 "하나의 명령어"로 취급하여 원자성 보장
- 네트워크 왕복(Round-Trip) 최소화로 극한의 속도
- 복잡한 로직(조회 → 검증 → 수정)을 안전하게 실행

**실무 활용:**
- 재고 차감 (본 프로젝트)
- 선착순 쿠폰 발급
- 포인트 적립/차감
- 좋아요 중복 방지

**주의사항:**
- Lua Script는 Redis를 Block하므로 반드시 O(1) 연산만 수행
- 긴 루프나 복잡한 로직은 절대 금지 (전체 시스템 멈춤)

---

## 8. Redis 활용 사례 요약표

| 활용 사례 | 핵심 Redis 기능 | 적합한 상황 | 프로젝트 적용 |
|----------|---------------|-----------|------------|
| **Caching** | String, Hash, TTL | 반복적인 DB 조회 최소화 | - |
| **Distributed Lock** | SETNX, Pub/Sub | 분산 환경 동시성 제어 | ✅ Redisson RLock |
| **Message Queue (Pub/Sub)** | PUBLISH/SUBSCRIBE | 실시간 알림, 채팅 | - |
| **Message Queue (Streams)** | XADD, XREAD, Consumer Group | 이벤트 소싱, 마이크로서비스 통신 | - |
| **Session Storage** | String, Hash, TTL | 웹 세션 관리 | - |
| **Rate Limiting** | INCR, EXPIRE | API 요청 제한 | - |
| **Leaderboard** | Sorted Set (ZADD, ZRANGE) | 실시간 순위표 | - |
| **Geospatial** | Geohash (GEOADD, GEORADIUS) | 위치 기반 검색 | - |
| **Atomic Operations** | Lua Script | Lock-Free 동시성 제어 | ✅ 재고 차감 Script |

---

## 9. 결론: Redis는 "만능 도구"인가?

### "Redis는 Swiss Army Knife다"

Redis는 놀라울 정도로 다양한 용도로 활용됩니다. 하지만 **"모든 것을 Redis로 해결하려는 것"**은 위험합니다.

**Redis가 적합한 경우:**
- 빠른 속도가 중요한 경우 (레이턴시 < 10ms)
- 일시적 데이터 (캐시, 세션)
- 동시성 제어가 필요한 경우 (Lock, Lua Script)
- 실시간 처리 (Pub/Sub, Streams)

**Redis가 부적합한 경우:**
- 영속성이 절대적으로 중요한 경우 (Redis는 메모리 기반이라 휘발성)
- 복잡한 조인/트랜잭션이 필요한 경우 (RDBMS가 더 적합)
- 대용량 데이터 분석 (Data Warehouse가 더 적합)

### 우리 프로젝트의 교훈

본 프로젝트에서 Redis를 두 가지 방식으로 활용했습니다:
1. **Distributed Lock:** 안정성 중시, DB 정합성 유지
2. **Lua Script:** 속도 중시, Lock-Free 극한 성능

이는 **"상황에 맞는 도구 선택(Right Tool for the Job)"**의 중요성을 보여줍니다.

---

## 10. 다음 학습 주제

Redis의 다양한 활용 사례를 이해했다면, 다음 단계로 넘어갈 수 있습니다:

1. **Redis Cluster:** 대용량 데이터를 여러 노드로 분산 (Sharding)
2. **Redis Sentinel:** 고가용성 (High Availability) 구성
3. **Redis Persistence:** RDB vs AOF 영속화 전략
4. **Redis Security:** ACL, TLS 암호화
5. **Redis Modules:** RediSearch (전문 검색), RedisTimeSeries (시계열 데이터)

---

*Reference: Redis Documentation, "Redis in Action" by Josiah L. Carlson*

## Sources

- [Distributed Locks with Redis | Docs](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/)
- [Redis Use Cases Beyond Caching: Queues, Pub/Sub, and More](https://infobytes.guru/articles/redis-use-cases-beyond-caching.html)
- [Redis Use Cases - by Neo Kim - The System Design Newsletter](https://newsletter.systemdesign.one/p/redis-use-cases)
- [Redis Use Cases: Caching, Locking, Pub-Sub, Rate Limiting, and Session Storage](https://medium.com/@avraul7/redis-use-cases-caching-locking-pub-sub-rate-limiting-and-session-storage-with-node-js-dcdedb2998da)
- [Redis Beyond Caching: 8 Production Use Cases Every Developer Should Know](https://azimmemon2002.github.io/blog/redis-beyond-caching/)
- [The 6 Most Impactful Ways Redis is Used in Production Systems](https://blog.bytebytego.com/p/the-6-most-impactful-ways-redis-is)
- [Redis Deep Dive for System Design Interviews | Hello Interview](https://www.hellointerview.com/learn/system-design/deep-dives/redis)
- [Event-Driven Architecture Using Redis Streams](https://www.harness.io/blog/event-driven-architecture-redis-streams)
- [Redis Streams: Ultimate Guide to Real-Time Data Processing](https://engineeringatscale.substack.com/p/redis-streams-guide-real-time-data-processing)
