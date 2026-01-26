# Sprint 2 회고 (Retrospective)

**Sprint:** Sprint 2 - Redis 구현 (Distributed Lock + Lua Script)
**기간:** 2026-01-24 ~ 2026-01-31 (계획: 7일)
**실제 완료:** 2026-01-26 (실제: 3일)
**회고 작성일:** 2026-01-26
**상태:** ✅ 완료 (Completed)

---

## 1. Sprint Goal 달성 여부

> **목표:** Redis Distributed Lock과 Lua Script를 구현하고, DB Lock과 함께 4가지 방법을 동일한 API로 호출할 수 있도록 완성한다.

### 결과: ✅ 목표 달성

Sprint 2의 목표를 100% 달성했습니다:
- Redis Distributed Lock (Redisson) 구현 완료
- Redis Lua Script 구현 완료
- 4가지 동시성 제어 방법 모두 REST API로 통합
- 예비 성능 비교 테스트 완료
- 모든 통합 테스트 통과 (100% Success Rate)

---

## 2. Definition of Done 체크리스트

### Iteration 1: Redis Distributed Lock ✅ 완료 (2026-01-24)
- [x] Redisson 설정 완료
- [x] RedisLockStockService 구현
- [x] RLock 동작 확인 (로그)
- [x] 동시성 통합 테스트 통과 (100% Success Rate 달성)
- [x] Checkpoint 1 통과
- [x] `iteration-1-summary.md` 생성

### Iteration 2: Redis Lua Script ✅ 완료 (2026-01-26)
- [x] Redis 재고 데이터 구조 설계
- [x] Lua Script 작성 및 실행
- [x] LuaScriptStockService 구현
- [x] 동시성 통합 테스트 통과
- [x] Checkpoint 2 통과
- [x] `iteration-2-summary.md` 생성

### Iteration 3: API 통합 및 비교 ✅ 완료 (2026-01-26)
- [x] REST API 확장 (method=redis-lock, lua-script)
- [x] 4가지 방법 예비 비교 테스트
- [x] API 통합 테스트 통과
- [x] `iteration-3-summary.md` 생성
- [x] Redis 심층 탐구 문서(`redis-deep-dive.md`) 작성

### 최종 검증 ✅ 완료
- [x] 4가지 전략 모두 정상 동작
- [x] 동시 요청 시 재고 정합성 보장
- [x] ArchUnit 테스트 통과
- [x] README 업데이트 (Redis 방법 추가)

---

## 3. 완료된 작업 (What Went Well)

### 3.1. 기술적 학습 및 성과

**Redis 원리 이해:**
- Redis의 역사, Single Thread 아키텍처, 원자성(Atomicity) 개념을 대략적으로 이해
- `redis-deep-dive.md` 문서를 통해 Redis의 본질적 특성을 학습
- Distributed Lock (Redisson)과 Lua Script라는 두 가지 다른 접근법의 원리 파악

**Lua Script 학습:**
- Redis에서 Lua Script를 사용하는 방법 습득
- 원자적 연산을 구현하는 실전 경험 획득
- Lock 없이도 동시성 문제를 해결할 수 있는 새로운 접근법 발견

**Redis 성능에 대한 놀라움:**
- 동시성 테스트에서 Redis 기반 방법들의 압도적인 성능 확인
- DB Lock 대비 Redis Lock과 Lua Script의 성능 우위 체감
- 실제 측정 결과가 이론적 예상과 일치함을 확인

### 3.2. 프로세스 및 워크플로우

**체계적인 작업 진행:**
- Iteration 기반 접근으로 작은 단위로 반복
- 각 Iteration별 명확한 목표와 Checkpoint 설정
- 계획 → 구현 → 검증 → 문서화 사이클이 일관되게 유지됨

**쉬운 리뷰 프로세스:**
- Iteration의 좁은 범위 덕분에 리뷰가 매우 용이
- 각 단계별 산출물이 명확해서 검토 포인트가 분명
- Checkpoint를 통한 빠른 피드백 루프

**체계적인 문서화:**
- `iteration-N-summary.md` 파일로 각 Iteration 결과 기록
- `redis-deep-dive.md`로 기술적 심층 학습 문서화
- plan.md의 체크리스트로 진행 상황 실시간 추적

### 3.3. Sprint 1 회고 반영 성과

**Iteration Summary 파일 생성 의무화:**
- Sprint 1의 Action Item이 성공적으로 반영됨
- 3개의 Iteration 모두 summary 파일 생성 완료
- 사용자 피드백 섹션을 통한 양방향 커뮤니케이션 개선

---

## 4. 미흡했던 점 (What Didn't Go Well)

### 4.1. Summary 파일 템플릿 준수 문제

**문제:**
- summary 파일을 처음 만들 때는 템플릿을 잘 지킴
- 수정 또는 내용 추가 요구가 들어오면 템플릿을 지키지 못하는 경우 발생

**원인:**
- AI가 수정 요청 시 전체 구조를 유지하지 못하고 일부만 변경
- 템플릿 일관성 검증 로직 부재

**영향:**
- 문서 구조의 일관성이 저하될 수 있음
- 나중에 summary 파일들을 통합적으로 분석할 때 어려움 발생 가능

**개선 방향:**
- Sprint 3부터는 수정 시에도 전체 템플릿 구조를 유지하도록 명시적 요청
- 또는 템플릿 검증 체크리스트 추가

---

## 5. 배운 점 (Lessons Learned)

### 5.1. Iteration 기반 접근의 압도적 효율성

**핵심 교훈:**
> "여전히 체계적. Iteration을 통한 작업의 좁은 범위로 인한 쉬운 리뷰는 압도적."

**구체적 학습:**
- 작은 범위로 나누면 각 단계의 복잡도가 낮아짐
- 리뷰 시 인지 부하가 현저히 감소
- 문제 발생 시 영향 범위가 제한적이어서 빠른 수정 가능

### 5.2. 새로운 기술 학습 전략

**학습 방식:**
- Redis에 대해 잘 알지 못하는 상태에서 프로젝트 시작
- 구현하면서 필요한 개념을 점진적으로 학습
- `redis-deep-dive.md` 같은 별도 문서로 심층 학습 보완

**효과:**
- "Learning by Doing" 방식이 효과적임을 확인
- 단순 사용법뿐 아니라 원리까지 이해하게 됨
- 실전 경험 + 이론적 배경의 결합이 학습 효과를 극대화

### 5.3. Redis의 두 가지 동시성 제어 패턴

**Distributed Lock (Redisson):**
- 전통적인 Lock 패러다임을 분산 환경에 적용
- MySQL과 함께 사용하여 DB 정합성 유지 가능
- Lock 획득/해제 오버헤드 존재하지만 충분히 빠름

**Lua Script:**
- Lock 없이 원자적 연산으로 동시성 보장
- 가장 빠른 성능 (예상대로 1위)
- Redis를 Source of Truth로 사용할 때 최적

---

## 6. 개선 사항 (Action Items for Next Sprint)

### 6.1. Sprint 3 사전 학습 계획

Sprint 2에서는 Redis를 잘 모르는 상태에서 시작했지만 성공적으로 완료했습니다. Sprint 3에서도 k6를 잘 모르는 상태로 시작하므로, 동일한 접근을 준비합니다.

**k6 개념 및 역사 학습:**
- [ ] k6가 무엇이고 왜 만들어졌는지 학습
- [ ] k6의 핵심 철학과 다른 부하 테스트 도구와의 차이점 이해
- [ ] Sprint 3 시작 전 또는 Iteration 1에서 `k6-overview.md` 문서 작성

**k6 기본 사용법 학습:**
- [ ] k6 시나리오 작성 방법 (JavaScript 기반)
- [ ] VU (Virtual User), RPS (Request Per Second) 개념
- [ ] Threshold, Metric 설정 방법
- [ ] 간단한 예제로 실습

**성능 측정 방법론 학습:**
- [ ] 성능 측정의 핵심 지표 (TPS, Latency, Percentile)
- [ ] 부하 테스트의 종류 (Load Test, Stress Test, Spike Test, Soak Test)
- [ ] 공정한 비교를 위한 테스트 환경 설정
- [ ] 결과 분석 및 해석 방법론

### 6.2. 문서화 품질 개선

**Summary 파일 템플릿 일관성 유지:**
- [ ] 수정 요청 시에도 전체 템플릿 구조를 명시적으로 유지 요청
- [ ] 또는 AI에게 템플릿 검증 체크리스트 제공

**문서 구조화:**
- [ ] `docs/technology/` 디렉토리 활용 (Redis 문서처럼)
- [ ] 기술 학습 문서를 별도로 관리

---

## 7. 메트릭 (Metrics)

### 작업 완료율
- **계획한 Task:** 50개
- **완료한 Task:** 50개
- **완료율:** 100%

### 시간
- **계획 기간:** 7일 (2026-01-24 ~ 2026-01-31)
- **실제 기간:** 3일 (2026-01-24 ~ 2026-01-26)
- **기간 차이:** 예상보다 4일 빠름 (약 57% 단축)

### Iteration별 완료 시간
- **Iteration 1:** 2026-01-24 완료 (1일)
- **Iteration 2:** 2026-01-26 완료 (2일)
- **Iteration 3:** 2026-01-26 완료 (동일 일자)

### 품질 지표
- **통합 테스트 성공률:** 100%
- **동시성 테스트 Success Rate:** 100% (모든 방법)
- **ArchUnit 테스트:** 통과
- **Blockers:** 0건

### 문서화
- **Iteration Summary 파일:** 3개 생성 (100%)
- **기술 문서:** 1개 (`redis-deep-dive.md`)
- **README 업데이트:** 완료

---

## 8. 종합 평가

### Sprint 2 성공 요인

1. **Iteration 기반 좁은 범위의 작업 단위**
   - 각 Iteration이 명확한 목표와 범위를 가짐
   - 리뷰와 검증이 용이
   - 인지 부하 최소화

2. **체계적인 Checkpoint 검증**
   - 각 주요 단계마다 동작 확인
   - 문제 조기 발견 및 수정
   - 빠른 피드백 루프

3. **Sprint 1 회고 반영**
   - Iteration Summary 파일 생성 의무화가 효과적
   - 문서화 품질 향상

4. **기술 학습과 구현의 병행**
   - Redis를 모르는 상태에서 시작했지만 성공적으로 완료
   - 구현하면서 학습하는 방식이 효과적
   - `redis-deep-dive.md`로 심층 학습 보완

### Sprint 2 완성도

- **계획 대비 달성률:** 100%
- **품질:** 상 (모든 테스트 통과, 100% Success Rate)
- **문서화:** 상 (체계적이고 일관된 문서 구조)
- **학습 효과:** 상 (Redis 원리 이해, Lua Script 습득)
- **프로세스 개선:** 중 (Summary 템플릿 일관성 문제 발견)

### Next Sprint로 넘어갈 준비 완료 여부

✅ **준비 완료**

- 4가지 동시성 제어 방법 모두 구현 완료
- REST API 통합 완료
- 예비 성능 비교 테스트 완료
- Sprint 3 (k6 부하 테스트)를 위한 기반 작업 완성
- Action Items 명확 (k6 학습, 성능 측정 방법론)

---

## 9. 팀 피드백 (Sprint Review 내용)

> 개인 프로젝트 셀프 리뷰

### 잘한 점

**압도적인 Iteration 효율성:**
- Iteration을 통한 좁은 범위 덕분에 리뷰가 매우 쉬움
- 각 단계가 명확해서 진행 상황 파악이 용이
- 문서화가 체계적이어서 나중에 다시 봐도 이해하기 쉬움

**기술적 깊이:**
- 단순히 "Redis를 사용했다"가 아니라 "Redis의 원리를 이해했다"
- Lua Script라는 새로운 패러다임 습득
- 성능 차이를 실제로 측정하고 체감

**문서화:**
- `redis-deep-dive.md`처럼 별도 기술 문서 작성이 학습 효과를 극대화
- Iteration Summary 파일이 Sprint 진행 과정을 잘 기록

### 아쉬운 점

**Summary 템플릿 일관성:**
- 수정/추가 요청 시 템플릿 구조가 깨지는 문제
- Sprint 3에서는 명시적으로 템플릿 유지 요청 필요

**계획 대비 빠른 완료:**
- 예상보다 4일 빠르게 완료
- 남은 시간을 활용해 추가 학습이나 실험을 할 수도 있었음
- 하지만 Sprint 목표는 100% 달성했으므로 문제는 아님

---

## 10. 다음 Sprint 준비사항

### Sprint 3 목표 (예상)

**k6 부하 테스트 및 최종 성능 비교:**
- k6 시나리오 작성
- 4가지 방법에 대한 공정한 부하 테스트
- TPS, Latency, Percentile 측정
- 결과 분석 및 문서화
- 최종 보고서 작성

### Sprint 3 시작 전 확인 사항

- [ ] 4가지 방법이 모두 정상 동작하는지 최종 확인
- [ ] k6 설치 및 기본 사용법 학습
- [ ] 성능 측정 방법론 개념 학습
- [ ] k6 개념 및 역사 문서 작성 (`k6-overview.md`)
- [ ] 테스트 환경 준비 (Docker 컨테이너 리소스 설정 등)
- [ ] Sprint 3 plan.md 초안 검토

### Action Items 우선순위

**High Priority:**
1. k6 개념 및 사용법 학습
2. 성능 측정 방법론 학습

**Medium Priority:**
3. Summary 템플릿 일관성 유지 방법 정립
4. 테스트 환경 최적화

---

## 결론

Sprint 2는 **100% 목표 달성**으로 성공적으로 완료되었습니다.

**핵심 성과:**
- Redis Distributed Lock과 Lua Script 두 가지 방법 구현 완료
- Redis의 원리를 깊이 이해하고 성능에 놀람
- Iteration 기반 접근의 압도적 효율성 재확인
- 체계적인 문서화로 학습 효과 극대화

**핵심 교훈:**
> "여전히 체계적. Iteration을 통한 작업의 좁은 범위로 인한 쉬운 리뷰는 압도적."

**다음 Sprint:**
- k6 부하 테스트를 통한 최종 성능 비교
- 새로운 도구(k6) 학습과 성능 측정 방법론 습득
- Summary 템플릿 일관성 유지 개선

**Sprint 3 시작 준비 완료 ✅**

---

**회고 완료일:** 2026-01-26
**다음 Sprint 시작 예정일:** TBD
