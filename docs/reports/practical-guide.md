# 📘 Practical Guide: 동시성 제어 실무 도입 가이드

본 가이드는 `concurrency-control-poc` 프로젝트의 **Sprint 5 성능 테스트 결과(Capacity, Contention, Stress)**를 기반으로 작성된 실무 적용 지침서입니다.

---

## 1. 의사결정 매트릭스 (Decision Matrix)

상황에 맞춰 가장 적합한 동시성 제어 방식을 선택하세요.

| 상황 (Scenario) | 추천 방식 (Best Practice) | 이유 (Why) |
| :--- | :--- | :--- |
| **선착순 이벤트 / 핫딜** <br> (초고부하, 짧은 시간) | 🥇 **Redis Lua Script** | • **압도적 성능:** DB 락 대비 2.5배~10배 처리량<br>• **원자성 보장:** 락 획득/해제 과정 없음<br>• **인프라 절감:** 적은 리소스로 최대 트래픽 방어 |
| **일반적인 재고/결제** <br> (데이터 정합성 최우선) | 🥈 **Pessimistic Lock** | • **안정성:** DB 레벨에서 강력한 정합성 보장<br>• **구현 용이:** `@Lock` 어노테이션으로 간단 구현<br>• **가성비:** 별도 Redis 운영 비용 없음 |
| **게시판 / 위키 편집** <br> (충돌 빈도 낮음) | 🥉 **Optimistic Lock** | • **평시 고성능:** 락을 걸지 않아 평소에 빠름<br>• **주의:** 충돌 시 재시도(Retry) 로직 필수 구현 |
| **분산 환경 리소스 제어** <br> (DB 외 자원 락킹) | ⚠️ **Redis Distributed Lock** | • **기능성:** 분산 서버 간 락 공유 필요 시 사용<br>• **주의:** 네트워크 오버헤드가 크므로 고부하 시 병목 주의 |

---

## 2. 방식별 구현 및 운영 가이드

### ① Redis Lua Script (The Performance King)
*   **적용 대상:** 티켓팅, 쿠폰 발급, 타임 세일 등 트래픽이 폭주하는 서비스.
*   **장점:** `EVAL` 명령어를 통해 스크립트 자체가 하나의 원자적(Atomic) 연산으로 실행됨.
*   **운영 Tip:**
    *   **스크립트 관리:** Lua 스크립트는 형상 관리(Git)에 포함하고, 해시값(SHA)으로 호출(`EVALSHA`)하여 네트워크 대역폭을 아끼세요.
    *   **복잡도 제한:** Redis는 싱글 스레드입니다. 스크립트 실행 시간이 길어지면 전체 Redis가 멈춥니다. 단순 연산만 포함하세요.

### ② Pessimistic Lock (The Reliable Standard)
*   **적용 대상:** 금융 거래, 물류 재고 등 데이터 정확도가 생명인 엔터프라이즈 시스템.
*   **장점:** 애플리케이션 레벨의 복잡한 로직 없이 DB가 순서를 보장해줍니다.
*   **운영 Tip:**
    *   **타임아웃 설정:** 무한 대기를 막기 위해 `javax.persistence.lock.timeout` 힌트를 반드시 설정하세요.
    *   **인덱스 필수:** 락을 거는 컬럼에 인덱스가 없으면 **테이블 풀 스캔 + 테이블 락**이 걸려 재앙이 발생합니다.

### ③ Optimistic Lock (The Read-Heavy Specialist)
*   **적용 대상:** 상품 정보 수정, 위키 문서 편집 등 '읽기'가 많고 '쓰기' 충돌이 적은 곳.
*   **장점:** 락 점유 시간이 '0'이므로 DB 커넥션 점유율이 낮습니다.
*   **운영 Tip:**
    *   **재시도 전략:** 충돌(`OptimisticLockException`) 발생 시 몇 번, 며칠 간격으로 재시도할지 정책(Exponential Backoff)을 마련해야 합니다.
    *   **Rate Limiter:** 충돌이 잦아지면 재시도 요청이 폭증하여 DB를 공격(DDoS)하는 형태가 될 수 있습니다. 앞단에서 유입량을 제어하세요.

---

## 3. 인프라 사이징 가이드 (Sizing Guide)

테스트 결과에 기반한 트래픽 규모별 권장 스펙입니다.

| 예상 트래픽 (RPS) | 권장 스펙 (App/DB) | 추천 아키텍처 | 비고 |
| :---: | :--- | :--- | :--- |
| **~ 500 RPS** | 2 vCPU / 4GB RAM | **Pessimistic Lock** | 단일 DB로 충분히 커버 가능 |
| **~ 2,000 RPS** | 2 vCPU / 4GB RAM | **Lua Script** | Redis 도입으로 DB 부하 분산 필수 |
| **5,000+ RPS** | 4 vCPU / 8GB RAM | **Lua Script** | Scale-up 및 Virtual Threads 활용 |

> **Note:** 위 수치는 `Virtual Threads`가 활성화된 Spring Boot 3.2+ 기준입니다.

---

## 4. 성능 최적화 체크리스트 (Checklist)

서비스 오픈 전 반드시 확인해야 할 항목입니다.

- [ ] **Connection Pool Tuning:**
    - HikariCP(DB)와 Lettuce(Redis)의 풀 사이즈를 부하 테스트를 통해 최적화했는가?
    - *권장:* HikariCP Max 50~100 (너무 크면 Context Switching 비용 증가)
- [ ] **Virtual Threads:**
    - Java 21 이상을 사용 중이라면 `spring.threads.virtual.enabled=true`를 켰는가?
- [ ] **Isolation Test:**
    - 성능 테스트 시 매번 인프라를 초기화하여 'Cold Start'와 'Warm 상태'를 구분해 측정했는가?
- [ ] **Fail-over Plan:**
    - Redis가 죽었을 때 DB 락으로 전환(Fallback)할 수 있는가? 아니면 점검 페이지를 띄울 것인가?
