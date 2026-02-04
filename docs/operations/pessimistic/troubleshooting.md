# [Deep Dive] Pessimistic Lock Troubleshooting & Checklists

**Parent Document:** [Pessimistic Lock 운영 가이드](../pessimistic-lock-ops.md)

비관적 락을 운영하며 마주치게 될 장애 상황(War Game)과, 이를 예방하기 위한 상세 체크리스트입니다. 단순한 설정을 넘어, 각 설정이 시스템에 미치는 영향(Side Effect)을 중심으로 기술합니다.

---

## 1. 운영 체크리스트 (Operational Checklists)

### 1.1 Deadlock 심층 분석 및 대응
단순한 교차 업데이트(A->B vs B->A) 외에도, **인덱스와 FK**로 인한 데드락이 실무에서 더 빈번하고 까다롭습니다.

*   **[Check 1] 인덱스 없는 쿼리 식별 (Full Table Scan Lock)**
    *   **증상:** 락이 걸린 테이블이 아닌데도 전체 시스템이 느려지거나, 전혀 상관없는 데이터 수정이 블로킹됨.
    *   **원인:** `WHERE` 절에 인덱스가 없으면 DB는 **Full Table Scan**을 수행합니다. 이때 InnoDB는 스캔하는 **모든 Row에 락**을 걸어버립니다. (설령 조건에 맞지 않아도!)
    *   **Action:**
        *   `EXPLAIN` 실행 계획에서 `type: ALL`이 나오면 즉시 비관적 락 사용을 중단하거나 인덱스를 추가해야 합니다.
        *   가능하면 `FORCE INDEX` 힌트를 사용하여 인덱스 사용을 강제하십시오.
*   **[Check 2] Foreign Key(FK) 데드락 (Lock Propagation)**
    *   **증상:** 부모 테이블 `DELETE`/`UPDATE`와 자식 테이블 `INSERT`가 충돌하여 데드락 발생.
    *   **원인:** 자식 테이블에 데이터를 넣을 때, 부모 데이터의 존재(FK)를 확인하기 위해 부모 Row에 암묵적 **공유 락(S-Lock)**이 걸립니다. 이때 부모를 수정하려는 트랜잭션이 **배타 락(X-Lock)**을 요청하면, 서로 락을 놓지 못해 데드락에 빠집니다.
    *   **Action:**
        *   **FK 제거 고려:** 대규모 트래픽 환경에서는 락 전파를 막기 위해 **물리적 FK를 제거**하고, 애플리케이션 레벨에서 정합성을 검증하는 것이 트렌드입니다.
            ```java
            // 애플리케이션 레벨의 정합성 검증 예시
            if (!teamRepository.existsById(teamId)) {
                // 부모(Team)가 존재하는지 먼저 확인
                throw new EntityNotFoundException("팀이 존재하지 않습니다");
            }
            // 검증 통과 후 자식(Member) 저장
            memberRepository.save(new Member(teamId, ...));
            ```
            *   **주의:** `existsById` 검증 통과 직후, **다른 스레드에서 부모 데이터를 삭제**해버리는 Race Condition 발생 가능.
            *   **보완:** **Soft Delete**(`is_deleted=true`)를 사용하여 물리적 삭제를 지연시키거나, 주기적인 **배치(Batch)**로 고아 데이터를 정리하는 전략을 병행해야 합니다.
        *   **순서 격리:** 부모 테이블 수정 트랜잭션과 자식 테이블 추가 트랜잭션이 겹치지 않도록 비즈니스 로직을 분리합니다.

### 1.2 Lock Timeout: "50초는 영원이다"
DB 기본값(`innodb_lock_wait_timeout = 50s`)은 웹 서비스 환경에서 재앙입니다. 사용자는 이미 떠났는데 DB 혼자 50초 동안 락을 잡고 대기하며 커넥션을 낭비합니다.

*   **[Rule 1] Fail-Fast 설정 (1~3초)**
    *   사용자가 "새로고침"을 누르기 전에 시스템이 먼저 실패를 감지하고 응답해야 합니다. (3초 이내 권장)
    *   **주의:** JPA Hint(`javax.persistence.lock.timeout`)는 DB 벤더마다 단위(초 vs 밀리초)가 다르거나 무시될 수 있습니다.
    *   **Action:** 가장 확실한 방법은 **JDBC URL 파라미터**(`sessionVariables=innodb_lock_wait_timeout=2`)나 **세션 레벨 설정**(`SET SESSION ...`)을 사용하는 것입니다.
*   **[Rule 2] Connection Pool 오염 방지**
    *   `LockTimeoutException`이 발생했을 때, 예외 처리가 미비하면 트랜잭션이 롤백되지 않은 상태로 커넥션이 풀(Pool)에 반납될 수 있습니다. (Dirty Connection)
    *   **Action:** 반드시 `try-catch` 블록에서 예외를 잡고, 트랜잭션을 **명시적으로 롤백** 처리하여 커넥션을 깨끗하게 비워야 합니다.
        ```java
        @Transactional
        public void processWithLock() {
            try {
                repository.findByIdForUpdate(id);
            } catch (PessimisticLockingFailureException e) {
                // 1. 로그 기록
                log.error("락 획득 실패: {}", e.getMessage());
                // 2. 중요: Spring에게 트랜잭션 롤백을 명시적으로 지시
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                // 3. 비즈니스 예외로 전환하여 던짐 (사용자 응답용)
                throw new CustomLockTimeoutException("잠시 후 다시 시도해주세요");
            }
        }
        ```
        *   **핵심:** `setRollbackOnly()`를 호출하면, 메서드가 끝나기 전까지는 작업을 계속할 수 있지만(로그 기록 등), 최종적으로 트랜잭션은 **반드시 롤백**되어 커넥션이 초기화됩니다.

### 1.3 모니터링: "평균의 배신"을 피하라
*   **[Metric 1] Max / P99 Lock Wait Time**
    *   대부분의 트랜잭션은 락 대기 시간이 0ms입니다. 따라서 평균(`Avg`)은 0.1ms 처럼 나와서 문제를 숨깁니다. **"가장 오래 기다린 놈(Max)"**이 5초, 10초를 기다리고 있는지 감시하십시오.
*   **[Metric 2] Current Lock Waits**
    *   `Performance Schema`의 `data_locks` 테이블 조회는 그 자체로 DB에 부하를 줍니다. 실시간(1초 단위)으로 조회하지 마십시오.
    *   **Action:** **5초/10초 주기 샘플링**이나, **타임아웃 에러 발생 시점에만 스냅샷**을 찍어서 로그에 남기는 전략을 사용하십시오.
*   **[Metric 3] 애플리케이션 지표 연동**
    *   DB 지표만으로는 "어떤 API"가 범인인지 모릅니다. AOP를 활용해 **`LockAcquisitionTime`**을 측정하고, 임계치(예: 1초)를 넘는 경우 로그에 `Request URI`, `User ID` 등을 함께 남겨야 추적이 가능합니다.

### 1.4 FK(Foreign Key) 의사결정 매트릭스
무조건 FK를 거는 것도, 무조건 빼는 것도 정답이 아닙니다. 트래픽과 데이터 중요도에 따라 결정하십시오.

| 구분 | **FK 권장 (Strong Consistency)** | **Non-FK 권장 (High Scalability)** |
| :--- | :--- | :--- |
| **도메인** | 금융 원장, 결제, 정산, 어드민 | 대용량 주문, 로그, 이력성 데이터, SNS 피드 |
| **이유** | 데이터 무결성이 성능보다 중요함. (돈이 꼬이면 안 됨) | 락 전파(Lock Propagation)로 인한 데드락과 성능 저하 방지. |
| **대안** | DB가 데이터 오염을 최종 방어 (안전벨트) | App 레벨 검증(`existsById`) + 배치(Batch)로 고아 데이터 정리 (Garbage Collection) |

---

## 2. 장애 대응 시나리오 (War Game)

### Scenario A: Flash Sale Lock Storm
*   **상황:** 인기 상품 오픈 직후 10만 명이 몰려 DB CPU가 100%를 치고, 커넥션 풀이 고갈됨.
*   **원인:** 수만 명이 동시에 `FOR UPDATE` 대기열에 진입하여 DB 리소스를 잠식함.
*   **대응:**
    1.  **긴급 (Bleeding Control):** L4/L7 로드밸런서에서 유입 차단 또는 대기열 페이지로 리다이렉트. DB 락 타임아웃을 **1초**로 축소하여 빨리 실패하게 만듦 (Fail-Fast).
    2.  **해결 (Fundamental):** **대기열(Virtual Waiting Room)** 도입. DB 앞단에 Redis나 Netfunnel을 두어 입장 인원을 제어(Throttling)해야 합니다. "DB는 죄가 없습니다."

### Scenario B: Long-Running Transaction
*   **상황:** DB 부하는 적은데(CPU 낮음), 모든 요청이 타임아웃으로 실패함.
*   **원인:** 개발자가 실수로 트랜잭션 안에서 **외부 결제 API(3초 소요)**를 호출함. 락을 잡은 채로 외부 통신을 하느라 커넥션을 놓아주지 않음.
*   **대응:**
    1.  **식별:** APM(Pinpoint 등) 트레이스에서 DB 쿼리 실행 후 다음 로직까지 **긴 공백(Gap)**이 있는 트랜잭션을 적발.
    2.  **해결:** **Facade 패턴** 적용. `재고 락 트랜잭션`과 `결제 API 호출`을 분리. 결제 실패 시 보상 트랜잭션(재고 복구) 수행.

### Scenario C: Database Failover (Master Down)
*   **상황:** Master DB 장애로 Slave가 승격되는 중. 애플리케이션에서 `Connection Refused` 에러 다발.
*   **대응:**
    1.  **Connection Pool:** `max-lifetime`을 DB `wait_timeout`보다 짧게 설정하여, WAS가 들고 있는 **좀비 커넥션**을 미리미리 정리하게 해야 합니다.
    2.  **Circuit Breaker:** 에러율이 치솟으면 서킷을 열어(Open) DB 연결 시도를 중단하고, 캐시 데이터나 점검 페이지를 리턴하여 DB가 회복할 시간(숨통)을 벌어줍니다.
    3.  **Read Replica 주의:** 단순 조회는 Slave로 돌릴 수 있지만, **재고/잔액 확인 로직은 절대 Slave를 믿으면 안 됩니다.** 복제 지연(Replication Lag)으로 인해 이미 팔린 상품을 또 팔 수 있습니다.