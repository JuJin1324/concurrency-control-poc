# Iteration 1 Summary: k6 개념 학습 및 환경 설정

**Sprint:** Sprint 3 - k6 부하 테스트 + 최종 성능 비교 분석
**Iteration:** 1/3
**완료일:** 2026-01-27
**상태:** ✅ 완료

---

## 구현 내용

### 1. k6 개념 학습 및 기술 문서 작성

**위치:** `docs/technology/k6-overview.md`

- **k6 채택 이유:** Go 기반의 고성능 엔진, JavaScript 기반의 개발자 친화적 스크립팅, CI/CD 통합 용이성.
- **핵심 개념 정리:** VU(가상 사용자), RPS vs TPS, Metric(Trend, Counter 등), Threshold(성공 기준).
- **성능 측정 방법론:** TPS, Latency(p50, p95, p99), Success Rate의 중요성 및 4가지 테스트 종류(Load, Stress, Spike, Soak) 정리.
- **공정한 비교 원칙:** 리소스 제한, Warm-up, 데이터 초기화 절차 수립.

---

### 2. k6 설치 및 환경 검증

- **설치:** macOS 환경에서 `brew install k6`를 통해 설치 완료 (버전 1.5.0).
- **검증:** `k6-scripts/hello-world.js`를 작성하여 `https://test.k6.io`를 대상으로 5초간 부하 테스트 수행 성공.
- **결과:** `checks_succeeded: 100.00%` 확인 및 CLI 출력 정상 동작 확인.

---

### 3. k6 스크립트 템플릿 작성

**위치:** `k6-scripts/template.js`

- **부하 시나리오(Stages):** 30초 Ramp-up -> 1분 유지 -> 30초 Ramp-down 구조 설계.
- **성공 기준(Thresholds):**
  - 에러율 1% 미만 (`http_req_failed < 0.01`)
  - 95% 응답 시간 500ms 미만 (`http_req_duration p(95) < 500`)
- **공통 로직:** JSON 페이로드 처리, 헤더 설정, 응답 상태 및 재고 필드 검증 로직 포함.

---

## 사용자 가이드 (How-to)

### 1. k6 설치 확인 및 수동 검증
터미널에서 다음 명령어로 설치 여부를 확인할 수 있습니다.
```bash
k6 version
# 출력 예시: k6 v0.56.0 (go1.23.4, darwin/arm64)
```

만약 설치되어 있지 않다면:
```bash
brew install k6
```

### 2. Hello World 스크립트 실행
작성된 기본 스크립트를 실행하여 동작을 검증합니다.
```bash
k6 run k6-scripts/hello-world.js
```
- **성공 확인:** 결과 하단의 `█ TOTAL RESULTS` 블록에서 `checks_succeeded...: 100.00%`가 표시되고, `✓ status is 200` 항목이 체크되어 있으면 정상입니다.

### 3. 테스트 유형 명세
이번 Sprint에서 수행할 주 테스트는 **Spike Test (스파이크 테스트)** 성격에 가깝습니다.
- **이유:** "한정된 재고(100개)에 대해 짧은 시간 동안 폭발적인 요청(100/1000명)이 몰리는 선착순 이벤트"를 시뮬레이션하기 때문입니다.
- **목적:** 일반적인 평균 부하(Load Test)보다는, 극한의 경합 상황(Stress/Spike)에서 각 동시성 제어 방식이 어떻게 반응하는지(TPS, Latency, 데이터 정합성)를 검증합니다.

---

## 주요 성과

| 항목 | 성과 |
|------|------|
| **도구 준비** | k6 설치 및 로컬 실행 환경 구축 완료 |
| **지식 자산** | k6 및 성능 측정 방법론 문서화 완료 |
| **표준화** | 향후 작성할 4가지 테스트 스크립트의 표준 템플릿 확보 |

---

## 생성된 파일

| 파일 | 설명 |
|------|------|
| `docs/technology/k6-overview.md` | k6 기술 가이드 및 성능 측정 방법론 |
| `k6-scripts/hello-world.js` | k6 설치 확인용 기본 스크립트 |
| `k6-scripts/template.js` | 부하 테스트 시나리오용 표준 템플릿 |

---

## 다음 Iteration 예고

**Iteration 2: 부하 테스트 환경 구축 및 k6 시나리오 작성**
- 애플리케이션 Docker 컨테이너화 및 리소스 제한 설정
- 4가지 동시성 제어 방식별 k6 스크립트 완성
- 단일 방법(Pessimistic Lock) 예비 테스트 실행

---

## 사용자 피드백

> 이 섹션은 Iteration 완료 후 사용자가 작성합니다.

### 코드/문서 리뷰 의견
- [x] **설치 및 구동 방법 구체화:** 요약 문서에 k6 설치 명령어와 `hello-world.js` 실행 및 성공 확인 방법(checks_succeeded 확인)을 명시함.
- [x] **테스트 유형 명확화:** 이번 Sprint에서 진행하는 테스트가 단순 Load Test가 아닌, 선착순 이벤트를 가정한 **Spike Test**임을 명시하고 그 이유와 목적을 기술함.

### 성능 측정 방법론 제안
- (피드백 입력)

### 기타 의견
- (피드백 입력)
