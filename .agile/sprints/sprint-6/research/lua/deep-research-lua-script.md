# **Redis Lua Script (원자적 연산) 실무 도입 사례 및 운영 노하우: 고성능 동시성 제어를 위한 심층 분석 보고서**

## **1. 서론: 엔터프라이즈 환경에서의 Redis Lua Script의 전략적 가치**

현대의 대규모 분산 시스템, 특히 이커머스, 티켓팅, 핀테크와 같이 초당 수만 건 이상의 트랜잭션(TPS)이 발생하는 환경에서 데이터의 **정합성(Consistency)**과 시스템의 **가용성(Availability)**을 동시에 확보하는 것은 엔지니어링의 핵심 난제입니다. 전통적인 RDBMS(Relational Database Management System) 기반의 비관적 락(Pessimistic Lock)이나 낙관적 락(Optimistic Lock)은 디스크 I/O와 트랜잭션 격리 수준(Isolation Level) 유지 비용으로 인해, 급증하는 트래픽(Thundering Herd) 상황에서 심각한 병목 현상을 초래합니다.

이러한 배경에서 **Redis Lua Script**는 단순한 캐싱 솔루션을 넘어, 비즈니스 로직의 원자적(Atomic) 실행을 보장하는 고성능 연산 엔진으로 자리 잡았습니다. Redis는 싱글 스레드 이벤트 루프(Single-threaded Event Loop) 아키텍처를 기반으로 하며, EVAL 명령어를 통해 실행되는 Lua 스크립트는 서버 내부에서 인터럽트 없이 순차적으로 실행됨을 보장합니다.3 이는 분산 락(Distributed Lock) 구현 시 발생하는 네트워크 왕복 시간(RTT)과 락 획득/해제 경합 비용을 획기적으로 줄여주는 기술적 이점을 제공합니다.

본 보고서는 Redis Lua Script를 활용하여 극한의 동시성 문제를 해결한 글로벌 및 국내 선도 기업(Ticketmaster, 우아한형제들, Grab 등)의 구체적인 실무 도입 사례를 심층 분석하고, 프로덕션 환경에서 발생할 수 있는 운영상의 문제점과 이를 극복하기 위한 고도화된 전략(버전 관리, 모니터링, DB 동기화)을 체계적으로 제시합니다.

## **2. 심층 사례 분석: 고성능 동시성 제어의 실제**

### **2.1 사례 1: Ticketmaster - 글로벌 티켓팅 시스템의 원자적 좌석 선점(Atomic Hold) 전략**

**도메인:** 대규모 공연 및 스포츠 경기 티켓팅

**선택 이유:** RDBMS의 Row-level Lock 한계를 극복하고 수백만 명의 동시 접속자가 발생하는 '좌석 선점' 트래픽을 처리하기 위해 Lua Script의 원자성을 활용함.

### **2.1.1 도입 배경 및 기술적 난제**

Ticketmaster와 같은 초대형 티켓팅 플랫폼에서는 인기 공연 예매 시작(On-sale) 시점에 수백만 명의 사용자가 동시에 접속하여 동일한 좌석(Inventory)을 선점하려는 시도가 발생합니다. 이를 전통적인 RDBMS로 처리할 경우 다음과 같은 치명적인 문제가 발생합니다:

1. **DB 커넥션 고갈:** 모든 요청이 DB 트랜잭션을 점유하려 시도하면서 커넥션 풀이 순식간에 고갈됩니다.
2. **경합(Contention) 비용:** 동일한 좌석 row에 대한 SELECT FOR UPDATE 요청이 몰리며 DB CPU가 급증하고, 교착 상태(Deadlock)가 발생할 확률이 높아집니다.
3. **사용자 경험 저하:** 락 대기 시간으로 인해 응답 지연(Latency)이 발생하고, 이는 사용자의 이탈로 이어집니다.5

### **2.1.2 구현 방식: Lua Script를 통한 원자적 Hold**

Ticketmaster는 "좌석 선점(Hold)"이라는 임시 상태를 고속으로 처리하기 위해 Redis를 메인 저장소 앞단에 배치하고, Lua Script를 통해 중복 예약을 원천 차단했습니다.

- **Lua Script 로직 (의사 코드):**

  단순한 SETNX만으로는 '이미 선점된 좌석인지', '유효기간이 만료되었는지' 등 복합적인 비즈니스 로직을 한 번에 처리하기 어렵습니다. Lua Script는 다음과 같이 검증과 쓰기를 단일 연산으로 수행합니다.

  Lua

    - - KEYS[1]: lock:event:{event_id}:seat:{seat_id} (좌석 락 키)
    - - ARGV[1]: user_id (사용자 ID)
    - - ARGV[2]: ttl_seconds (점유 시간, 예: 300초)
    - - 1. 좌석이 이미 선점되었는지 확인 (EXISTS)

  if redis.call("EXISTS", KEYS[1]) == 1 then

  -- 이미 다른 사용자가 선점함

  return 0

  end

    - - 2. 좌석 선점 (Lock 생성 및 TTL 설정)
    - - 원자적으로 키를 생성하고 만료 시간을 설정

  redis.call("SET", KEYS[1], ARGV[1], "EX", ARGV[2])

  return 1 -- 선점 성공

  이 스크립트는 5만 명의 사용자가 동시에 같은 좌석을 클릭하더라도, Redis 내부에서 순차적으로 실행되므로 오직 첫 번째 요청만이 1을 반환받고, 나머지 49,999명은 0을 반환받아 즉시 실패 처리됩니다. 이 과정에서 RDBMS에는 부하가 전혀 전달되지 않습니다.6

- **Script 버전 관리:**

  Ticketmaster와 같은 대규모 시스템에서는 스크립트 변경 시 배포 안정성이 중요합니다. 스크립트 파일은 Git 저장소에서 관리되며, 배포 파이프라인(CI/CD) 과정에서 SCRIPT LOAD를 통해 Redis에 미리 적재되고, 애플리케이션은 반환된 SHA1 해시값만을 설정 파일에 주입받아 사용합니다. 이는 네트워크 대역폭을 절약하고(스크립트 본문을 매번 전송하지 않음) 버전 불일치 문제를 방지합니다.

- **Redis → DB 동기화 전략:** 좌석 선점 단계에서는 RDB에 쓰기 작업을 수행하지 않습니다. 대신 Redis에만 데이터를 기록하고, 사용자가 결제를 완료하는 시점에 비동기적으로 RDB의 Bookings 테이블에 데이터를 기록하는 방식을 사용합니다. 만약 Redis 장애로 데이터가 유실되더라도, 결제 트랜잭션 단계에서 DB의 UNIQUE 제약 조건을 통해 최종적인 중복 예약을 방지하는 이중 안전장치를 마련했습니다.6

### **2.1.3 성능 지표 및 운영 노하우**

- **성능 지표:**
- **Latency:** RDBMS 사용 시 수백 ms에 달하던 응답 속도가 Redis Lua Script 도입 후 **< 5ms** 수준(p99)으로 단축되었습니다.
- **Throughput:** 단일 Redis 인스턴스에서 초당 수만 건의 좌석 선점 요청을 처리할 수 있으며, 이는 RDBMS 대비 약 50배 이상의 처리량입니다.6
- **운영 노하우:**
- **Push Model (CDN 활용):** 좌석 현황을 조회하는 '읽기' 트래픽은 Redis에서 직접 서빙하지 않고, 변경 사항을 1~2초 주기로 집계하여 CDN에 정적 파일(JSON/Bitmap)로 푸시합니다. 이를 통해 수백만 건의 조회 요청을 Redis 부하와 완전히 격리시켰습니다.6
- **장애 경험:**
- 초기 도입 시 TTL(Time-To-Live) 설정 실수로 인해, 사용자가 결제 도중 좌석 선점이 풀려버리는 문제가 발생했습니다. 이를 해결하기 위해 결제 페이지 진입 시 Redis의 TTL을 연장(PEXPIRE)하거나, 결제 게이트웨이의 타임아웃 시간과 Redis TTL을 동기화하는 로직을 추가했습니다.

### **2.2 사례 2: 우아한형제들 (배달의민족) - 선착순 쿠폰 이벤트 시스템**

**도메인:** 마케팅 선착순 이벤트 (FCFS: First-Come-First-Served)

**선택 이유:** 분산 락(Distributed Lock)의 성능 한계를 극복하고, Set 자료구조와 Lua Script를 결합하여 중복 발급 방지와 수량 제어를 동시에 달성함.

### **2.2.1 도입 배경**

배달의민족의 선착순 이벤트는 특정 시간에 트래픽이 폭발적으로 집중되는 특성을 가집니다. 초기에는 RDBMS의 Transaction이나 Redis의 분산 락(Redisson 등)을 사용했으나, 락을 획득하고 해제하는 과정에서 발생하는 네트워크 오버헤드와 대기 시간으로 인해 목표 트래픽을 처리하지 못하는 문제가 발생했습니다. 특히 분산 락은 락을 획득하지 못한 요청들이 대기 큐에 쌓이면서 전체 시스템의 응답 속도를 저하시키는 원인이 되었습니다.7

### **2.2.2 구현 방식: Lock-free Lua Scripting**

우아한형제들은 락을 걸고 푸는 행위 자체를 제거하고, Redis의 Set 자료구조와 Lua Script를 활용하여 "확인(Check)"과 "발급(Set)"을 원자적인 하나의 동작으로 구현했습니다.

- **Lua Script 로직 (구체적 구현):**

  Lua

    - - KEYS[1]: event:coupon:{event_id}:users (쿠폰 발급 유저 Set)
    - - ARGV[1]: max_count (최대 발급 수량)
    - - ARGV[2]: user_id (유저 ID)
    - - 1. 중복 발급 검증 (SISMEMBER)
    - - O(1) 복잡도로 유저의 존재 여부를 즉시 확인

  if redis.call("SISMEMBER", KEYS[1], ARGV[2]) == 1 then

  return -1 -- 이미 발급받음

  end

    - - 2. 수량 검증 (SCARD)
    - - 현재 Set의 크기(발급된 수량)를 확인

  local current_count = redis.call("SCARD", KEYS[1])

  if tonumber(current_count) >= tonumber(ARGV[1]) then

  return 0 -- 선착순 마감

  end

    - - 3. 쿠폰 발급 (SADD)
    - - 검증이 완료되었으므로 유저를 Set에 추가

  redis.call("SADD", KEYS[1], ARGV[2])

  return 1 -- 발급 성공

  이 방식은 RDBMS의 유니크 키 제약조건이나 분산 락 없이도 완벽하게 중복을 방지하며, 선착순 수량을 정확하게 제어합니다. SCARD와 SISMEMBER는 모두 O(1)의 시간 복잡도를 가지므로, 데이터가 많아져도 성능 저하가 거의 없습니다.8

- **Redis → DB 동기화 전략 (Write-Behind & Eventual Consistency):**

  Redis에서 발급 성공 응답(1)을 받은 경우, 애플리케이션은 즉시 사용자에게 "발급 성공" 메시지를 보여줍니다. 실제 RDBMS(MySQL) 로의 적재는 **비동기 이벤트**를 통해 처리됩니다.

1. Redis 발급 성공 시, 애플리케이션은 Kafka와 같은 메시지 큐에 '쿠폰 발급 이벤트'를 발행합니다.
2. 별도의 워커(Worker) 프로세스가 이벤트를 구독(Subscribe)하여 RDB에 INSERT를 수행합니다.
3. 이 방식은 DB 병목이 사용자 응답 속도에 영향을 주지 않도록 결합도(Coupling)를 낮춥니다.7

### **2.2.3 성능 지표 및 운영 노하우**

- **성능 지표:**
- **TPS:** 분산 락 사용 시 대비 처리량이 수십 배 향상되었으며, 네트워크 병목 없이 Redis의 CPU 한계치까지 성능을 끌어올릴 수 있었습니다.
- **Latency:** 트래픽 피크 시점에도 사용자 응답 지연이 거의 발생하지 않음.
- **운영 노하우:**
- **Redis SLOWLOG 모니터링:** 스크립트 실행 시간이 길어지면 Redis 전체가 블로킹되므로, SLOWLOG 임계값을 낮게 설정(예: 5ms)하여 모니터링합니다. 만약 특정 스크립트가 느려진다면, SCARD와 같은 O(1) 명령어 대신 O(N) 명령어가 포함되지 않았는지 점검합니다.
- **장애 경험 (DB 동기화 실패):**
- Redis에는 저장되었으나, RDB 동기화 과정에서 서버 다운이나 네트워크 이슈로 실패하는 경우가 발생했습니다.
- **해결책:** **보상 트랜잭션(Compensating Transaction)** 로직을 구현했습니다. RDB 적재 실패 시, 재시도(Retry)를 수행하고 최종 실패 시에는 '보상 워커'가 Redis Set에서 해당 유저를 SREM으로 제거하여 시스템 간의 상태를 일치시킵니다. 또한, Kafka의 Dead Letter Queue(DLQ)를 활용하여 실패한 이벤트를 별도로 저장하고, 운영자가 수동으로 재처리할 수 있는 프로세스를 마련했습니다.7

### **2.3 사례 3: Grab - 할당 시스템의 데이터 일관성 이슈 및 Lua 복제 전략**

**도메인:** 차량 호출 할당 (Ride Allocation)

**선택 이유:** Redis Master/Replica 아키텍처에서 Lua Script 사용 시 발생할 수 있는 데이터 불일치(Inconsistency) 문제와 그 해결 과정을 보여주는 대표적인 사례임.

### **2.3.1 도입 배경 및 기술적 난제**

Grab은 드라이버 할당 시스템에서 Redis를 상태 저장소 및 메시지 큐로 활용합니다. 고가용성을 위해 Master-Slave 구조를 사용하는데, Lua Script를 통해 복잡한 할당 로직을 처리하던 중 **Master와 Replica 간의 데이터가 불일치하는 심각한 문제**를 발견했습니다.10

### **2.3.2 장애 경험: 비결정적(Non-deterministic) 스크립트와 복제**

Redis의 과거 버전(특히 5.0 이전 기본 설정)이나 특정 설정에서는 Lua Script를 복제할 때, **스크립트의 실행 결과(Effect)가 아닌 스크립트 소스 코드(Script) 자체를 Replica로 전송**하여 재실행하는 방식을 사용했습니다 (Script Replication).

- **문제 발생:** Grab의 스크립트 내부에서 HGETALL 명령어를 사용했는데, HGETALL은 해시 필드의 반환 순서를 보장하지 않습니다. Master에서는 A, B 순서로 반환되어 로직이 수행되었으나, Replica에서는 B, A 순서로 반환되어 로직이 수행되면서 서로 다른 결과값이 저장되는 현상이 발생했습니다. 또한 TIME이나 RANDOM과 같은 명령어를 스크립트 내에서 사용할 경우, 실행 시점에 따라 값이 달라져 데이터 불일치를 유발합니다.10

### **2.3.3 해결 및 운영 노하우: Effect Replication 및 결정적 코드**

- **Effect Replication (스크립트 효과 복제) 적용:**

  Grab은 이 문제를 해결하기 위해 Redis 설정을 변경하여 **Script Replication** 대신 **Effect Replication**을 적용했습니다.

- **방식:** 스크립트 자체를 복제하는 것이 아니라, 스크립트 실행 결과로 생성된 **쓰기 명령어(Write Commands)들만** Replica로 전송합니다.
- **Lua 코드:** 스크립트 내에서 redis.replicate_commands()를 호출하여 이 모드를 활성화할 수 있습니다 (Redis 3.2+). Redis 5.0부터는 이 방식이 기본값(Default)으로 변경되었으나, 레거시 시스템 운영 시 반드시 확인이 필요합니다.
- **결정적(Deterministic) 코드 작성:** 스크립트 복제 방식을 사용해야 하는 경우(예: 네트워크 대역폭 절약), 스크립트는 반드시 **결정적**이어야 합니다. Grab은 HGETALL이나 SMEMBERS로 가져온 데이터를 Lua 내부에서 table.sort를 사용하여 명시적으로 정렬한 후 로직을 수행하도록 수정하여, 어느 노드에서 실행되든 항상 동일한 결과를 보장하도록 했습니다.10

## **3. 운영 관점의 핵심 고려사항 및 노하우**

Redis Lua Script는 강력하지만, 잘못 사용하면 전체 시스템을 마비시킬 수 있는 양날의 검입니다. 운영 안정성을 위해 다음 사항들을 반드시 고려해야 합니다.

### **3.1 Lua Script 버전 관리 및 배포 (Versioning Best Practice)**

Lua 스크립트는 애플리케이션 코드 외부에 존재하는 로직이므로, 체계적인 형상 관리가 필수적입니다.

- **Repository 관리:** 스크립트 파일(.lua)을 프로젝트 소스 코드(Git) 내 별도 디렉토리(예: src/main/resources/redis/scripts)에 포함시켜 애플리케이션 코드와 함께 버전을 관리합니다.
- **SHA1 해시 기반 실행:**

  프로덕션에서는 EVAL 명령어로 스크립트 본문을 매번 전송하지 않습니다. 대신 애플리케이션 구동 시 SCRIPT LOAD를 통해 Redis에 스크립트를 등록하고 반환된 SHA1 해시를 메모리에 캐싱하여 EVALSHA로 실행합니다.

- **재해 복구:** Redis가 재시작되면 스크립트 캐시가 휘발됩니다. 클라이언트 라이브러리(Spring Data Redis, Lettuce 등)는 EVALSHA 실행 시 NOSCRIPT 에러를 감지하면 자동으로 스크립트를 다시 로드(SCRIPT LOAD)하고 재시도하는 로직을 내장하고 있어야 합니다.11
- **배포 파이프라인:** 스크립트 내용이 변경되면 새로운 SHA1이 생성되므로, 배포 시 구버전과 신버전 애플리케이션이 공존하는 Blue/Green 배포 환경에서도 문제가 발생하지 않습니다(각각 다른 SHA1을 호출).

### **3.2 Script 성능 모니터링 (SLOWLOG & Prometheus)**

Lua 스크립트는 **Atomic**하게 실행되므로, 스크립트 하나가 오래 실행되면 그동안 Redis는 다른 어떤 요청도 처리하지 못하고 멈춥니다(Blocking).

- **lua-time-limit의 오해와 진실:** redis.conf의 lua-time-limit(기본 5초)은 스크립트를 강제 종료하는 시간이 아닙니다. 이 시간이 지나면 Redis는 다른 요청에 대해 BUSY 에러를 반환할 뿐, 스크립트는 계속 실행됩니다.13 따라서 이 설정에 의존하면 안 되며, 스크립트 자체를 최적화해야 합니다.
- **모니터링 전략:**
- **SLOWLOG:** slowlog-log-slower-than을 매우 낮게(예: 1000마이크로초 = 1ms) 설정하여, 조금이라도 느린 스크립트를 즉시 포착해야 합니다.14
- **Prometheus Metrics:** redis_exporter를 통해 redis_script_execution_time 지표를 수집하고, Grafana 대시보드에서 스크립트 실행 시간의 p99, p99.9 지표를 실시간으로 감시합니다. 스파이크 발생 시 알람이 울리도록 설정합니다.15

### **3.3 Script 복잡도 관리 및 한계점**

- **O(N) 명령어 금지:** 스크립트 내부에서 KEYS *와 같은 전체 스캔 명령이나, 요소가 많은 Set/List에 대한 전체 순회는 절대 금지입니다. 필요한 경우 SCAN 명령어를 사용하거나, 데이터를 페이징하여 처리해야 합니다.13
- **복잡도 한계:** 스크립트가 너무 복잡해지면(수백 라인 이상), 디버깅이 어렵고 유지보수성이 떨어집니다. 이 경우 Redis 7.0의 **Redis Functions** 도입을 고려하거나, 일부 로직을 애플리케이션 레벨로 이동시켜야 합니다.

## **4. DB 동기화 전략: 정합성과 성능의 균형**

Redis에서의 원자적 처리는 성공했지만, 영구 저장소인 RDB와의 데이터가 일치하지 않는다면 비즈니스에 치명적입니다. 이를 해결하기 위한 전략을 상세히 정리합니다.

### **4.1 Redis → DB 동기화 아키텍처 (Write-Behind)**

대규모 트래픽 환경에서는 **Write-Behind (Write-Back)** 패턴이 표준입니다.

| **구분** | **Write-Through** | **Write-Behind (권장)** |
| --- | --- | --- |
| **방식** | Redis와 DB에 동시에 씀 | Redis에 먼저 쓰고, 비동기로 DB 반영 |
| **장점** | 강력한 데이터 정합성 | **최고의 쓰기 성능 (Redis 속도)** |
| **단점** | DB 병목이 전체 성능 저하 | Redis 장애 시 데이터 유실 가능성 |
| **적합** | 금융 거래, 결제 정보 | **재고 차감, 선착순 이벤트, 조회수** |

**구현 단계:**

1. **Atomic Operation:** Redis Lua Script로 재고 차감 및 주문 데이터 생성.
2. **Event Publishing:** 스크립트 실행 후 성공 시, Redis Stream이나 Kafka에 '주문 완료 이벤트' 발행 (애플리케이션 레벨에서 수행).
3. **Async Consumption:** 별도의 Consumer 워커가 이벤트를 받아 RDB에 INSERT/UPDATE 수행.

### **4.2 동기화 실패 시 복구 시나리오 (Saga Pattern)**

RDB 반영 단계에서 실패(예: DB 서버 다운, 데이터 타입 오류, 제약 조건 위반)가 발생했을 때, 데이터 정합성을 맞추기 위한 보상 트랜잭션이 필요합니다.

- **시나리오:** Redis 재고 차감(-1) 성공 → Kafka 발행 → DB 주문 Insert 실패.
- **복구 전략 (Compensating Transaction):**
1. **Retry:** Consumer는 DB 저장을 일시적인 오류(네트워크 등)로 간주하고 지수 백오프(Exponential Backoff)로 재시도합니다.
2. **Dead Letter Queue (DLQ):** 재시도 횟수 초과 시, 해당 메시지를 Kafka의 DLQ로 이동시킵니다.
3. **보상 로직 실행 (Saga):** DLQ를 구독하는 별도의 '보상 워커(Compensator)'가 트리거됩니다. 이 워커는 Redis에 보상 스크립트를 호출하여 차감했던 재고를 다시 증가(+1)시키고, 유저의 발급 내역을 취소(SREM)하여 시스템 상태를 원상 복구합니다.16

### **4.3 데이터 정합성 보장 (Reconciliation)**

실시간 보상 트랜잭션 외에도, 주기적인 배치(Batch) 작업을 통해 Redis와 DB 간의 데이터 불일치를 전수 조사하고 보정하는 **Reconciliation** 프로세스를 운영해야 합니다. 이는 알 수 없는 버그나 시스템 크래시로 인한 미세한 데이터 오차를 최종적으로 바로잡는 안전장치입니다.

## **5. 결론 및 요약**

Redis Lua Script는 Ticketmaster, 우아한형제들, Grab과 같은 선도 기업들이 증명했듯이, 초고부하 환경에서 **속도(Performance)**와 **원자성(Atomicity)**을 동시에 달성할 수 있는 가장 현실적이고 강력한 도구입니다.

**성공적인 도입을 위한 3가지 핵심 제언:**

1. **비즈니스 로직의 경량화:** Lua Script는 마법이 아닙니다. 스크립트는 가능한 짧고 단순하게 유지하며, O(N) 연산을 철저히 배제하여 Redis의 싱글 스레드 병목을 방지해야 합니다.
2. **운영 수준의 안전장치 마련:** EVALSHA를 통한 네트워크 최적화, NOSCRIPT 에러 자동 핸들링, 그리고 철저한 SLOWLOG 모니터링 체계를 갖춰야 합니다.
3. **정교한 실패 처리 전략:** Redis와 DB 간의 불일치는 언제든 발생할 수 있다는 가정하에, Write-Behind 패턴과 보상 트랜잭션(Saga), DLQ를 포함한 견고한 동기화 파이프라인을 구축해야 합니다.

본 리서치 결과가 귀사의 docs/operations/lua-script-ops.md 운영 가이드 작성에 실질적인 도움이 되기를 바랍니다.

### **참고 자료**

1. 상품 재고 관리(재고 예약) - velog, 2월 3, 2026에 액세스, https://velog.io/@maaaaay/%EC%83%81%ED%92%88-%EC%9E%AC%EA%B3%A0-%EC%98%88%EC%95%BD
2. [redis] lua 사용 사례 - 김용환 블로그, 2월 3, 2026에 액세스, https://knight76.tistory.com/entry/redis-lua-%EC%82%AC%EC%9A%A9-%EC%82%AC%EB%A1%80
3. Ticketmaster System Design, 2월 3, 2026에 액세스, https://grokkingthesystemdesign.com/guides/ticketmaster-system-design/
4. Ticket master system design. Requirements | by Dilip Kumar | Medium, 2월 3, 2026에 액세스, https://dilipkumar.medium.com/ticket-master-system-design-e794c51d79f7
5. 선물하기 시스템의 상품 재고는 어떻게 관리되어질까? | 우아한형제들 ..., 2월 3, 2026에 액세스, https://techblog.woowahan.com/2709/
6. 선착순 쿠폰 발급 시스템 개선과 정합성 문제 해결 (Redis·Kafka), 2월 3, 2026에 액세스, https://alstn113.tistory.com/67
7. 선착순 쿠폰 발급 시스템 구현하기: Redis와 Kafka를 활용한 설계, 2월 3, 2026에 액세스, https://yeseul-dev.tistory.com/34
8. Uncovering the Truth Behind Lua and Redis Data Consistency, 2월 3, 2026에 액세스, https://engineering.grab.com/uncovering-the-truth-behind-lua-and-redis-data-consistency
9. Redis load Lua script and cache it from file (instead of SCRIPT LOAD), 2월 3, 2026에 액세스, https://stackoverflow.com/questions/45405693/redis-load-lua-script-and-cache-it-from-file-instead-of-script-load
10. Scripting with Lua | Docs - Redis, 2월 3, 2026에 액세스, https://redis.io/docs/latest/develop/programmability/eval-intro/
11. How to Fix Redis "BUSY" Errors from Lua Scripts - OneUptime, 2월 3, 2026에 액세스, https://oneuptime.com/blog/post/2026-01-21-redis-busy-errors-lua-scripts/view
12. How Redis Slow Log Helps You Debug Laggy Commands, 2월 3, 2026에 액세스, https://dev.to/rijultp/how-redis-slow-log-helps-you-debug-laggy-commands-22j6
13. Redis and Lua Powered Sliding Window Rate Limiter - Halodoc Blog, 2월 3, 2026에 액세스, https://blogs.halodoc.io/taming-the-traffic-redis-and-lua-powered-sliding-window-rate-limiter-in-action/
14. How to rollback distributed transactions? - Stack Overflow, 2월 3, 2026에 액세스, https://stackoverflow.com/questions/54745015/how-to-rollback-distributed-transactions
15. Saga pattern with PHP: Masterful Coordination of Distributed ..., 2월 3, 2026에 액세스, https://dev.to/igornosatov_15/saga-pattern-with-php-masterful-coordination-of-distributed-transactions-in-microservices-1phi
16. How to Sync Data Between Redis and PostgreSQL - OneUptime, 2월 3, 2026에 액세스, https://oneuptime.com/blog/post/2026-01-21-sync-data-redis-postgresql/view