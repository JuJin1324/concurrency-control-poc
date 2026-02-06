# ADR-006: 왜 Best Fit Verification 접근법을 선택했는가?

**날짜:** 2026-02-06
**상태:** 승인됨 (Accepted)
**Sprint:** Sprint 7

## 배경 (Context)

Sprint 0-5를 통해 4가지 동시성 제어 방식(Pessimistic Lock, Optimistic Lock, Redis Distributed Lock, Lua Script)을 구현하고 성능을 측정했습니다.

**기존 테스트 결과 (Performance V2):**
- 모든 시나리오에서 **Lua Script**가 압도적 1위
- Redis Lock은 모든 지표에서 최하위
- Pessimistic/Optimistic은 중간 성적

**Sprint 6 회고에서 발견한 문제점:**
> *"기존 테스트는 '누가 제일 빠른가?'라는 **절대적 기준**으로만 접근했습니다. 마치 '트럭과 스포츠카를 F1 서킷에서 경주시킨 것'과 같았습니다."*

**핵심 질문:**
- "Lua Script가 모든 상황에서 최선인가?"
- "Redis Lock은 정말 쓸모없는가?"
- "비즈니스 맥락에 따른 최적 선택은 무엇인가?"

---

## 결정 (Decision)

Sprint 7에서는 **절대적 성능 비교**가 아닌 **상황별 최적화(Best Fit Verification)** 접근법을 채택합니다.

### 핵심 원칙

> **"은탄환은 없다. 적재적소(Right Tool for Right Job)만 있을 뿐."**

각 동시성 제어 방식이 **필승**하는 시나리오를 설계하고, 해당 시나리오에서 예상 우승자가 실제로 우위를 점하는지 검증합니다.

### 4가지 Best Fit 시나리오 (효율적 접근)

| 시나리오 | 핵심 평가 축 | 비교 대상 | 예상 우승자 |
|----------|-------------|-----------|-------------|
| **Complex Transaction** | 🛡️ 안정성 | Pessimistic vs Optimistic (2개) | Pessimistic |
| **Read-Heavy Workload** | ⚡ 성능 | Optimistic vs Pessimistic (2개) | Optimistic |
| **Resource Protection** | 🏥 회복력 | Redis Lock vs DB Locks (2-3개) | Redis Lock |
| **Atomic Counter** | 🚀 극한 성능 | 기존 데이터 활용 (Sprint 5) | Lua Script |

**핵심 원칙:**
- 시나리오마다 의미 있는 비교 대상만 선정 (2-3개)
- 다양한 평가 축 활용 (성능뿐 아니라 안정성, 회복력)
- 기존 데이터 재활용으로 효율성 확보

---

## 근거 (Rationale)

### 1. 현실 세계의 비즈니스 맥락 반영

실무에서는 "가장 빠른 기술"보다 **"상황에 맞는 기술"**을 선택합니다.

**사례 (Sprint 6 Case Study):**
- **은행:** 느려도 안전한 Pessimistic Lock 선택 (신뢰 > 속도)
- **Notion:** 락 없는 Optimistic Lock 선택 (UX > 정합성)
- **배민:** 극한 성능의 Lua Script 선택 (속도 > 복잡도)

각 선택에는 **Trade-off**가 있으며, 비즈니스 목표에 따라 최적 선택이 달라집니다.

### 2. 기술적 다양성 입증

**절대적 비교의 한계:**
- 단순 재고 차감 시나리오만으로는 각 기술의 **고유 강점**을 드러낼 수 없음
- Redis Lock이 "비효율적"으로 평가절하 되었지만, **DB 보호** 관점에서는 필수

**Best Fit 접근의 가치:**
- 각 기술이 빛나는 **특수 상황** 제시
- 기술 선택의 **판단 기준** 제공

### 3. 시니어 엔지니어의 통찰력 입증

**주니어 vs 시니어 접근:**
- **주니어:** "이 기술이 빠르니까 이걸 씁시다."
- **시니어:** "우리 상황에서는 이 기술이 적합합니다. 왜냐하면..."

**Sprint 7의 목표:**
> 단순 구현 능력을 넘어, **상황 판단 능력**과 **Trade-off 분석 능력**을 증명합니다.

### 4. 포트폴리오 차별화

**일반적인 PoC:**
- "4가지 방법을 구현했고, Lua가 가장 빠릅니다."

**우리 PoC:**
- "4가지 방법을 구현했고, **각각의 최적 사용 시나리오**를 검증했습니다."
- "비즈니스 요구사항에 따른 **의사결정 가이드**를 제공합니다."

---

## 결과 (Consequences)

### 긍정적 효과 (Positive)

1. **현실성 (Realism):** 실무 의사결정 과정을 시뮬레이션
2. **깊이 (Depth):** 각 기술의 고유 특성을 깊이 이해
3. **완성도 (Completeness):** 단순 벤치마크를 넘어 실무 가이드 제공
4. **차별성 (Differentiation):** 시니어 엔지니어의 사고방식 증명

### 도전 과제 (Challenges)

1. **시나리오 설계 복잡도:** 각 방식이 명확히 우위를 점하는 시나리오 설계 필요
2. **구현 비용:** 기존 단순 재고 차감을 넘어 복합 트랜잭션, Read-Heavy 등 구현 필요
3. **검증 기준:** "2배 이상 성능 차이" 또는 "결정적 차이" 등 명확한 기준 필요

### 완화 전략 (Mitigation)

- **Sprint 6 인사이트 활용:** 실무 사례 분석 결과를 시나리오 설계에 반영
- **기존 인프라 재사용:** 새로운 코드는 최소화하고 테스트 스크립트 중심으로 검증
- **명확한 검증 매트릭스:** 각 시나리오별 성공 기준을 사전 정의

---

## 검증 방법 (Verification)

각 시나리오에서 예상 우승자가 **명확한 우위**를 보여야 합니다:

**정량적 기준:**
- 성능 지표에서 **2배 이상** 차이 또는
- 정성적 지표에서 **결정적 차이** (예: 시스템 다운 여부)

**실패 조건:**
- 예상 우승자가 2위 이하로 밀림
- 성능 차이가 통계적으로 유의미하지 않음 (< 20%)

---

## 대안 (Alternatives Considered)

### Option 1: 절대적 성능 비교 지속 (기각)
- **장점:** 구현 간단, 명확한 순위
- **단점:** 현실성 부족, Redis Lock 등의 가치 입증 불가
- **기각 이유:** Sprint 6 회고에서 "절대적 비교의 함정" 발견

### Option 2: 모든 조합 테스트 (기각)
- **장점:** 완전한 데이터
- **단점:** 시간 부족 (N x M 조합 폭발)
- **기각 이유:** PoC 범위 초과, 1-2달 제약

### Option 3: Best Fit Verification (선택)
- **장점:** 현실성, 깊이, 차별성
- **단점:** 시나리오 설계 복잡도
- **선택 이유:** PoC 목표(이직용)에 가장 부합

---

## 관련 문서 (Related Documents)

- `docs/planning/bestfit-scenarios.md` - 4가지 시나리오 상세 설계
- `docs/reports/performance-v2.md` - Sprint 0-5 절대적 성능 비교 결과
- `docs/reports/case-study-v1.md` - Sprint 6 실무 사례 분석
- `.agile/sprints/sprint-6/retrospective.md` - Sprint 6 회고

---

## 변경 이력 (Change History)

- 2026-02-06: 초안 작성 및 승인
