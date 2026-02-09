---
name: sprint-status
description: "현재 Sprint의 진행 상황을 시각화하여 보여줍니다. 완료/진행 중/대기 중 태스크를 집계하고 진행률을 계산합니다."
---

# Sprint Status

## 목적

현재 Sprint의 진행 상황을 명확히 시각화하여, 어디까지 진행했는지 한눈에 파악할 수 있도록 돕습니다.

**해결하는 문제:**
- ❌ 어디까지 진행했는지 불명확
- ❌ 남은 작업량 파악 어려움
- ❌ 진행 상황 시각화 도구 없음

**제공하는 가치:**
- ✅ 진행률 시각화 → 명확한 현황 파악
- ✅ 태스크 상태 집계 → 다음 할 일 명확
- ✅ 남은 기간 확인 → 시간 관리

---

## 파일 구조

```
.agile/
├── current-sprint.txt          # 현재 Sprint 번호
└── sprints/
    └── sprint-N/
        └── plan.md            # Sprint 계획 (진행 상황 포함)
```

---

## 작동 방식

### Step 1: 현재 Sprint 확인

- `.agile/current-sprint.txt` 읽기
- 현재 Sprint 번호 확인
- Sprint가 없으면 → `/sprint-start` 권장

### Step 2: Sprint Plan 파일 파싱

**파일 읽기:** `.agile/sprints/sprint-N/plan.md`

**태스크 상태 집계:**
- `[x]` → Completed
- `[ ]` (+ "Status: In Progress" 또는 "🔄") → In Progress
- `[ ]` (기본) → To Do

**진행률 계산:**
```
진행률 = (Completed / Total) * 100
```

**기간 확인:**
- Sprint 시작일/종료일 파싱
- 남은 기간 계산

### Step 3: 시각화 출력

```
📊 Sprint N 진행 상황

🎯 Goal: [Sprint 목표]
📅 기간: {시작일} ~ {종료일}
⏰ 남은 기간: X일

📈 Progress: ████████░░ 60% (3/5)

✅ Completed (2)
  - Stock Domain 모델 구현
  - Pessimistic Lock Service 구현

🔄 In Progress (1)
  - Optimistic Lock Service 구현

📋 To Do (2)
  - REST API 구현
  - 통합 테스트 작성

---

💡 Tip: plan.md 파일을 직접 수정해서 진행 상황을 업데이트하세요!
     완료한 태스크는 [ ]를 [x]로 변경하면 됩니다.
```

---

## 태스크 상태 파싱 규칙

### 1. Completed (완료)
```markdown
- [x] 태스크 이름
```

### 2. In Progress (진행 중)

**방법 1:** 명시적 표시
```markdown
- [ ] 태스크 이름
  - Status: In Progress
```

**방법 2:** 이모지 사용
```markdown
- [ ] 🔄 태스크 이름
```

**방법 3:** 섹션 기반
```markdown
### 🔄 In Progress
- [ ] 태스크 이름
```

### 3. To Do (대기 중)
```markdown
- [ ] 태스크 이름
```

---

## 진행률 시각화 포맷

### Progress Bar 생성

**10칸 기준:**
```
0-9%   : ░░░░░░░░░░  0%
10-19% : █░░░░░░░░░ 10%
20-29% : ██░░░░░░░░ 20%
...
90-99% : █████████░ 90%
100%   : ██████████ 100%
```

### 완료율 계산

```
완료율 = (완료한 태스크 수 / 전체 태스크 수) * 100
```

**예시:**
- 전체: 10개
- 완료: 6개
- 진행 중: 2개
- 대기: 2개
- **완료율: 60%**

---

## 추가 정보 제공

### US별 진행 상황 (선택)

Sprint Plan에 US 구조가 있는 경우:

```
📊 Sprint 0 진행 상황

🎯 Goal: 플랫폼 엔지니어링 + 아키텍처 설계

📈 Overall Progress: ██████████ 100% (15/15)

---

### Sprint 시작 ✅ (100%)
- ✅ ADR-001: 4가지 방법 선택 근거
- ✅ ADR-002: PoC 범위 축소 근거
- ✅ ADR-003: 시각화 우선 워크플로우

### US-0.1: 인프라 환경 구축 ✅ (100%)
- ✅ 인프라 다이어그램 완성
- ✅ ADR-004 완성
- ✅ Docker Compose 구현

### US-0.2: 프로젝트 스캐폴딩 ✅ (100%)
- ✅ 애플리케이션 구조 다이어그램 완성
- ✅ Spring Boot 프로젝트 생성

### US-0.3: 문서화 ✅ (100%)
- ✅ README 작성
- ✅ C4 Diagram 작성
```

### Blockers 표시

plan.md에 Blockers 섹션이 있으면:

```
⚠️ Blockers (1)
- Docker 컨테이너 실행 오류 (MySQL 포트 충돌)
```

---

## 사용자 가이드

### 진행 상황 업데이트 방법

**1. plan.md 파일 직접 수정**
```markdown
# Sprint 1: DB Lock 구현

## Tasks

### ✅ Completed
- [x] Stock Domain 모델 구현 (2026-01-06)
- [x] Pessimistic Lock Service 구현 (2026-01-07)

### 🔄 In Progress
- [ ] Optimistic Lock Service 구현
  - Status: 50% (Version 컬럼 추가 완료, Retry 로직 작성 중)

### 📋 To Do
- [ ] REST API 구현
- [ ] 통합 테스트 작성
```

**2. `/sprint-status` 실행**
- AI가 파일을 파싱하여 현황 시각화

---

## 중요 원칙

### 1. 사용자 주도권 유지

- ✅ AI는 읽기만 함 (plan.md 수정 안 함)
- ✅ 사용자가 직접 [ ]를 [x]로 변경
- ✅ AI는 시각화만 제공

### 2. 실시간 반영

- plan.md를 수정하면 즉시 `/sprint-status`에 반영
- 별도 동기화 작업 불필요

---

## ✅ AI가 해야 할 것

- plan.md 파일 읽기 및 파싱
- 태스크 상태 집계 (Completed, In Progress, To Do)
- 진행률 계산 및 Progress Bar 생성
- 남은 기간 계산
- 시각화된 현황 출력

## ❌ AI가 하지 말아야 할 것

- plan.md 파일 수정
- 사용자 대신 태스크 상태 변경
- 임의로 태스크 추가/삭제
- 진행 상황 평가 또는 조언 (순수하게 시각화만)

---

## 다음 스텝

진행 상황 확인 후:

1. **plan.md 파일 수정** - 완료한 태스크 체크
2. **`/sprint-status`** - 다시 현황 확인
3. **`/sprint-complete`** - Sprint 완료 시 회고 작성

---

**버전:** 3.0.0
**최종 업데이트:** 2026-02-09
**변경 사항:**
- **Breaking:** Iteration 중간 계층 제거, Sprint > US 2단계 구조로 단순화
- US별 진행 상황 표시로 변경
