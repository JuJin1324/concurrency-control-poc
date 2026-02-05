# [Deep Dive] Redis Distributed Lock Troubleshooting & Checklists

**Parent Document:** [Redis Distributed Lock 운영 가이드](../redis-lock-ops.md)

Redis 분산 락은 강력하지만 네트워크 지연, 클라이언트 멈춤 등 분산 시스템 고유의 불확실성에 노출되어 있습니다. 이 문서는 발생 가능한 장애 시나리오와 이에 대한 대응 매뉴얼을 상세히 기술합니다.

---

## 1. 락 누수(Lock Leak) 방지: "TTL의 딜레마"

락을 잡은 프로세스가 비정상 종료되면 시스템은 영원히 잠깁니다.

*   **해결책: Redisson Watchdog**
    *   **동작:** TTL을 명시적으로 설정하지 않으면 기본값 30초로 락을 걸고, 백그라운드 스레드(Watchdog)가 10초마다 락을 다시 30초로 연장함.
    *   **장점:** 프로세스가 죽으면 연장이 멈추므로 30초 뒤 자동으로 락이 해제됨. 가장 안전한 설정.
*   **권장 설정:** 명시적 TTL이 필요하다면 **(P99 처리 시간) x 3~5** 정도로 넉넉하게 설정.

---

## 2. 클라이언트 멈춤(GC Pause) 대응

클라이언트가 락을 잡고 일하는 도중 **Full GC(Stop-the-world)**가 10초간 발생한다면? 그 사이 Redis 락은 만료되고 다른 놈이 진입할 수 있습니다.

*   **해결책: Fencing Token 패턴**
    *   **메커니즘:** 락을 발급할 때마다 단조 증가하는 번호(33, 34...)를 함께 줌.
    *   **동작:** DB에 쓸 때 "지금 락 번호가 33인데, 이거보다 큰 번호가 이미 들어와 있니?" 확인.
    *   **구현:** Redisson의 `RFencedLock`이 이 기능을 지원하므로 정합성이 극도로 중요한 경우 사용 권장.

---

## 3. Redis 장애 시 3단계 Fallback 전략

쿠팡의 사례처럼 Redis가 죽었을 때 서비스 전체가 죽으면 안 됩니다.

1.  **1순위 (Main):** Redis Distributed Lock.
2.  **2순위 (Fallback):** DB Pessimistic Lock.
    *   Redis 실패율이 높아지면 **서킷 브레이커**를 열고 DB 락으로 우회.
3.  **3순위 (Critical):** 요청 거부 및 재시도 안내.
    *   DB까지 위험해지면 시스템 보호를 위해 Fail-fast.

---

## 4. 운영 체크리스트 (Operational Checklists)

### 개발 단계
- [ ] **finally 블록에서 unlock**: 예외 발생 시에도 락 해제 보장 필수.
- [ ] **isHeldByCurrentThread 확인**: 내가 안 잡은 락을 해제하려는 실수 방지.
- [ ] **WaitTime 제한**: 무한정 기다리지 말고 2~3초 내에 타임아웃 처리.
- [ ] **Idempotency Key 병행**: Redis 락은 성능 최적화 수단일 뿐, 최종 정합성은 DB 유니크 제약조건 등으로 보강.

### 운영 단계
- [ ] **Latency 모니터링**: p99 > 100ms 시 알람. (네트워크/부하 이슈)
- [ ] **SLOWLOG 점검**: 스크립트가 Redis를 블로킹하고 있지 않은지 확인.
- [ ] **Failover 시간 최적화**: Sentinel `down-after-milliseconds`를 5000ms 수준으로 낮게 조정 고려.

---

## 5. Detailed Checklists & Scenarios

### 5.1 Lock 누수 방지 상세 체크리스트
단순한 코드 리뷰를 넘어, 다음 항목들을 기계적으로 체크해야 합니다.
- [ ] **TTL 필수 설정:** `leaseTime` 없는 락 획득은 절대 금지. (프로세스 다운 시 영구 데드락)
- [ ] **Finally Unlock:** `unlock()`은 반드시 `finally` 블록의 **가장 마지막**에 위치해야 함.
- [ ] **Ownership Check:** `if (lock.isHeldByCurrentThread()) { lock.unlock(); }` 패턴 준수. (타임아웃으로 이미 남에게 넘어간 락을 내가 끄면 안 됨)
- [ ] **Watchdog 활성화:** Redisson 사용 시 `leaseTime = -1`로 설정하여 Watchdog 기능을 켜는 것이 가장 안전함.

### 5.2 네트워크 파티션(Network Partition) 대응 시나리오
Redis 서버는 살아있지만 네트워크 문제로 연결이 안 될 때의 시나리오입니다.
1.  **Timeout 발생:** Redis 연결 타임아웃(예: 5초)이 연속으로 발생.
2.  **Circuit Breaker Open:** Redis 호출을 즉시 차단하고 **DB Pessimistic Lock**으로 경로를 변경. (서비스 다운 방지)
3.  **Half-Open:** 일정 시간(예: 30초) 후 Redis에 핑(Ping)을 보내 복구 여부를 확인.
4.  **Recovery:** 연결이 안정화되면 다시 Redis Lock으로 복귀.
