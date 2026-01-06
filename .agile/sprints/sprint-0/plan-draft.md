# Sprint 0: 플랫폼 엔지니어링 + 아키텍처 설계

**기간:** 2026-01-06 ~ 2026-01-10 (5일)
**목표:** 개발 환경을 구축하고 프로젝트 아키텍처를 시각화하여, 누구나 30분 안에 프로젝트를 이해하고 실행할 수 있게 한다.

---

## Sprint Goal

> 비기능적 요구사항을 충족하고, 아키텍처를 시각화하여 프로젝트의 Foundation을 완성한다.

---

## 워크플로우 철학

> **AI 시대의 개발 순서: 작은 단위로 시각화 → 검토 → 구현 반복**
>
> 전통적 방식(구현 → 문서화)이 아닌, AI를 활용한 사전 검증 방식을 적용합니다.
>
> **핵심 원칙:**
> 1. **작은 단위로 반복** - 인지 부하 최소화
> 2. **빠른 피드백 루프** - 한 Iteration이 끝나면 바로 검증
> 3. **검증 후 구현** - 잘못된 방향으로 개발 방지
>
> **Iteration 구조:**
> ```
> Iteration N:
>   시각화 → 검토 (Checkpoint) → 구현 → 검증
>     ↓
> Iteration N+1 시작
> ```

---

## Tasks

### Sprint 0 시작: 프로젝트 범위 및 워크플로우 결정

#### ADR 작성 (의사결정 기록)
- [ ] ADR-001: 왜 이 4가지 방법을 비교하는가?
  - Context: 동시성 제어 방법이 많은데 왜 이 4가지인가?
  - Decision: Pessimistic, Optimistic, Redis Lock, Lua Script 선택
  - Consequences: 실무 적용 가능한 주요 방법 비교 가능

- [ ] ADR-002: 왜 PoC 범위로 축소했는가?
  - Context: 원래 3가지 기술(동시성, 조회, EDA)을 하려 했으나
  - Decision: 동시성 제어 하나만 깊게
  - Consequences: 1-2달 안에 완성 가능, 전문성 증명

- [ ] ADR-003: 왜 시각화를 먼저 하는가?
  - Context: 전통적 방식은 구현 → 문서화
  - Decision: AI 시대에는 시각화 → 검토 → 구현
  - Consequences: 잘못된 방향으로 개발 방지, 인지 부하 감소

**Note:**
- 이 3개 ADR은 Sprint 시작 시 프로젝트 전체 방향을 결정하며 작성
- docs/adr 디렉터리에 저장

---

### Iteration 1: 인프라 환경 구축 (Infrastructure)

#### US-0.1: 인프라 시각화 + 기술 스택 결정
- [ ] Docker Compose 환경 다이어그램 작성 (Mermaid)
  - MySQL 8.0, Redis 7.0 컨테이너 구성
  - 네트워크 설정 및 볼륨 구조
  - 포트 매핑 및 헬스체크 설정
- [ ] 데이터 초기화 플로우 다이어그램 작성
  - 재고 100개 생성 프로세스
  - 초기 데이터 스키마
- [ ] **ADR-004: 왜 MySQL과 Redis를 선택했는가?**
  - Context: 다양한 DB/캐시 옵션 (PostgreSQL, MongoDB, Memcached 등)
  - Decision: MySQL 8.0 + Redis 7.0 선택
  - Consequences: 범용성, 학습 자료 풍부, 실무 적용 가능

**Acceptance Criteria:**
- 인프라 다이어그램을 보고 누구나 환경 구성 이해 가능
- Docker Compose 구성이 다이어그램으로 명확히 표현됨
- docs/architecture/infrastructure.md 파일 생성
- ADR-004 작성 완료 (기술 스택 선택 근거 명확)

**🔍 Checkpoint 1:** 인프라 다이어그램 검토 및 확정

---

#### US-0.2: 인프라 구현
- [ ] Docker Compose 작성 (US-0.1 다이어그램 기반)
- [ ] 데이터 초기화 스크립트 작성 (재고 100개 생성)
- [ ] Makefile 작성 (`make init`, `make up`, `make down`, `make test`)
- [ ] 환경 검증 스크립트 작성 (헬스체크)

**Acceptance Criteria:**
- `make up` 실행 시 MySQL + Redis 정상 동작
- `make init` 실행 시 재고 100개 데이터 생성 확인
- `docker ps` 결과에 2개 컨테이너 healthy 상태
- US-0.1 다이어그램과 실제 구현이 일치

**✅ Iteration 1 완료 조건:** 인프라 환경이 다이어그램대로 동작

---

### Iteration 2: 프로젝트 스캐폴딩 (Project Scaffolding)

#### US-0.3: 애플리케이션 구조 시각화 + 아키텍처 패턴 결정
- [ ] 패키지 구조 다이어그램 작성 (Layered Architecture)
  - domain / service / repository / controller 레이어
  - 각 레이어의 역할 및 의존성 규칙
- [ ] ArchUnit 규칙 다이어그램 작성
- [ ] **ADR-005: 왜 Layered Architecture를 선택했는가?**
  - Context: Hexagonal, Clean Architecture 등 다양한 옵션 존재
  - Decision: Layered Architecture 선택
  - Consequences: 단순성, PoC에 적합, 오버엔지니어링 방지

**Acceptance Criteria:**
- 패키지 구조가 명확히 시각화됨
- ArchUnit 규칙이 다이어그램으로 표현됨
- docs/architecture/application.md 파일 생성
- ADR-005 작성 완료 (아키텍처 패턴 선택 근거 명확)

**🔍 Checkpoint 2:** 애플리케이션 구조 검토 및 확정

---

#### US-0.4: Spring Boot 프로젝트 생성
- [ ] Spring Boot 프로젝트 생성 (Java 17, Gradle)
- [ ] 패키지 구조 생성 (US-0.3 다이어그램 기반)
- [ ] ArchUnit 테스트 작성 (레이어 의존성 규칙 강제)
- [ ] 기본 설정 파일 작성 (application.yml)

**Acceptance Criteria:**
- `./gradlew test` 실행 시 ArchUnit 테스트 통과
- 패키지 구조가 US-0.3 다이어그램과 일치
- 애플리케이션 정상 기동 확인 (`./gradlew bootRun`)

**✅ Iteration 2 완료 조건:** 빈 Spring Boot 프로젝트가 구조화되어 정상 동작

---

### Iteration 3: 전체 시스템 시각화 및 문서화 (System Overview & Documentation)

#### US-0.5: 전체 시스템 아키텍처 시각화
- [ ] C4 Container Diagram 작성 (시스템 전체 구성)
- [ ] Sequence Diagram 4종 작성 (동시성 제어 방식별)
  1. Pessimistic Lock
  2. Optimistic Lock
  3. Redis Distributed Lock
  4. Redis Lua Script
- [ ] 부하 테스트 Architecture Diagram 작성
- [ ] 온보딩 Flow Diagram 작성

**Acceptance Criteria:**
- C4 Container Diagram으로 시스템 전체 파악 가능
- 4가지 동시성 제어 방법이 Sequence Diagram으로 표현됨
- 부하 테스트 아키텍처 명확히 시각화
- docs/architecture/system-overview.md 파일 생성

**Note:** 시각화만 진행, 실제 동시성 제어 구현은 Sprint 1에서

---

#### US-0.6: README 작성
- [ ] README 초안 작성 (프로젝트 소개 + Quick Start)
- [ ] 프로젝트 목표 및 배경 설명
- [ ] 로컬 환경 실행 가이드
- [ ] 아키텍처 다이어그램 링크
- [ ] ADR 링크 (5개)

**Acceptance Criteria:**
- README만 보고 누구나 프로젝트 이해 가능
- Quick Start 섹션으로 즉시 실행 가능
- 모든 아키텍처 다이어그램 링크됨
- 모든 ADR 링크됨

**✅ Iteration 3 완료 조건:** 프로젝트 문서화 완성

---

## Sprint 0 Definition of Done

### Sprint 시작: 프로젝트 방향 설정 ✅
- [ ] ADR-001 완성: 4가지 방법 선택 근거
- [ ] ADR-002 완성: PoC 범위 축소 근거
- [ ] ADR-003 완성: 시각화 우선 워크플로우 근거

### Iteration 1: 인프라 환경 구축 ✅
- [ ] 인프라 다이어그램 완성 (docs/architecture/infrastructure.md)
- [ ] ADR-004 완성: 기술 스택(MySQL, Redis) 선택 근거
- [ ] Checkpoint 1 통과 (사용자 검토 완료)
- [ ] `make up` 실행 시 MySQL + Redis 정상 동작
- [ ] `make init` 실행 시 재고 100개 데이터 생성
- [ ] 다이어그램과 실제 구현 일치

### Iteration 2: 프로젝트 스캐폴딩 ✅
- [ ] 애플리케이션 구조 다이어그램 완성 (docs/architecture/application.md)
- [ ] ADR-005 완성: Layered Architecture 선택 근거
- [ ] Checkpoint 2 통과 (사용자 검토 완료)
- [ ] Spring Boot 빈 프로젝트 생성 및 정상 기동
- [ ] `./gradlew test` 실행 시 ArchUnit 테스트 통과
- [ ] 다이어그램과 실제 패키지 구조 일치

### Iteration 3: 전체 시스템 시각화 및 문서화 ✅
- [ ] C4 Container Diagram 완성
- [ ] 동시성 제어 4종 Sequence Diagram 완성
- [ ] 부하 테스트 아키텍처 다이어그램 완성
- [ ] README 완성 (모든 다이어그램 + ADR 링크 포함)
- [ ] docs/architecture/system-overview.md 파일 생성

### 최종 검증
- [ ] 총 5개 ADR 문서 완성 (각 의사결정 시점에 작성됨)
- [ ] 모든 Checkpoint 통과 (2회)
- [ ] 신규 개발자가 README + 다이어그램만 보고 시스템 이해 가능
- [ ] `make up` → `make init` → `./gradlew bootRun` 순서로 즉시 실행 가능
- [ ] 인프라와 애플리케이션이 다이어그램과 일치

---

## Blockers

- 없음

---

## Notes

### 범위: 플랫폼 엔지니어링 + 아키텍처 설계
Sprint 0는 brainstorm.md의 원래 범위를 준수합니다:
1. **플랫폼 엔지니어링** (Docker + Makefile)
2. **프로젝트 스캐폴딩** (빈 Spring Boot 프로젝트)
3. **아키텍처 시각화** (모든 다이어그램)
4. **문서화** (ADR + README)

**동시성 제어 구현은 Sprint 1에서 진행**

### 워크플로우: Iteration 기반
- **작은 단위로 시각화 → 검토 → 구현 반복**
- Checkpoint로 사용자 검토 (Iteration 1, 2)
- 인지 부하 최소화 (한 번에 하나씩)
- 빠른 피드백 루프

### ADR 작성 철학: 의사결정 시점에 기록
ADR은 별도 작업이 아닌, **아키텍처 고민이 발생한 시점에 자연스럽게 작성**합니다:
- **Sprint 시작 시 (3개):** 프로젝트 전체 방향 결정
  - ADR-001: 4가지 방법 선택
  - ADR-002: PoC 범위 축소
  - ADR-003: 시각화 우선 워크플로우
- **Iteration 1 (1개):** 인프라 기술 스택 선택
  - ADR-004: MySQL + Redis 선택
- **Iteration 2 (1개):** 애플리케이션 아키텍처 패턴 선택
  - ADR-005: Layered Architecture 선택

**총 5개 ADR이 각 의사결정 시점에 작성됨**

### 구성
- brainstorm.md의 Sprint 0 계획 기반
- 총 3개 Iteration, 6개 User Story
- Iteration 1-2: 시각화 + ADR → Checkpoint → 구현
- Iteration 3: 시각화 + 문서화만 (구현 없음)
