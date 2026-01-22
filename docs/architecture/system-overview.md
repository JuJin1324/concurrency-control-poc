# 시스템 전체 구성 (System Overview)

## 1. C4 Container Diagram

전체 시스템은 단일 **Spring Boot 애플리케이션**을 중심으로 데이터 정합성을 위한 **MySQL(Lock)**과 성능 최적화를 위한 **Redis(Distributed Lock/Lua Script)**로 구성됩니다.

```mermaid
C4Container
    title Container Diagram: Concurrency Control PoC

    Person(developer, "Developer", "시스템 개발 및 테스트 수행")
    Person(tester, "Load Tester (k6)", "대규모 트래픽 시뮬레이션")

    System_Boundary(c1, "Concurrency Control System") {
        Container(api, "Stock API", "Spring Boot 4.0, Java 21", "재고 차감 API 제공 (REST)")
        ContainerDb(mysql, "MySQL 8.0", "Database", "재고 데이터(Stock) 영속화 및 Pessimistic/Optimistic Lock 제공")
        ContainerDb(redis, "Redis 7.0", "Cache/Lock", "분산 락(Distributed Lock) 및 Lua Script 원자적 실행")
    }

    Rel(developer, api, "API 호출 및 검증", "HTTP/JSON")
    Rel(tester, api, "재고 차감 요청 (Concurrent)", "HTTP/JSON")
    
    Rel(api, mysql, "데이터 읽기/쓰기", "JDBC/JPA")
    Rel(api, redis, "Lock 획득/해제, Script 실행", "Redis Protocol (Lettuce/Redisson)")
```

---

## 2. 동시성 제어 시퀀스 다이어그램 (Concurrency Control Flows)

### 2.1. Pessimistic Lock (비관적 락)
**핵심 구현:** JPA의 `@Lock(LockModeType.PESSIMISTIC_WRITE)`를 사용하여 `SELECT ... FOR UPDATE` 쿼리를 실행합니다.

```mermaid
sequenceDiagram
    participant Client
    participant Service
    participant MySQL

    Client->>Service: 재고 차감 요청 (ID=1)
    Service->>MySQL: 트랜잭션 시작 (Target Isolation: READ_COMMITTED)
    Service->>MySQL: SELECT * FROM stock WHERE id=1 FOR UPDATE
    
    rect rgb(200, 255, 200)
        Note over MySQL: Row Lock 획득 (다른 트랜잭션 대기)
        MySQL-->>Service: Stock 엔티티 반환
        Service->>Service: 재고 수량 확인 (quantity > 0)
        Service->>Service: quantity = quantity - 1
        Service->>MySQL: UPDATE stock ...
    end
    
    Service->>MySQL: 트랜잭션 커밋 (Lock 해제)
    MySQL-->>Service: 성공
    Service-->>Client: 200 OK
```

### 2.2. Optimistic Lock (낙관적 락)
**핵심 구현:** JPA의 `@Version` 어노테이션을 사용하여 `UPDATE ... WHERE version = ?` 쿼리를 실행하고, 실패 시 애플리케이션 레벨에서 재시도(Retry)합니다.

```mermaid
sequenceDiagram
    participant Client
    participant Facade as OptimisticLockFacade
    participant Service
    participant MySQL

    Client->>Facade: 재고 차감 요청
    loop 재시도 (최대 3회)
        Facade->>Service: 차감 로직 실행 (RequiresNew)
        Service->>MySQL: SELECT * FROM stock WHERE id=1
        MySQL-->>Service: Stock (quantity=100, version=1)
        
        Service->>Service: 재고 감소
        Service->>MySQL: UPDATE stock SET quantity=99, version=2 WHERE id=1 AND version=1
        
        alt 업데이트 성공 (Rows > 0)
            MySQL-->>Service: 1 row updated
            Service-->>Facade: 성공 리턴
            Facade-->>Client: 200 OK (Break Loop)
        else 업데이트 실패 (충돌, Rows = 0)
            MySQL-->>Service: 0 row updated
            Service-->>Facade: OptimisticLockException 발생
            Note over Facade: 50ms 대기 후 재시도
        end
    end
```

### 2.3. Redis Distributed Lock (분산 락)
**핵심 구현:** Redisson 라이브러리의 `RLock`을 사용하여 Pub/Sub 기반의 효율적인 락을 구현합니다.

```mermaid
sequenceDiagram
    participant Client
    participant Service
    participant Redis
    participant MySQL

    Client->>Service: 재고 차감 요청
    Service->>Redis: RLock.tryLock(waitTime=5s, leaseTime=3s)
    
    alt 락 획득 성공
        Redis-->>Service: OK (Lock Acquired)
        Service->>MySQL: 트랜잭션 시작
        Service->>MySQL: SELECT * FROM stock WHERE id=1
        MySQL-->>Service: Stock 데이터
        Service->>Service: 재고 감소
        Service->>MySQL: UPDATE stock ...
        Service->>MySQL: 트랜잭션 커밋
        Service->>Redis: unlock()
        Redis-->>Service: Lock Released
        Service-->>Client: 200 OK
    else 락 획득 실패 (Timeout)
        Redis-->>Service: Fail
        Service-->>Client: 429 Too Many Requests
    end
```

### 2.4. Redis Lua Script (원자적 연산)
**핵심 구현:** Redis의 단일 스레드 특성과 Lua Script의 원자성을 활용하여, Lock 획득 과정 없이 재고를 차감합니다.

```mermaid
sequenceDiagram
    participant Client
    participant Service
    participant Redis
    participant MySQL

    Client->>Service: 재고 차감 요청
    
    Note over Service: Lua Script 실행 (sha1)
    Service->>Redis: EVAL sha1 keys=["stock:1"] argv=["1"]
    
    Note over Redis: [Redis 내부 실행]<br/>1. local q = tonumber(redis.call('GET', KEYS[1]))<br/>2. if q < ARGV[1] then return -1 end<br/>3. return redis.call('DECRBY', KEYS[1], ARGV[1])
    
    alt 차감 성공 (Result >= 0)
        Redis-->>Service: 남은 재고 반환
        Service->>MySQL: (비동기) DB 업데이트 이벤트 발행
        Service-->>Client: 200 OK
    else 재고 부족 (Result == -1)
        Redis-->>Service: -1 반환
        Service-->>Client: 400 Bad Request (Out of Stock)
    end
```

---

## 3. 부하 테스트 아키텍처

k6를 사용하여 동시 사용자(VU)를 시뮬레이션하고, 각 구현체의 **TPS(처리량)**와 **Latency(지연 시간)**를 측정합니다.

```mermaid
graph TB
    subgraph "Load Generator"
        K6["k6 Container<br/>(vus: 100~10000)"]
    end

    subgraph "Application"
        API["Spring Boot API"]
    end

    subgraph "Storage"
        MySQL["MySQL<br/>(Database)"]
        Redis["Redis<br/>(Lock/Cache)"]
    end

    subgraph "Monitoring (Optional)"
        Prom["Prometheus"]
        Graf["Grafana"]
    end

    K6 -->|HTTP POST /api/stock/deduct| API
    API -->|JDBC| MySQL
    API -->|Redis Protocol| Redis
    API -.->|"Metrics (Actuator)"| Prom
    Prom -.-> Graf
```

### 테스트 시나리오
1.  **Warm-up:** VUs 10 -> 100 (30초) - JVM 워밍업
2.  **Load:** VUs 1000 (1분) - TPS 및 Latency 측정 (안정 구간)
3.  **Stress:** VUs 5000+ (1분) - 한계 지점(Fail Point) 확인 및 병목 지점 파악
4.  **Cool-down:** VUs 0 (30초) - 리소스 회수 확인
