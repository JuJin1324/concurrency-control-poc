# Sprint 7: 상황별 최적화 검증 (Best Fit Verification)

**기간:** 2026-02-06 ~ 2026-02-19 (14일)
**시작일:** 2026-02-06
**목표:** "절대적인 성능 우위는 없다"는 것을 증명하기 위해, 각 방식이 가장 빛나는 'Best Fit' 시나리오를 설계하고 실제 성능으로 검증한다.
**Phase:** Phase 5 (Deep Dive - Verification)

---

## Sprint Goal

> **"은탄환은 없다. 적재적소(Right Tool for Right Job)만 있을 뿐."**
>
> 각 동시성 제어 방식이 **주인공이 되는 무대**를 만들어, 기술의 우열이 아닌 '상황에 따른 최적 선택'을 실제 성능으로 증명한다.

---

## 워크플로우 철학

> **Sprint 6의 통찰을 실전으로 증명 - 효율적 접근법**

**Sprint 6에서 배운 것:**
- ❌ 절대적 비교의 함정 (F1 서킷에서 트럭과 스포츠카 경주)
- ❌ 성능 측정에만 치중 → 안정성, 회복력 등 다른 가치 간과
- ✅ 비즈니스 맥락에 따른 최적 선택

**Sprint 7에서 증명할 것:**
- 각 방식이 **필승**하는 시나리오 설계
- **다양한 평가 축**: 성능/안정성/회복력/복잡도
- **효율적 접근**: 의미 있는 비교 대상만 선정 (2-3개)
- **기존 데이터 활용**: Sprint 5 결과 재활용

---

## 📊 시나리오 개요

| 시나리오 | 핵심 평가 축 | 비교 대상 | 핵심 지표 | 예상 우승자 |
|----------|-------------|-----------|-----------|-------------|
| **1. Complex Transaction** | 🛡️ 안정성 | Pessimistic vs Optimistic | Rollback Count | Pessimistic |
| **2. Low Contention Update** | ⚡ 성능 | Optimistic vs Pessimistic | TPS, Retry Count | Optimistic |
| **3. Resource Protection** | 🏥 회복력 | Redis Lock vs DB Locks | System Uptime | Redis Lock |
| **4. Atomic Counter** | 🚀 극한 성능 | 기존 Sprint 5 데이터 활용 | TPS | Lua Script |

**효율성:**
- ⏱️ 전체 비교 시 10+ 일 → **효율적 접근 4-6일**
- 의미 없는 측정 제거, 핵심 비교에 집중

---

## Tasks

### Iteration 1: Best Fit 시나리오 설계 및 구현 준비

**목표:** 각 방식의 강점이 극대화되는 4가지 시나리오를 설계하고 구현 방향을 확정한다.

#### US-7.1: 시나리오 설계 및 ADR 작성
- [x] 4가지 Best Fit 시나리오 상세 정의
  - Pessimistic Lock: "Complex Transaction" (복합 ACID 트랜잭션)
  - Optimistic Lock: "Low Contention Update" (분산 업데이트, 충돌률 1-5%)
  - Redis Distributed Lock: "Resource Protection" (DB 포화 상태 보호)
  - Lua Script: "Atomic Counter" (기존 Sprint 5 데이터 활용)
- [x] 각 시나리오의 가설 및 검증 지표 정의
- [x] ADR-006: Best Fit Verification 접근 방법 및 근거 작성
- [x] 효율적 접근법 반영 (의미 있는 비교 대상만 선정)

**🔍 Checkpoint 1:** 시나리오 설계 및 가설 확정

#### US-7.2: 테스트 환경 설계
- [x] 기존 리소스 현황 파악 (Java 구현체, k6 스크립트)
- [x] 각 시나리오별 k6 테스트 스크립트 설계
  - **k6 구조 결정:** 시나리오 중심 (bestfit/ 디렉토리)
  - Scenario 4는 기존 contention.js 활용 (bestfit/에 포함 안 함)
- [x] 측정 지표 정의 (TPS, Latency, Rollback Count, System Uptime)
- [x] 비교 기준선(Baseline) 설정
- [x] 구현 우선순위 확정 (2 → 4 → 3 → 1)

---

### Iteration 2: Best Fit 시나리오 구현 및 검증 (1/2)

**목표:** Pessimistic Lock과 Optimistic Lock의 Best Fit 시나리오를 구현하고 검증한다.

#### US-7.3: Pessimistic Lock - "Complex Transaction" 구현
- [ ] 복합 트랜잭션 시나리오 구현
  - 재고 차감 + 포인트 사용 + 결제 이력 생성
  - ACID 보장 및 롤백 처리
- [ ] k6 테스트 스크립트 작성
- [ ] 성능 측정 및 결과 분석
- [ ] 가설 검증: "복잡한 트랜잭션에서는 Pessimistic이 가장 안정적"

**Acceptance Criteria:**
- 복합 트랜잭션에서 Pessimistic이 타 방식 대비 안정성 우위 증명
- 롤백 비용 비교 데이터 확보

#### US-7.4: Optimistic Lock - "Low Contention Update" 구현
- [ ] 분산 업데이트 시나리오 구현
  - 100개 상품, 1000명이 랜덤하게 구매
  - 충돌률 1-5% (일부 인기 상품에서만 발생)
  - 기존 재고 로직 활용, 테스트 스크립트만 변경
- [ ] k6 테스트 스크립트 작성
- [ ] 성능 측정 및 결과 분석
- [ ] 가설 검증: "수요 분산 환경에서는 Optimistic이 2배 이상 빠름"

**Acceptance Criteria:**
- 충돌 희귀 환경(1-5%)에서 Optimistic이 Pessimistic 대비 TPS 우위 증명
- 재시도 횟수 및 락 오버헤드 비교 데이터 확보

**✅ Iteration 2 완료 조건:**
- Pessimistic, Optimistic 각각의 Best Fit 증명 완료
- 성능 데이터 수집 완료

---

### Iteration 3: Best Fit 시나리오 구현 및 검증 (2/2)

**목표:** Redis Distributed Lock과 Lua Script의 Best Fit 시나리오를 구현하고 검증한다.

#### US-7.5: Redis Distributed Lock - "Resource Protection" 구현
- [ ] DB 포화 상태 시뮬레이션
  - DB CPU 부하 100% 상황 재현
  - Redis를 통한 유입 제어(Throttling) 구현
- [ ] k6 테스트 스크립트 작성
- [ ] 성능 측정 및 결과 분석
- [ ] 가설 검증: "DB 포화 시 Redis Lock이 시스템 안정성 보장"

**Acceptance Criteria:**
- Busy DB 상황에서 Redis Lock의 보호 효과 증명
- DB 다운 방지 및 안정적 처리량 유지 확인

#### US-7.6: Lua Script - "Atomic Counter" 구현
- [ ] 단순 고속 처리 시나리오 구현
  - 로직 단순화 (단순 카운터 연산)
  - 대규모 트래픽 (100만 건) 처리
- [ ] k6 테스트 스크립트 작성
- [ ] 성능 측정 및 결과 분석
- [ ] 가설 검증: "단순 로직에서는 Lua Script가 압도적 성능"

**Acceptance Criteria:**
- 단순 연산에서 Lua Script의 압도적 TPS 우위 증명
- 타 방식 대비 성능 격차 정량 측정

**✅ Iteration 3 완료 조건:**
- Redis Lock, Lua Script 각각의 Best Fit 증명 완료
- 4가지 방식 전체 검증 완료

---

### Iteration 4: 결과 통합 및 문서화

**목표:** 검증 결과를 통합하여 최종 리포트를 작성하고, 포트폴리오를 완성한다.

#### US-7.7: Best Fit Verification 리포트 작성
- [ ] 개별 시나리오 리포트 작성 (4개)
  - **템플릿 기준:** `docs/reports/test-report-template.md`
  - `docs/reports/bestfit-complex-transaction.md` (Pessimistic)
  - `docs/reports/bestfit-read-heavy.md` (Optimistic)
  - `docs/reports/bestfit-resource-protection.md` (Redis Lock)
  - `docs/reports/bestfit-atomic-counter.md` (Lua Script)
- [ ] 통합 리포트 작성
  - **템플릿 기준:** `docs/reports/performance-v2.md` 구조 참조
  - `docs/reports/best-fit-verification.md` 작성
  - Executive Summary: 4가지 시나리오별 우승자 정리
  - 성능 비교 매트릭스 (시나리오 × 방식)
  - 가설 검증 결과 및 인사이트
  - 개별 리포트 링크
- [ ] 시각화: 시나리오별 성능 차트 및 그래프

#### US-7.8: README 및 기존 문서 최종 업데이트
- [ ] README 업데이트
  - Best Fit Verification 섹션 추가
  - 마일스톤에 Sprint 7 추가
  - **[NEW]** "프로젝트 접근 방법" 섹션 추가
    - 애자일 스프린트 기반 진행 (Sprint 0-7)
    - MVP 사고방식: 작은 단위로 검증하며 확장
    - 선(先) 구현, 후(後) 연구의 시너지 효과
    - 시니어 엔지니어의 문제 해결 접근법
- [ ] `docs/reports/performance-v2.md` 업데이트
  - Best Fit 시나리오 섹션 추가
  - 또는 best-fit-verification.md 링크 추가
- [ ] `docs/reports/practical-guide.md` 업데이트
  - **템플릿 기준:** 기존 구조 유지
  - Section 1 (의사결정 매트릭스): Best Fit 시나리오 반영
  - Section 2 (방식별 가이드): Best Fit 검증 결과 추가
  - Section 3 (인프라 사이징): 필요 시 업데이트

#### US-7.9: 프로젝트 최종 회고 작성
- [ ] `.agile/sprints/sprint-7/retrospective.md` 작성
- [ ] Sprint 0-7 전체 여정 정리
  - 각 Sprint의 목표와 성과
  - Phase별 진화 과정
- [ ] PoC 목표 달성도 평가
- [ ] **[NEW]** 애자일 & MVP 접근법 회고 (면접 어필 포인트)
  - 왜 Sprint 단위로 나눴는가?
  - MVP 마인드셋: 완벽보다 빠른 검증
  - 선(先) 구현, 후(後) 연구의 학습 효과
  - 작은 단위 반복(Iteration)의 가치
  - 시니어 엔지니어로서의 성장 포인트
- [ ] 이직 시장 어필 포인트 최종 정리
  - 기술적 깊이 (4가지 방식 구현 + 최적화)
  - 운영 관점 (실무 사례 분석 + Best Fit 검증)
  - 프로세스 역량 (애자일 스프린트 + MVP)
- [ ] 배운 것 (Lessons Learned)
  - 기술적 인사이트
  - 프로젝트 관리 인사이트
- [ ] 향후 확장 가능성 (Optional)

**✅ Iteration 4 완료 조건:**
- Best Fit Verification 리포트 완성
- 모든 문서 최종 업데이트 완료
- 프로젝트 회고 작성 완료

---

## Sprint 7 Definition of Done

### Iteration 1: 시나리오 설계 및 준비
- [ ] 4가지 Best Fit 시나리오 상세 정의 완료
- [ ] 가설 및 검증 지표 확정
- [ ] ADR 작성 완료
- [ ] 테스트 환경 설계 완료

### Iteration 2: Pessimistic & Optimistic 검증
- [ ] Complex Transaction 시나리오 검증 완료
- [ ] Read-Heavy 시나리오 검증 완료
- [ ] 성능 데이터 수집 완료

### Iteration 3: Redis & Lua 검증
- [ ] Resource Protection 시나리오 검증 완료
- [ ] Atomic Counter 시나리오 검증 완료
- [ ] 4가지 방식 전체 검증 완료

### Iteration 4: 통합 및 문서화
- [ ] Best Fit Verification 리포트 완성
- [ ] README 최종 업데이트
- [ ] 프로젝트 최종 회고 작성

### 최종 검증
- [ ] 4가지 Best Fit 시나리오 모두 검증 완료
- [ ] 모든 가설 검증 결과 문서화
- [ ] 포트폴리오 이직 시장 제출 가능 상태
- [ ] GitHub 프로필 핀 고정 준비 완료

---

## Notes & Expected Outcomes

### 핵심 가설 (Hypothesis)

1. **Pessimistic Lock (Complex Transaction):**
   - 복잡한 ACID 트랜잭션에서는 비관적 락이 가장 안정적
   - 롤백 비용이 적고 데이터 정합성 보장

2. **Optimistic Lock (Read-Heavy):**
   - 충돌이 드문 읽기 위주 환경에서는 낙관적 락이 가장 효율적
   - 락 오버헤드 제거로 높은 TPS 달성

3. **Redis Distributed Lock (Resource Protection):**
   - DB 포화 상태에서는 Redis 락이 시스템 안정성 보장
   - Throttling을 통한 DB 보호 효과 입증

4. **Lua Script (Atomic Counter):**
   - 단순 로직에서는 Lua 스크립트가 압도적 성능
   - I/O 최소화로 최고 TPS 달성

### 기대 효과

- ✅ 기술의 우열이 아닌 **상황별 최적 선택** 증명
- ✅ 포트폴리오 차별화 (단순 구현 → 상황별 최적화 검증)
- ✅ 시니어 엔지니어의 통찰력 입증
- ✅ 이직 시장 어필력 극대화

---

## Blockers

- 없음

---

## References

- Sprint 6 Retrospective: `.agile/sprints/sprint-6/retrospective.md`
- how-diagram.md: `docs/planning/how-diagram.md`
- Performance Report V2: `docs/reports/performance-v2.md`
- Practical Guide: `docs/reports/practical-guide.md`
