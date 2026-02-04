# **엔터프라이즈 환경에서의 비관적 락(Pessimistic Lock) 운용 전략: 실무 사례 분석 및 고가용성 아키텍처**

## **1. 서론: 동시성 제어의 패러다임 변화와 비관적 락의 재조명**

디지털 트랜잭션의 규모가 폭발적으로 증가하는 현대의 엔터프라이즈 환경에서, 데이터 무결성(Data Integrity)을 보장하는 것은 시스템 설계의 가장 타협할 수 없는 원칙이다. 마이크로서비스 아키텍처(MSA)와 클라우드 네이티브 환경의 확산으로 인해 데이터베이스가 물리적으로 분산되면서, 애플리케이션 레벨의 분산 락(Distributed Lock)이나 낙관적 락(Optimistic Lock)이 유행처럼 번져나갔다. 그러나 금융 원장(Ledger), 이커머스 재고 관리(Inventory Management), 물류 배송 등 데이터의 정확성이 금전적 가치와 직결되는 미션 크리티컬(Mission Critical) 도메인에서, 데이터베이스 엔진 레벨에서 물리적인 정합성을 강제하는 **비관적 락(Pessimistic Lock)**, 즉 SELECT... FOR UPDATE 구문의 중요성은 결코 퇴색되지 않았다.

비관적 락은 "데이터 충돌은 반드시 발생한다"는 보수적인 가정하에 트랜잭션의 시작 시점부터 자원을 선점하는 방식이다. 이는 낙관적 락이 가진 재시도(Retry) 비용의 불확실성을 제거하고, 분산 락이 가진 구현 복잡도와 네트워크 오버헤드를 줄여주는 강력한 수단이 된다. 특히 고도로 최적화된 RDBMS(MySQL InnoDB, PostgreSQL)의 내부 락 메커니즘을 적절히 활용할 경우, 외부 의존성 없이도 가장 신뢰할 수 있는 동시성 제어 시스템을 구축할 수 있다.1

본 리서치 보고서는 2020년 이후 주요 테크 기업들(토스증권, 뱅크샐러드, 위키미디어 등)의 실제 도입 및 장애 사례를 심층 분석함으로써, 비관적 락이 단순한 SQL 문법을 넘어 어떻게 아키텍처의 핵심 컴포넌트로 작동하는지를 규명한다. 또한 데드락(Deadlock) 회피, 타임아웃(Timeout) 전략, 모니터링 지표 설정 등 운영 엔지니어가 현업에서 즉시 적용 가능한 수준의 구체적인 가이드를 제공하여, 이론과 실무의 간극을 메우는 것을 목표로 한다.

## **2. 데이터베이스 엔진별 락 메커니즘의 심층 분석**

실무에서의 비관적 락 운영 실패는 대부분 데이터베이스 엔진 내부 동작에 대한 이해 부족에서 기인한다. SELECT... FOR UPDATE는 표준 SQL이지만, 이를 처리하는 스토리지 엔진의 방식은 MySQL과 PostgreSQL이 판이하게 다르며, 이 차이가 운영의 성패를 가른다.

### **2.1 MySQL InnoDB의 인덱스 기반 락킹(Index-Record Locking)**

MySQL InnoDB 엔진에서 가장 빈번하게 발생하는 오해는 락이 '물리적인 레코드(Row)' 자체에 걸린다는 생각이다. 하지만 InnoDB는 레코드가 아닌 **인덱스(Index)**에 락을 건다. 이는 운영상 치명적인 사이드 이펙트를 유발할 수 있는 구조적 특성이다.4

- **클러스터링 인덱스(Clustered Index)와 X-Lock:** Primary Key를 통해 명확하게 단일 행을 조회할 경우(WHERE id = 1 FOR UPDATE), InnoDB는 해당 PK 인덱스 레코드에 배타 락(Exclusive Lock, X-Lock)을 건다. 이는 가장 이상적인 형태이며 경합을 최소화한다.
- **갭 락(Gap Lock)과 넥스트 키 락(Next-Key Lock):** 가장 심각한 성능 저하는 범위 검색이나 Non-Unique 인덱스를 사용할 때 발생한다. InnoDB는 REPEATABLE READ 격리 수준(Isolation Level)에서 팬텀 리드(Phantom Read)를 방지하기 위해, 실제 존재하지 않는 레코드 사이의 간격(Gap)까지 잠그는 갭 락을 수행한다.
- *시나리오:* SELECT * FROM orders WHERE status = 'PENDING' FOR UPDATE를 실행하면, 현재 PENDING 상태인 주문뿐만 아니라, 향후 PENDING 상태로 들어올 수 있는 인덱스 범위 전체가 잠길 수 있다. 이는 다른 트랜잭션의 INSERT 작업까지 블로킹하여 시스템 전체의 처리량(Throughput)을 급격히 떨어뜨린다.5
- **락 에스컬레이션(Lock Escalation)의 부재:** Oracle이나 SQL Server와 달리, InnoDB는 메모리 절약을 위해 다수의 Row Lock을 Table Lock으로 승격시키지 않는다. 대신 락 정보가 메모리(Lock Struct)에 상주하므로, 수백만 건의 레코드를 잠그더라도 락 자체의 오버헤드는 적지만, 락 관리를 위한 메모리 사용량이 증가하고 데드락 감지 비용이 상승한다.3

### **2.2 PostgreSQL의 세분화된 락 모드(Fine-grained Lock Modes)**

PostgreSQL은 MySQL에 비해 훨씬 다층적인 락 모드를 제공하여, 개발자가 비즈니스 로직의 특성에 맞춰 동시성을 미세 조정할 수 있게 한다. 이는 단순한 FOR UPDATE의 남용을 막고 시스템 효율을 높이는 핵심 기제다.7

| **락 모드 (Lock Mode)** | **설명 및 운영적 의미** | **호환성 및 충돌** |
| --- | --- | --- |
| **FOR UPDATE** | 가장 강력한 락. 해당 행의 삭제(DELETE) 및 모든 컬럼의 업데이트를 차단한다. 완전한 독점권을 보장한다. | 모든 모드와 충돌 |
| **FOR NO KEY UPDATE** | **[실무 권장]** 키(Primary Key, Unique Key) 컬럼을 제외한 나머지 컬럼의 업데이트를 막는다. FOR KEY SHARE와 호환되므로, 외래 키(Foreign Key) 참조 무결성 검사 시 동시성을 확보할 수 있다. | FOR KEY SHARE 허용 |
| **FOR SHARE** | 데이터를 읽는 동안 변경을 막지만, 다른 트랜잭션도 동시에 읽기 락(FOR SHARE)을 걸 수 있다. 데이터 집계나 리포팅 시 유용하다. | UPDATE 계열 차단 |
| **FOR KEY SHARE** | 가장 약한 락. 키 컬럼의 변경만 막는다. 데이터가 삭제되거나 PK가 바뀌지 않음만 보장하면 되는 경우 사용한다. | FOR UPDATE만 차단 |

PostgreSQL 운영 시에는 무조건적인 FOR UPDATE 사용을 지양하고, 비즈니스 요건에 맞춰 FOR NO KEY UPDATE 등을 적극 활용함으로써 불필요한 락 경합(Contention)을 줄이는 것이 성능 튜닝의 핵심이다.8

## **3. 엔터프라이즈 도입 사례 심층 분석**

비관적 락은 이론적으로 완벽해 보이지만, 실제 대규모 트래픽이 발생하는 프로덕션 환경에서는 예상치 못한 변수들과 상호작용한다. 다음은 국내외 주요 기술 기업들이 비관적 락을 도입하며 겪은 구체적인 사례와 그로부터 도출된 아키텍처 패턴이다.

### **사례 1: 토스증권 (Toss Securities) - 원장 관리 및 해외 주식 결제 시스템**

**도메인:** 금융 (주식 거래, 원장 관리, 해외 주식 결제)

**선택 이유:** 분산 환경에서의 데이터 무결성 보장 및 외부 API 지연에 따른 정합성 유지

### **3.1 도입 배경 및 아키텍처적 난제**

토스증권의 해외 주식 거래 시스템은 단 1원의 오차도 허용하지 않는 엄격한 정합성을 요구한다. 초기 아키텍처 설계 단계에서 개발팀은 전통적인 단일 데이터베이스 환경의 비관적 락을 검토했으나, 마이크로서비스 아키텍처(MSA) 도입으로 인해 데이터베이스가 서비스별로 파편화되면서 단순한 DB 락만으로는 전체 트랜잭션을 제어할 수 없는 문제에 봉착했다. 또한, 해외 브로커 망과의 통신 레이턴시(Network Latency)가 길어질 경우, DB 락을 잡은 상태로 대기하게 되면 커넥션 풀이 순식간에 고갈될 위험이 있었다.9

### **3.2 구현 방식: 하이브리드 락 전략 (Redis Distributed Lock + Optimistic Lock + DB Lock)**

토스증권은 순수한 비관적 락의 한계를 극복하기 위해 **계층형 방어 전략(Layered Defense Strategy)**을 채택했다.

1. **1계층 - Redis 분산 락 (논리적 비관적 락):**
- 사용자 ID나 계좌 번호를 Key로 하는 Redis 분산 락을 사용하여, 애플리케이션 레벨에서 1차적으로 동시 접근을 제어한다. 이는 DB까지 트래픽이 도달하기 전에 경합을 해소하는 역할을 한다.
- **구현 패턴:** Redisson 라이브러리의 RLock을 사용하여 스핀 락(Spin Lock) 구현의 복잡성을 낮추고, 락 획득 대기 시간(Wait Time)과 락 점유 시간(Lease Time)을 분리하여 설정했다.
1. **2계층 - JPA 낙관적 락 (안전장치):**
- 분산 락은 TTL(Time-to-Live)이 존재하여, 네트워크 지연 등으로 로직 수행이 길어지면 락이 만료될 수 있다. 이 경우 두 개의 트랜잭션이 동시에 진입하는 **갱신 유실(Lost Update)** 문제가 발생할 수 있다.
- 이를 방지하기 위해 DB 레벨에서 @Version 컬럼을 이용한 낙관적 락(CAS: Compare-And-Set)을 적용하여, 분산 락이 뚫리더라도 최종 데이터 쓰기 시점에 충돌을 감지하고 방어하도록 설계했다.9
1. **외부 API 연동 패턴 (상태 분리):**
- 긴 레이턴시를 가진 해외 주식 주문의 경우, DB 락을 잡고 API를 호출하는 안티 패턴을 제거했다.
- > `[외부 API 호출]` -> 순서로 트랜잭션을 쪼개어 DB 점유 시간을 최소화했다.10

### **3.3 성능 지표 및 운영 노하우**

- **성능 최적화:** 순수 비관적 락 사용 시 발생했던 DB 커넥션 병목 현상을 Redis로 오프로딩(Off-loading)함으로써, TPS를 유지하면서도 DB CPU 사용률을 안정화했다.
- **데드락 대응:** Redis 락의 Lease Time을 트랜잭션 평균 처리 시간(TP99)의 3~5배로 설정하여, 서버 장애 시에도 락이 영원히 점유되는 것을 방지했다.
- **모니터링:** Lost Update 발생 비율과 낙관적 락 재시도 횟수를 핵심 지표로 모니터링하여, 분산 락의 타임아웃 설정이 적절한지 지속적으로 튜닝했다.

### **사례 2: 뱅크샐러드 (Banksalad) - 게임화된 앱테크 서비스 '일해라 김뱅샐'**

**도메인:** 핀테크/게이미피케이션 (자산 연동 보상 포인트 지급)

**선택 이유:** 비관적 락의 오버헤드를 회피하고 UX 반응성을 극대화하기 위한 **'비관적 락 배제'** 결정

### **3.1 비관적 락을 선택하지 않은 역발상**

뱅크샐러드의 '일해라 김뱅샐' 서비스는 사용자가 미션을 수행하고 포인트를 획득하는 구조다. 금융 데이터의 정합성은 중요하지만, 이 서비스의 특성상 **"동일 데이터에 대한 타인과의 경합"**이 거의 발생하지 않는다는 점에 주목했다. 내 포인트는 나만 접근하기 때문이다. 따라서 무거운 비관적 락이나 인프라 비용이 드는 분산 락 대신, 가장 가벼운 **낙관적 락(Optimistic Lock)**을 선택하는 과감한 결정을 내렸다.11

### **3.2 구현 방식: Time-delta 보상 알고리즘**

낙관적 락의 최대 단점은 충돌 시 재시도(Retry) 로직이 복잡하다는 것이다. 뱅크샐러드는 이를 기술적으로 해결하기보다 **도메인 로직의 재설계**로 풀어냈다.

- **기존 문제:** 따닥(Double Click) 발생 시, 두 번째 요청이 Version 불일치로 실패하면 사용자에게 에러를 보여주거나 백그라운드에서 재시도해야 한다.
- **해결책 (Time-delta):** 보상 지급 로직을 "마지막 성공 시점(last_rewarded_at)으로부터 현재까지의 시간 차이"에 비례하여 지급하도록 설계했다.
- 요청 A와 요청 B가 동시에 들어와서 A가 성공하고 B가 실패하더라도, B는 재시도할 필요가 없다.
- 다음 요청 C가 들어올 때, A가 성공한 시점부터 C 시점까지의 시간을 계산하여 보상을 지급하므로, B가 실패했던 보상분이 자연스럽게 합산되어 지급된다.
- 이 **"자기 보정(Self-correcting)"** 구조 덕분에 락 충돌에 대한 예외 처리를 획기적으로 줄일 수 있었다.11

### **3.3 운영 노하우 및 교훈**

- **트레이드오프 분석:** 모든 금융 트랜잭션에 비관적 락이 정답은 아니다. 데이터의 소유권이 명확히 격리(Isolated)되어 있고 경합도가 낮은 경우, 비관적 락은 불필요한 엔지니어링 비용(Over-engineering)이 될 수 있음을 증명했다.
- **UX 중심 설계:** 비관적 락 사용 시 발생할 수 있는 미세한 프리징(Freezing) 현상을 제거하여, 게임과 같은 부드러운 사용자 경험을 제공했다.

### **사례 3: 위키미디어 (Wikimedia) - 콘텐츠 번역 도구 장애 사례**

**도메인:** 콘텐츠 관리 시스템 (CMS)

**핵심 이슈:** SELECT FOR UPDATE와 프론트엔드 버그의 결합으로 인한 커넥션 풀 고갈(Connection Pool Exhaustion)

### **3.1 장애 시나리오의 재구성**

2017년 발생한 위키미디어의 콘텐츠 번역 도구 장애는 비관적 락이 잘못 사용되었을 때 시스템 전체를 어떻게 무너뜨리는지를 보여주는 교과서적인 사례다.12

1. **발단 (Trigger):** 데이터센터 전환(Switch-over) 작업 중, DB가 일시적으로 읽기 전용(Read-Only) 모드로 전환되었다. 동시에 프론트엔드 버그로 인해 일부 사용자의 '임시 저장' 요청에 불필요하게 거대한 데이터 페이로드가 포함되어 전송되었다.
2. **전개 (Escalation):** 백엔드 로직은 중복 저장을 방지하기 위해 bw_cx_corpora 테이블의 특정 행에 대해 SELECT... FOR UPDATE를 수행하도록 되어 있었다.
- DB가 Read-Only 상태임에도 불구하고 애플리케이션은 락 획득을 시도했고, 예외 처리가 미비하여 무한 재시도(Retry Loop)에 진입하거나, 락 대기 큐(Lock Wait Queue)에 트랜잭션을 쌓기 시작했다.
- 거대한 페이로드로 인해 트랜잭션 처리 시간이 길어진 상태에서, 수백 개의 요청이 동시에 락을 대기하면서 DB 커넥션을 점유했다.
1. **결과 (Impact):** Max Connections 설정값에 도달하여 커넥션 풀이 고갈되었다. 이로 인해 번역 도구뿐만 아니라, 해당 DB 클러스터를 공유하는 위키미디어의 다른 서비스들까지 연쇄적으로 접속 불가 상태에 빠졌다.

### **3.2 복구 및 사후 대응**

- **즉각 조치:** 문제가 되는 쿼리를 강제로 킬(KILL)하고, 프론트엔드 배포를 롤백하여 요청 유입을 차단했다.
- **근본 원인 해결:**
- **Fail-fast 적용:** DB가 Read-Only 상태이거나 락 획득에 실패할 경우, 재시도하지 않고 즉시 에러를 반환하도록 로직을 수정했다.
- **타임아웃 설정:** innodb_lock_wait_timeout에 의존하지 않고, 애플리케이션 레벨에서 더 짧은 타임아웃을 설정하여 커넥션이 좀비 상태로 남는 것을 방지했다.
- **쿼리 최적화:** 락을 잡는 범위를 줄이기 위해 불필요한 조인이나 데이터 로딩을 락 획득 이후로 미루거나 제거했다.

### **3.3 교훈: 운영 관점의 인사이트**

이 사례는 **"프론트엔드의 버그가 백엔드 DB의 물리적 자원을 고갈시킬 수 있음"**을 시사한다. 비관적 락을 사용하는 API는 클라이언트의 행동(재시도, 대량 요청)에 대해 방어적으로 설계되어야 하며, 특히 인프라 변경(DC 스위칭 등) 상황에서의 동작을 반드시 테스트해야 한다.

## **4. 실무 운영 가이드: 비관적 락의 안정적 제어와 모니터링**

성공적인 비관적 락 운영은 코드 구현보다 **설정(Configuration)**과 **관측(Observability)**에 달려 있다. 다음은 현업 엔지니어가 반드시 준수해야 할 운영 가이드라인이다.

### **4.1 데드락(Deadlock) 감지 및 해결 방안**

데드락은 비관적 락을 사용하는 이상 피할 수 없는 숙명과도 같다. 목표는 데드락 제로(0)가 아니라, 데드락이 발생했을 때 서비스 영향도를 최소화하는 것이다.

### **발생 메커니즘과 패턴**

1. **교차 업데이트(Update-Update Conflict):** 트랜잭션 A는 자원 1→2 순서로, 트랜잭션 B는 자원 2→1 순서로 락을 요청할 때 발생한다. 가장 흔한 패턴이다.6
2. **FK와 인덱스 경합:** 부모 테이블을 삭제(DELETE)할 때 자식 테이블에 공유 락(S-Lock)이 걸리는데, 동시에 자식 테이블에 INSERT가 발생하면 배타 락(X-Lock)과 충돌하여 데드락이 발생할 수 있다.13

### **[Checklist] 데드락 대응 전략**

- **[필수] 자원 접근 순서의 정규화 (Canonical Ordering):** 애플리케이션 전체에서 테이블과 레코드에 접근하는 순서를 엄격하게 통일한다.
- *예시:* 여러 계좌 간 이체 시, 항상 계좌 ID가 작은 순서대로 락을 획득한다 (ORDER BY id ASC).

SQL

- - Bad Pattern: 입력된 순서대로 락 획득 (위험)

SELECT * FROM accounts WHERE id IN (300, 100) FOR UPDATE;

- - Good Pattern: ID 순으로 정렬하여 락 획득 (안전)

SELECT * FROM accounts WHERE id IN (100, 300) ORDER BY id ASC FOR UPDATE;

- **[권장] 트랜잭션 범위 축소:** 락을 획득하는 시점을 트랜잭션의 가장 마지막으로 미루고, 락을 획득하자마자 업데이트 후 즉시 커밋한다. 사용자 입력을 기다리거나 외부 API를 호출하는 구간에서는 절대 락을 잡고 있어서는 안 된다.3
- **[설정] 데드락 감지 비용 관리:** MySQL의 innodb_deadlock_detect는 기본적으로 활성화되어 있으나, 동시성이 극도로 높은 상황에서는 데드락 감지 스레드 자체가 CPU를 과도하게 점유할 수 있다. 이 경우 감지 기능을 끄고(OFF), 매우 짧은(예: 500ms) 락 타임아웃으로 실패 처리하는 것이 전체 처리량 면에서 유리할 수 있다.14

### **4.2 Lock Timeout 설정의 수학적 접근**

"적절한 타임아웃 값은 얼마인가?"라는 질문에 대해 많은 엔지니어가 감에 의존한다. 하지만 이는 대기열 이론(Queueing Theory)에 기반하여 산정해야 한다.

- **MySQL:** innodb_lock_wait_timeout (기본값: 50초)
- **PostgreSQL:** lock_timeout (기본값: 무한대)

**운영 권고안:**

기본값 50초는 웹 서비스 환경에서 영겁의 시간이다. 사용자가 브라우저에서 이탈하기까지의 시간(보통 3~5초)보다 길게 설정하는 것은 무의미하다.

- **OLTP 서비스:** **1초 ~ 3초**. 사용자가 인내할 수 있는 한계 내에서 빠르게 실패(Fail-fast)하고, 애플리케이션에서 안내 메시지를 띄우는 것이 낫다.
- **배치 작업:** **30초 ~ 60초**. 대량 처리가 필요한 경우 조금 더 여유를 둔다.

**JPA QueryHint를 이용한 설정 예시:** 애플리케이션 코드 레벨에서 쿼리별로 타임아웃을 다르게 가져가는 것이 가장 유연하다.15

Java

@Lock(LockModeType.PESSIMISTIC_WRITE)

@QueryHints({

@QueryHint(name = "javax.persistence.lock.timeout", value = "2000") // 2초 타임아웃

})

Optional<Inventory> findByProductId(Long id);

### **4.3 모니터링 및 관측성(Observability) 확보**

장애 징후를 사전에 포착하기 위해 다음과 같은 지표를 대시보드(Grafana 등)에 시각화해야 한다.

| **지표 (Metric)** | **설명** | **임계치 및 알람 조건** |
| --- | --- | --- |
| **Current Lock Waits** | 현재 락 획득을 대기 중인 트랜잭션 수 | 평소 대비 2배 이상 급증 시 Warning |
| **Lock Wait Time (Avg/Max)** | 락 획득까지 걸린 시간 | 평균 100ms 초과 시 점검 필요 |
| **Deadlocks / Sec** | 초당 데드락 발생 횟수 | 지속적으로 > 0 일 경우 코드 리뷰 필요 |
| **Row Lock Contention** | 특정 테이블/인덱스에 대한 락 집중도 | 특정 테이블이 전체 락의 80% 이상 점유 시 |

**MySQL 실시간 분석 쿼리 (Performance Schema):**

SQL

SELECT

r.trx_id AS waiting_trx_id,

r.trx_mysql_thread_id AS waiting_thread,

LEFT(r.trx_query, 50) AS waiting_query,

b.trx_id AS blocking_trx_id,

LEFT(b.trx_query, 50) AS blocking_query,

TIMESTAMPDIFF(SECOND, r.trx_wait_started, NOW()) AS wait_duration_sec

FROM performance_schema.data_lock_waits w

JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_engine_transaction_id

JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_engine_transaction_id

WHERE TIMESTAMPDIFF(SECOND, r.trx_wait_started, NOW()) > 1; -- 1초 이상 대기 건 조회

## **5. 고성능 패턴: SKIP LOCKED와 NOWAIT**

최신 데이터베이스 기능인 SKIP LOCKED와 NOWAIT은 비관적 락의 패러다임을 '대기'에서 '회피'로 전환시킨다. 이를 활용하면 메시지 큐(Message Queue) 없이도 DB만으로 고성능 작업 큐를 구현할 수 있다.10

### **5.1 SKIP LOCKED를 활용한 동시성 처리량 극대화**

SELECT... FOR UPDATE SKIP LOCKED는 이미 락이 걸린 레코드는 결과 집합에서 과감히 제외하고, 락이 걸리지 않은 레코드만 즉시 반환한다.

- **적용 사례:** 이메일 발송, 정산 처리, 쿠폰 지급 등 "순서는 중요하지 않으나 중복 처리는 안 되며, 최대한 빨리 처리해야 하는" 작업.
- **효과:** 여러 워커(Worker) 프로세스가 동시에 DB를 쿼리해도 서로를 블로킹하지 않고 각자 처리할 수 있는 물량을 가져가므로, 데드락이 원천적으로 차단되고 처리량이 선형적으로 증가한다.
- **코드 예시 (티켓 발권 대기열):**

  SQL

    - - 워커 1이 실행: ID 1, 2를 가져감

  SELECT * FROM tickets WHERE status = 'READY' LIMIT 2 FOR UPDATE SKIP LOCKED;

    - - 워커 2가 실행: ID 1, 2는 건너뛰고 ID 3, 4를 즉시 가져감 (대기 없음)

  SELECT * FROM tickets WHERE status = 'READY' LIMIT 2 FOR UPDATE SKIP LOCKED;


### **5.2 NOWAIT을 이용한 Fail-Fast UX**

SELECT... FOR UPDATE NOWAIT은 락이 걸려있을 경우 대기하지 않고 즉시 에러를 반환한다.

- **적용 사례:** 영화 좌석 예매, 선착순 한정 판매. "이미 다른 고객이 선택 중인 좌석입니다"라는 메시지를 즉시 띄워주는 것이 사용자 경험상 유리하다.
- **효과:** 불필요한 대기 스레드가 쌓이는 것을 방지하여 DB 커넥션 풀을 보호한다.

## **6. 결론 및 제언**

비관적 락은 강력하지만 비싼 도구다. 본 리서치를 통해 확인된 실무의 핵심은 "비관적 락을 쓰느냐 마느냐"의 이분법이 아니라, **"어떻게 안전하게 가두어두고(Containment) 제어할 것인가"**에 있다.

1. **MSA 환경의 표준 패턴:** 토스증권 사례와 같이 **Redis 분산 락**을 1차 관문으로 세워 트래픽을 제어하고, **DB 비관적 락**을 데이터 무결성의 최종 보루로 사용하는 하이브리드 패턴이 엔터프라이즈 표준으로 자리 잡았다.
2. **도메인 특성에 따른 유연성:** 뱅크샐러드 사례처럼 경합이 낮거나 결과적 정합성으로 해결 가능한 영역에는 과감하게 낙관적 락을 적용하여 시스템 복잡도를 낮추는 지혜가 필요하다.
3. **운영이 곧 아키텍처:** 위키미디어 사례는 코드의 결함이 운영 설정(타임아웃, Read-Only 처리) 미비와 만나 재앙이 됨을 보여준다. ORDER BY를 통한 락 순서 정렬, 짧은 타임아웃 설정, SKIP LOCKED 활용은 선택이 아닌 필수 운영 요건이다.

현업 엔지니어들은 본 보고서에 제시된 **타임아웃 전략(1~3초)**, **정렬된 락 획득 패턴**, 그리고 **모니터링 쿼리**를 기반으로 시스템을 점검하고, 비즈니스 로직에 가장 적합한 락 전략을 수립해야 할 것이다.

### **부록: Pessimistic Lock 운영 체크리스트 (Operational Checklist)**

| **구분** | **체크 항목** | **권장 값/조치 전략** | **중요도** |
| --- | --- | --- | --- |
| **기본 설정** | MySQL innodb_lock_wait_timeout | **1s ~ 3s** (웹 서비스), 30s (배치) | Critical |
| **기본 설정** | PostgreSQL lock_timeout | **2s ~ 5s** (기본값 무한대 사용 금지) | Critical |
| **데드락** | 데드락 감지 (innodb_deadlock_detect) | **ON** (단, 초고부하 시 OFF 후 짧은 타임아웃 적용) | High |
| **코드 품질** | 락 획득 순서 (Ordering) | PK 오름차순 정렬 (ORDER BY id ASC) 필수 준수 | Critical |
| **코드 품질** | 외부 API 호출 (External Call) | **트랜잭션(락) 범위 외부**로 반드시 분리 | Critical |
| **코드 품질** | 락 범위 (Scope) | 비즈니스 로직 수행 후 **업데이트 직전**에 락 획득 | High |
| **성능 최적화** | 인덱스 사용 (Index Usage) | 락은 인덱스에 걸리므로, WHERE 조건에 인덱스 필수 | Critical |
| **성능 최적화** | SKIP LOCKED 활용 | 작업 큐(Job Queue) 형태의 로직에는 필수 적용 | High |
| **모니터링** | Lock Wait Alert | 대기 트랜잭션 수 > N개 발생 시 알림 설정 | High |
| **모니터링** | Deadlock Alert | 발생 시 로그 수집 및 개발팀 즉시 통보 | Medium |

**[참고 문헌 및 출처]** 1 Hwangrolee Tech Blog - Understanding Pessimistic Lock 7 PostgreSQL Locking Guide - FOR UPDATE Variants & Monitoring 3 OneUptime Blog - Pessimistic Locking Implementation & Deadlock Prevention 9 Toss Tech Blog / SLASH 22 - Broker Concurrency & Distributed Lock 11 Banksalad Tech Blog - Optimistic vs Pessimistic Lock Decision 13 Netdata - PostgreSQL Deadlock Patterns & Resolution 12 Wikimedia Incident Documentation - Content Translation Outage 2 StackOverflow - Optimistic vs Pessimistic Locking 4 Tencent Cloud - Database Lock Monitoring Best Practices 15 Spring Data JPA QueryHints Guide 6 Releem - MySQL Deadlock Detection & Fixes

### **참고 자료**

1. 비관적 락(Pessimistic Lock), 진짜 뭔지 아세요? - 개발자가 궁금해할 ..., 2월 3, 2026에 액세스, [https://hwangrolee.github.io/blog/%EB%B9%84%EA%B4%80%EC%A0%81-%EB%9D%BD(Pessimistic-Lock)-%EC%A7%84%EC%A7%9C-%EB%AD%94%EC%A7%80-%EC%95%84%EC%84%B8%EC%9A%94-%EA%B0%9C%EB%B0%9C%EC%9E%90%EA%B0%80-%EA%B6%81%EA%B8%88%ED%95%B4%ED%95%A0-%EC%A7%88%EB%AC%B8-7%EA%B0%80%EC%A7%80%EB%A1%9C-%EC%A0%95%EB%A6%AC/](https://hwangrolee.github.io/blog/%EB%B9%84%EA%B4%80%EC%A0%81-%EB%9D%BD(Pessimistic-Lock)-%EC%A7%84%EC%A7%9C-%EB%AD%94%EC%A7%80-%EC%95%84%EC%84%B8%EC%9A%94-%EA%B0%9C%EB%B0%9C%EC%9E%90%EA%B0%80-%EA%B6%81%EA%B8%88%ED%95%B4%ED%95%A0-%EC%A7%88%EB%AC%B8-7%EA%B0%80%EC%A7%80%EB%A1%9C-%EC%A0%95%EB%A6%AC/)
2. Optimistic vs. Pessimistic locking - Stack Overflow, 2월 3, 2026에 액세스, https://stackoverflow.com/questions/129329/optimistic-vs-pessimistic-locking
3. How to Build Pessimistic Locking Implementation - OneUptime, 2월 3, 2026에 액세스, https://oneuptime.com/blog/post/2026-01-30-pessimistic-locking-implementation/view
4. How to avoid database table locking? - Tencent Cloud, 2월 3, 2026에 액세스, https://www.tencentcloud.com/techpedia/136177
5. 외부 API가 서비스를 마비시킬 뻔한 이야기: 가상스레드 도입기, 2월 3, 2026에 액세스, https://myvelop.tistory.com/261
6. MySQL Deadlock Detection - Releem, 2월 3, 2026에 액세스, https://releem.com/blog/mysql-deadlock-detection
7. Everything You Need To Know About Postgresql Locks: Practical ..., 2월 3, 2026에 액세스, https://mohitmishra786.github.io/chessman/2025/03/02/Everything-You-Need-to-Know-About-PostgreSQL-Locks-Practical-Skills-You-Need.html
8. The SELECT FOR UPDATE Trap Everyone Falls Into - Medium, 2월 3, 2026에 액세스, https://medium.com/fresha-data-engineering/the-select-for-update-trap-everyone-falls-into-8643089f94c7
9. 토스 SLASH 22, 애플 한 주가 고객에게 전달되기 까지 - haon.blog, 2월 3, 2026에 액세스, https://haon.blog/article/toss-slash/broker-issue-concurrency-and-network-latency/
10. Pessimistic Locks. What are Pessimistic locks? | by Vansh Uppal ..., 2월 3, 2026에 액세스, https://medium.com/@vansh7uppal/pessimistic-locks-e316905d7671
11. 뱅크샐러드가 게임을 만들 때 데이터 정합성을 유지하는 법 (feat ..., 2월 3, 2026에 액세스, https://blog.banksalad.com/tech/banksalad-optimistic-lock/
12. T163344 Do a root-cause analysis on CX outage during dc switch ..., 2월 3, 2026에 액세스, https://phabricator.wikimedia.org/T163344
13. 10 Real-World PostgreSQL Deadlock Stack Traces and How They ..., 2월 3, 2026에 액세스, https://www.netdata.cloud/academy/10-real-world-postgresql-deadlock/
14. MySQL 엔진 아키텍처 - 정리하는 습관 - 티스토리, 2월 3, 2026에 액세스, https://yainii.tistory.com/40
15. How @QueryHints in Spring Data JPA Can Speed Up ... - Medium, 2월 3, 2026에 액세스, https://medium.com/jpa-java-persistence-api-guide/how-queryhints-in-spring-data-jpa-can-speed-up-your-applications-a-practical-guide-639d6cacfa0f