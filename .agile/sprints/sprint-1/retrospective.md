# Sprint 1 회고 (Retrospective)

**Sprint:** Sprint 1 - DB Lock 구현 (Pessimistic + Optimistic)
**기간:** 2026-01-22 ~ 2026-01-24 (3일)
**회고 작성일:** 2026-01-24
**상태:** ✅ 완료 (Completed)

---

## 1. Sprint Goal 달성 여부

> **목표:** Pessimistic Lock과 Optimistic Lock을 구현하고, 동시성 테스트로 정합성을 검증한다.

### 결과: ✅ 목표 달성

- Pessimistic Lock: `@Lock(PESSIMISTIC_WRITE)` + `SELECT ... FOR UPDATE` 구현 완료
- Optimistic Lock: `@Version` + Spring Framework 7 네이티브 `@Retryable` 구현 완료
- 동시성 테스트: 100개 동시 요청으로 정합성 검증 완료
- REST API: Strategy Pattern으로 Lock 방식 선택 가능

---

## 2. Sprint Metrics

| 항목 | 값 |
|------|-----|
| 계획한 Task | 32개 |
| 완료한 Task | 32개 |
| **완료율** | **100%** |
| 계획 기간 | 7일 |
| 실제 기간 | 3일 |
| **효율성** | **계획 대비 57% 단축** |

### Iteration별 완료 현황

| Iteration | 내용 | 상태 |
|-----------|------|------|
| Iteration 1 | Stock Domain 모델 | ✅ |
| Iteration 2 | Pessimistic Lock Service | ✅ |
| Iteration 3 | Optimistic Lock Service | ✅ |
| Iteration 4 | REST API | ✅ |

### 테스트 결과

| Lock 방식 | Success Rate | 특징 |
|-----------|--------------|------|
| Pessimistic Lock | 100% | Lock 대기, 강력한 정합성 |
| Optimistic Lock | ~96% | Retry + Exponential Backoff |

---

## 3. 잘된 점 (What Went Well)

### 3.1. Iteration 기반 워크플로우

> "일을 어디서 끝내야 할지 몰라서 계속 시간을 쏟아 인지 에너지가 바닥나는 Pain Point를 해결"

- 작은 단위로 반복하여 인지 부하 최소화
- 각 Iteration마다 명확한 완료 조건 (Definition of Done)
- Checkpoint로 중간 검증 → 잘못된 방향 사전 차단

### 3.2. AI 페어 프로그래밍 효과

- AI가 만든 코드임에도 **사용자가 전부 이해 가능**
- 주도권을 사용자가 가지고 코드를 만들어나가는 느낌
- 코드 리뷰 → 수정 요청 → 반영의 빠른 피드백 루프

### 3.3. Spring Framework 7 네이티브 기능 발견

- `@Retryable` 애노테이션이 Spring Framework 7에 네이티브로 추가됨
- `org.springframework.resilience.annotation` 패키지
- spring-retry 외부 의존성 불필요

### 3.4. 전략 패턴으로 확장 가능한 구조

- `StockService` 인터페이스로 Lock 전략 추상화
- `Map<String, StockService>`로 런타임 전략 선택
- Sprint 2에서 Redis Lock 추가 시 동일한 패턴 적용 가능

---

## 4. 아쉬운 점 (What Didn't Go Well)

### 4.1. 중간 수정 사항들

- **문제:** 구현 중 예상치 못한 수정 필요
  - Spring Boot 4.x 패키지 변경 (Jackson 3.0, MockMvc)
  - ArchUnit 레이어 규칙 위반 수정
  - 초기 데이터 설정 문제 (Docker 볼륨)
- **원인:** Spring Boot 4.x가 최신 버전이라 변경사항 파악 부족
- **영향:** 크지 않음. 작은 단위로 반복했기에 빠르게 수정 가능

### 4.2. 터미널 환경의 한계

- 스크롤해서 작업 내역을 다시 따라가기 어려움
- 진행 상황 파악을 위해 사용자가 직접 추적해야 함

---

## 5. 배운 점 (Lessons Learned)

### 5.1. 작은 단위의 힘

> "작은 단위로 반복하니까 진짜 내가 주도권을 가지고 코드를 만들어나간다는 느낌"

- 큰 작업을 작은 Iteration으로 분할
- 각 Iteration마다 검증 → 문제 조기 발견
- 인지 에너지 관리에 효과적

### 5.2. Pessimistic vs Optimistic 트레이드오프

| 관점 | Pessimistic | Optimistic |
|------|-------------|------------|
| 성공률 | 100% | ~96% (Retry 의존) |
| 동시성 | 낮음 (Lock 대기) | 높음 (Lock 없음) |
| 적합 상황 | 충돌 많음, 정합성 중요 | 충돌 적음, 성능 중요 |

### 5.3. Spring Boot 4.x 변경사항

- Jackson 3.0: `com.fasterxml.jackson` → `tools.jackson`
- MockMvc: `org.springframework.boot.test.autoconfigure.web.servlet` → `org.springframework.boot.webmvc.test.autoconfigure`
- 네이티브 `@Retryable`: `org.springframework.resilience.annotation`

---

## 6. 개선 사항 (Action Items for Next Sprint)

### 6.1. Iteration 완료 시 진행 내역 문서화

- [ ] 각 Iteration 완료 후 `.md` 파일로 진행 내역 제공
- [ ] 파일 구조: `.agile/sprints/sprint-N/iteration-M-summary.md`
- [ ] 내용: 완료한 작업, 생성/수정된 파일, 주요 결정사항
- [ ] **사용자 의견 작성 칸 추가** → 코드/아키텍처 수정 요청 가능

### 6.2. Spring Boot 4.x 변경사항 사전 조사

- [ ] 새로운 기술 적용 전 공식 마이그레이션 가이드 확인
- [ ] 특히 테스트 관련 패키지 변경 주의

### 6.3. 테스트 데이터 관리 개선

- [x] `make reset`: TRUNCATE로 AUTO_INCREMENT 리셋 (완료)
- [x] `make stock`: 재고 조회 명령어 추가 (완료)

---

## 7. 종합 평가

### Sprint 1 성공 요인

1. **Iteration 기반 워크플로우** - 인지 부하 최소화, 명확한 완료 조건
2. **AI 페어 프로그래밍** - 빠른 피드백 루프, 사용자 주도권 유지
3. **전략 패턴** - 확장 가능한 구조, 기술적 차이 명확

### Sprint 1 완성도

- **계획 대비 달성률:** 100%
- **코드 품질:** 상 (ArchUnit 통과, 테스트 커버리지)
- **문서화:** 상 (README, plan.md, 주석)

### Next Sprint 준비 완료 여부

✅ **준비 완료**

- Redis Lock도 동일한 전략 패턴으로 추가 가능
- 인프라(Redis) 이미 Docker Compose에 준비됨
- `StockService` 인터페이스 확장만 하면 됨

---

## 8. 다음 Sprint 준비사항

### Sprint 2 목표 (예상)

- Redis Distributed Lock (Redisson)
- Redis Lua Script (Atomic Execution)
- 4가지 방법 성능 비교 (k6 부하 테스트)

### Sprint 2 시작 전 확인 사항

- [ ] Redis 컨테이너 정상 동작 확인
- [ ] Redisson 의존성 추가
- [ ] Iteration 진행 내역 문서화 방식 적용

---

## 결론

Sprint 1은 **Iteration 기반 워크플로우**의 효과를 확인한 Sprint였다.

작은 단위로 반복하면서:
- 사용자가 주도권을 유지하고
- AI 코드도 완전히 이해하며
- 인지 에너지를 효율적으로 관리할 수 있었다.

다음 Sprint에서는 **Iteration 완료 시 진행 내역 문서화**를 추가하여
터미널 환경의 한계를 보완할 예정이다.

**Sprint 2 시작 준비 완료 ✅**
