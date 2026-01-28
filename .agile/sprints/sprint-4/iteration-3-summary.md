# Iteration 3 Summary: 테스트 인프라 재구성

**Sprint:** Sprint 4 - 문서화 + 프로젝트 완성
**Iteration:** 3/5
**완료일:** 2026-01-28
**상태:** ✅ 완료

---

## 1. 완료한 작업

### 1.1 k6 스크립트 재구성 (메서드별 → 시나리오별)

**변경 이유:**
- 기존: `pessimistic-test.js`, `optimistic-test.js`, `redis-lock-test.js`, `lua-script-test.js` (메서드별 4개)
- 문제: 각 메서드마다 동일한 시나리오를 반복해서 작성 → 유지보수 어려움
- 해결: 시나리오별로 통합하고, 환경변수 `METHOD`로 메서드 선택

**변경 사항:**
- [x] `stress-test.js` → `high-test.js` (rename)
- [x] 최종 스크립트 구조 (시나리오별):
  - `high-test.js` - High Load (재고 1,000개)
  - `extreme-test.js` - Extreme Load (재고 10,000개)
  - `hell-test.js` - Hell Test (재고 100개 vs 5,000 VUs)
  - `recovery-test.js` - Recovery Test (회복력 검증)

**사용 예시:**
```bash
# 동일한 스크립트, 다른 메서드
k6 run -e METHOD=pessimistic k6-scripts/high-test.js
k6 run -e METHOD=lua-script k6-scripts/high-test.js
```

---

### 1.2 Makefile 개선 (자동화 제거)

**변경 이유:**
- 기존: `make test` 명령어로 전체 자동화
- 문제점:
  1. Recovery Test는 Extreme Load **직후** 실행해야 의미가 있음
  2. `make test`가 내부에서 `make clean && make up`을 하면 연속성이 깨짐
  3. 사용자가 특정 메서드/시나리오만 테스트하고 싶을 때 유연성 부족
  4. 과도한 자동화 = 선택권 제거

**변경 사항:**
- [x] `.PHONY`에서 `test` 제거
- [x] `make test` 명령어 전체 제거 (133-169줄 삭제)
- [x] Makefile의 역할 재정의: **준비 명령어만 제공**

**최종 Makefile 구조:**
```makefile
# 데이터 초기화
make reset        # 100개
make reset-1k     # 1,000개
make reset-10k    # 10,000개

# 결과 확인
make show-db      # MySQL 재고 확인
make show-redis   # Redis 재고 확인

# 테스트 유틸리티
make warmup       # 시스템 예열 (METHOD 파라미터)

# 인프라 관리
make up / down / clean

# 시스템 정보
make ps / stats
```

**철학:**
- ✅ 반복적인 설정 자동화 (reset, show)
- ✅ 사용자가 테스트 순서/조합을 직접 결정
- ❌ 테스트 실행 순서 강제하지 않음 (Recovery 같은 순차 테스트 가능)

---

## 2. 생성/수정된 파일

### 변경된 파일
| 파일 경로 | 변경 내용 |
|-----------|-----------|
| `k6-scripts/stress-test.js` → `k6-scripts/high-test.js` | Rename (시나리오명 명확화) |
| `Makefile` | `make test` 제거, `make warmup` 추가, `.PHONY` 정리 |

### 파일 구조 (k6-scripts/)
```
k6-scripts/
├── high-test.js       ✅ (High Load - 재고 1,000개)
├── extreme-test.js    ✅ (Extreme Load - 재고 10,000개)
├── hell-test.js       ✅ (Hell Test - 재고 100개 vs 5,000 VUs)
├── recovery-test.js   ✅ (Recovery Test - 회복력 검증)
├── hello-world.js     (학습용)
└── template.js        (템플릿)
```

---

## 3. 주요 결정사항

### 결정 1: 자동화 제거
- **선택:** `make test` 제거
- **이유:**
  - Recovery Test는 Extreme 직후 실행해야 의미 있음 (도커 재시작하면 무의미)
  - 사용자에게 선택권 제공 (특정 메서드만 테스트)
  - 과도한 자동화는 프로젝트 범위 벗어남 (brainstorm.md 철학: "하나를 완벽하게")
- **트레이드오프:**
  - 장점: 유연성 증가, 순차 의존 테스트 가능
  - 단점: 사용자가 명령어를 직접 조합해야 함 (하지만 README에 제공 예정)

### 결정 2: 시나리오별 스크립트 구조
- **선택:** 메서드별 → 시나리오별
- **이유:**
  - 코드 중복 제거 (4개 → 4개이지만 내용 통합)
  - 환경변수 `METHOD`로 메서드 선택
  - 유지보수성 향상
- **트레이드오프:**
  - 장점: DRY (Don't Repeat Yourself), 유지보수 쉬움
  - 단점: 환경변수 전달 필요 (하지만 간단함)

---

## 4. 다음 단계 준비

**README.md 작성 시 포함할 내용:**

### Quick Test (Individual)
```bash
make reset && k6 run -e METHOD=lua-script k6-scripts/hell-test.js
```

### Hell Test (5,000 VUs)
```bash
# 1. Prepare
make reset && k6 run -e METHOD=lua-script --vus 100 --iterations 2000 k6-scripts/hell-test.js > /dev/null

# 2. Execute
k6 run -e METHOD=lua-script -e VUS=5000 -e ITERATIONS=5000 k6-scripts/hell-test.js

# 3. Verify
make show-db  # Expected: quantity = 0
```

### Recovery Test (Resilience)
```bash
# 1. Extreme Load
make reset-10k && k6 run -e METHOD=lua-script k6-scripts/extreme-test.js

# 2. Recovery (바로 이어서, 도커 재시작 하지 말 것!)
k6 run -e METHOD=lua-script --vus 100 --iterations 100 k6-scripts/hell-test.js
```

> 💡 **Tip:** 도커를 재시작하면 Recovery 테스트의 의미가 사라집니다.

---

## 5. 테스트 결과

**변경 후 검증:**
- [x] k6 스크립트 rename 확인 (`ls k6-scripts/`)
- [x] Makefile에서 `test` 명령어 제거 확인 (`grep "^test:" Makefile`)
- [x] Makefile에 `warmup` 명령어 추가 확인 (`grep "^warmup:" Makefile`)
- [x] `.PHONY` 정리 확인 (info 제거, warmup 추가)

**검증 결과:**
```
✅ high-test.js 존재
✅ test 명령어 없음 (정상)
✅ warmup 명령어 추가됨
✅ .PHONY 정리 완료
```

---

## 사용자 피드백

> 아래에 코드, 아키텍처, 또는 진행 방식에 대한 수정 요청이나 의견을 작성해주세요.

### 수정 요청사항
<!--
예시:
- [ ] CLAUDE.md도 업데이트 필요
- [ ] k6 스크립트 주석 추가 필요
-->

### 기타 의견
<!--
예시:
- 이제 README.md 작업 시작하면 됨
- Iteration 4 (프로젝트 구조 정리)는 생략 가능할 듯
-->

---

## 다음 Iteration 준비

**Iteration 4 계획 (원래):** 프로젝트 구조 정리 (TODO.md 반영)
- 문서 파일 정리 (brainstorm.md, how-diagram.md 등)
- Docker 파일 구조 정리 (선택)

**대안:** Iteration 4를 건너뛰고 Iteration 5 (README 고도화)로 바로 진행할 수도 있음.

**시작 전 확인사항:**
- [ ] Iteration 3 변경사항 커밋 완료
- [ ] 다음 Iteration 방향 결정 (사용자와 논의)
