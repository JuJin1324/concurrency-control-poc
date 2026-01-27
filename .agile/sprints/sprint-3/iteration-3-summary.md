# Iteration 3 Summary: 전체 방법 부하 테스트 및 결과 분석

**Sprint:** Sprint 3 - k6 부하 테스트 + 최종 성능 비교 분석
**Iteration:** 3/3
**완료일:** 2026-01-27
**상태:** ✅ 완료

---

## 구현 내용

### 1. 4가지 동시성 제어 방식 부하 테스트

- **도구:** k6 (Local Docker Environment)
- **조건:** 재고 100개, 초당 100회 요청, 총 1,000회 (Spike Test)
- **대상:**
  - Pessimistic Lock
  - Optimistic Lock
  - Redis Lock
  - Redis Lua Script
- **검증:** 각 테스트 후 DB 및 Redis 데이터 정합성 확인 (모두 통과)

---

### 2. 성능 분석 및 리포트 작성

**위치:** `docs/performance-test-result.md`

- **주요 발견:**
  - 로컬 Docker 환경에서는 **Pessimistic Lock**이 가장 우수한 성능(4.82ms)을 보임.
  - **Optimistic Lock**은 k6의 Pacing 효과로 인해 예상보다 훨씬 좋은 성능(4.75ms)을 기록.
  - **Redis 기반 방식**들은 네트워크 RTT로 인해 약 1ms 정도의 추가 지연 발생.
  - 모든 방식이 데이터 정합성(재고 0)을 완벽하게 보장함.

---

### 3. 실무 적용 가이드 작성

**위치:** `docs/practical-guide.md`

- **가이드 핵심:**
  - **Pessimistic Lock:** 단일 DB, 데이터 정합성 최우선 시 추천 (기본값).
  - **Optimistic Lock:** 읽기 위주, 충돌이 적은 웹 서비스에 추천.
  - **Redis Lock:** 분산 DB/MSA 환경, DB 부하 감소 필요 시 추천.
  - **Lua Script:** 극한의 성능이 필요한 선착순 이벤트에 추천 (단, DB 동기화 이슈 고려).

---

## 주요 성과

| 항목 | 성과 |
|------|------|
| **정량적 데이터** | 4가지 방식의 Latency, TPS, Success Rate 비교 데이터 확보 |
| **의사결정 기준** | 상황별 최적의 기술 선택을 위한 가이드라인 수립 |
| **PoC 완수** | "어떤 방식이 가장 좋은가?"에 대한 답을 데이터로 증명 |

---

## 생성된 파일

| 파일 | 설명 |
|------|------|
| `docs/performance-test-result.md` | 상세 성능 테스트 결과 리포트 |
| `docs/practical-guide.md` | 실무 도입을 위한 전략 가이드 |

---

## Sprint 3 종료

이로써 Sprint 3의 모든 Iteration이 완료되었습니다.
- **Sprint Goal:** k6 부하 테스트를 통한 정량 지표 측정 및 가이드 작성 (달성 완료)
- **Next Step:** 프로젝트 종료 또는 추가 고도화 (DB 동기화 패턴 구현 등)

---

## 사용자 피드백

> 이 섹션은 Iteration 완료 후 사용자가 작성합니다.

### 성능 결과에 대한 의견
- (피드백 입력)

### 가이드 문서 피드백
- (피드백 입력)

### 기타 의견
- (피드백 입력)
