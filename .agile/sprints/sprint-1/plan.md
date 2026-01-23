# Sprint 1: DB Lock 구현 (Pessimistic + Optimistic)

**기간:** 2026-01-22 ~ 2026-01-29 (7일)
**목표:** MySQL 기반 동시성 제어 2가지 방법을 구현하고, 동시성 테스트로 검증하여 데이터 정합성을 보장한다.

---

## Sprint Goal

> Pessimistic Lock과 Optimistic Lock을 구현하고, 동시성 테스트로 정합성을 검증한다.

---

## 워크플로우 철학

> **AI 시대의 개발 순서: 작은 단위로 시각화 → 검토 → 구현 반복**

**핵심 원칙:**
1. 작은 단위로 반복 (인지 부하 최소화)
2. 빠른 피드백 루프 (Checkpoint로 검증)
3. 검증 후 구현 (잘못된 방향 사전 차단)

---

## Tasks

### Iteration 1: Stock Domain 모델 구현

#### US-1.1: Stock Entity 설계
- [x] Stock Entity 작성 (JPA Entity, domain 패키지)
  - `@Entity`, `@Table(name = "stock")`
  - id (Long, `@Id`, `@GeneratedValue`)
  - productId (String, 상품 ID)
  - quantity (int, 재고 수량)
  - version (Long, `@Version` - Optimistic Lock용)
  - createdAt, updatedAt (Timestamp)
- [x] 비즈니스 로직 메서드
  - `decrease(int amount)` - 재고 차감
  - `isAvailable(int amount)` - 재고 확인
- [x] 비즈니스 불변식
  - 재고는 0 이상이어야 함
  - 재고 부족 시 예외 발생 (InsufficientStockException)

**Acceptance Criteria:**
- Stock이 JPA Entity로 정의됨
- 비즈니스 로직이 Entity에 포함됨
- ArchUnit 테스트 통과 (Domain 계층 규칙)

---

#### US-1.2: Stock Repository 구현
- [x] StockRepository 인터페이스 작성
  - `JpaRepository<Stock, Long>` 상속
  - Pessimistic Lock 메서드 추가
    - `@Lock(LockModeType.PESSIMISTIC_WRITE)`
    - `Optional<Stock> findByIdWithPessimisticLock(Long id)`
- [x] 기본 CRUD 메서드 확인

**Acceptance Criteria:**
- Repository가 Spring Data JPA 표준 사용
- Pessimistic Lock 메서드 동작 확인

**✅ Iteration 1 완료 조건:** Stock Entity + Repository 완성

---

### Iteration 2: Pessimistic Lock 구현 (전략 패턴)

#### US-1.3: StockService 인터페이스 정의
- [x] StockService 인터페이스 작성 (service 패키지)
  - `void decreaseStock(Long stockId, int amount)` 메서드
  - 전략 패턴의 공통 인터페이스
- [x] 도메인 예외 정의
  - InsufficientStockException
  - StockNotFoundException

**Acceptance Criteria:**
- StockService가 순수 인터페이스
- 구현체 독립적

---

#### US-1.4: PessimisticLockStockService 구현
- [x] PessimisticLockStockService 작성 (StockService 구현)
- [x] `decreaseStock()` 메서드 구현
  - `findByIdWithPessimisticLock()` 호출
  - Stock Entity에서 `decrease()` 메서드 호출
  - Repository로 저장
- [x] Transaction 관리 (`@Transactional`)
- [x] 재고 부족/없음 예외 처리

**Acceptance Criteria:**
- `SELECT ... FOR UPDATE` 쿼리 실행 확인 (로그)
- 비즈니스 로직이 Entity 메서드로 실행됨
- Service가 ArchUnit 규칙 준수

**🔍 Checkpoint 1:** Pessimistic Lock 동작 확인 및 쿼리 검증

---

#### US-1.5: Pessimistic Lock 통합 테스트
- [x] 동시성 시뮬레이션 테스트 작성
  - 100개 재고에 100개 요청
  - CountDownLatch + ExecutorService 사용
- [x] 재고 정합성 검증
  - 최종 재고가 정확히 0이 되는지 확인
- [x] 100% Success Rate 달성

**Acceptance Criteria:**
- 동시 요청 시 재고가 음수가 되지 않음
- 100% Success Rate 달성
- 통합 테스트 통과

**✅ Iteration 2 완료 조건:** Pessimistic Lock이 동시성 문제를 해결함

---

### Iteration 3: Optimistic Lock 구현 (전략 패턴)

#### US-1.6: OptimisticLockStockService 구현
- [x] OptimisticLockStockService 작성 (StockService 구현)
- [x] `decreaseStock()` 메서드 구현
  - 일반 `findById()` 조회 (Lock 없음)
  - Stock Entity에서 `decrease()` 메서드 호출
  - Repository로 저장 (`@Version` 자동 체크)
- [x] OptimisticLockException 처리
- [x] Retry 로직 구현 (Self-injection 방식)
  - 최대 3회 재시도
  - 재시도 간 50ms 대기
  - Self-injection으로 @Transactional 적용

**Acceptance Criteria:**
- `@Version` 컬럼이 자동으로 증가 (로그 확인)
- 충돌 시 OptimisticLockException 발생
- Retry 로직이 정상 동작

**🔍 Checkpoint 2:** Optimistic Lock 및 Retry 로직 검증

---

#### US-1.7: Optimistic Lock 통합 테스트
- [x] 동시성 시뮬레이션 테스트 작성
  - 100개 재고에 100개 동시 요청
  - CountDownLatch + ExecutorService 사용
- [x] Success Rate 측정
  - 최종 재고 확인
  - 성공/실패 비율 출력
- [x] 재고 정합성 검증
  - 성공한 요청 수만큼 재고 감소 확인

**Acceptance Criteria:**
- Retry 로직이 정상 동작
- Success Rate가 측정됨 (예상: 90% 이상)
- 통합 테스트 통과

**✅ Iteration 3 완료 조건:** Optimistic Lock이 정상 동작하고 Retry 로직 검증됨

---

### Iteration 4: REST API 구현

#### US-1.8: Stock Controller 구현 (Strategy 선택)
- [ ] REST API 엔드포인트 작성
  - `POST /api/stock/decrease?method=pessimistic`
  - `POST /api/stock/decrease?method=optimistic`
  - `GET /api/stock/{id}` (재고 조회)
- [ ] Request/Response DTO 작성
  - StockDecreaseRequest (stockId, amount)
  - StockDecreaseResponse (success, remainingQuantity, message)
- [ ] Strategy Pattern 적용
  - `Map<String, StockService>` 또는 `@Qualifier` 사용
  - method 파라미터에 따라 구현체 선택
- [ ] Controller 유효성 검증 (`@Valid`)
- [ ] 예외 처리 (GlobalExceptionHandler)

**Acceptance Criteria:**
- Controller가 DTO만 사용 (Entity 직접 노출 금지)
- 전략 패턴으로 구현체 선택
- ArchUnit 테스트 통과 (Controller → Domain 직접 접근 불가)

---

#### US-1.9: API 통합 테스트
- [ ] MockMvc 기반 API 테스트 작성
  - Pessimistic Lock API 테스트
  - Optimistic Lock API 테스트
- [ ] 재고 조회 API 테스트
- [ ] 예외 시나리오 테스트
  - 재고 부족
  - 존재하지 않는 Stock ID
  - 잘못된 파라미터

**Acceptance Criteria:**
- 모든 API 엔드포인트가 정상 동작
- 예외 처리가 적절함
- MockMvc 테스트 통과

**✅ Iteration 4 완료 조건:** REST API가 완성되고 Postman/cURL로 호출 가능

---

## Sprint 1 Definition of Done

### Iteration 1: Stock Domain 모델 ✅
- [x] Stock Entity 구현 (JPA, 비즈니스 로직 포함)
- [x] StockRepository 구현 (Spring Data JPA)
- [x] Pessimistic Lock 메서드 추가
- [x] ArchUnit 테스트 통과 (Domain 계층 규칙)

### Iteration 2: Pessimistic Lock (전략 패턴) ✅
- [x] StockService 인터페이스 정의
- [x] PessimisticLockStockService 구현
- [x] `@Lock(PESSIMISTIC_WRITE)` 적용
- [x] `SELECT ... FOR UPDATE` 쿼리 확인
- [x] 동시성 통합 테스트 통과 (100% Success Rate)
- [x] Checkpoint 1 통과

### Iteration 3: Optimistic Lock (전략 패턴) ✅
- [x] OptimisticLockStockService 구현
- [x] `@Version` 컬럼 동작 확인
- [x] Retry 로직 구현 및 테스트
- [x] 동시성 통합 테스트 통과 (Success Rate 측정)
- [x] Checkpoint 2 통과

### Iteration 4: REST API ✅
- [ ] Stock Controller 구현 (전략 패턴 적용)
- [ ] Request/Response DTO 작성
- [ ] method 파라미터로 Service 전략 선택
- [ ] API 통합 테스트 통과
- [ ] Postman/cURL로 API 호출 가능

### 최종 검증
- [ ] 2가지 전략(Pessimistic, Optimistic) 정상 동작
- [ ] 동시 요청 시 재고 정합성 보장
- [ ] ArchUnit 테스트 통과
  - Controller → Domain Entity 직접 노출 불가
  - Service → Repository 의존
- [ ] Service 코드에서 Lock 방식 차이가 명확히 보임
- [ ] README 업데이트 (아키텍처 설명 + API 사용 예시)

---

## Blockers

- 없음

---

## Notes

### 아키텍처: Service 전략 패턴 (간결한 구조)

**패키지 구조:**
```
domain/
  ├── Stock.java (JPA Entity + 비즈니스 로직)
  └── StockRepository.java (Spring Data JPA)

service/
  ├── StockService.java (Interface)
  ├── PessimisticLockStockService.java (전략 1)
  └── OptimisticLockStockService.java (전략 2)

controller/
  └── StockController.java (Strategy 선택, DTO만 사용)
```

**핵심 원칙:**
1. **Stock Entity에 비즈니스 로직 포함** - decrease(), isAvailable()
2. **Service만 전략 패턴** - 각 Lock 방식을 독립적 구현체로
3. **Controller는 Strategy 선택** - method 파라미터로 구현체 선택
4. **기술적 차이가 명확** - Service 코드만 봐도 어떤 Lock 사용하는지 직관적

**장점:**
- 간결한 구조로 쇼케이스 목적에 적합
- Pessimistic vs Optimistic 차이가 Service 코드에서 명확히 보임
- 과도한 추상화 없이 기술적 비교에 집중

**Redis Lock은 Sprint 2에서 진행 (동일한 전략 패턴 적용)**

### 워크플로우: Iteration 기반
- **작은 단위로 반복** (Iteration 1-4)
- Checkpoint로 검증 (Iteration 2, 3)
- 인지 부하 최소화 (한 번에 하나씩)
- 빠른 피드백 루프

### 테스트 전략
- **단위 테스트:** Domain 로직 검증
- **통합 테스트:** 동시성 시뮬레이션
- **API 테스트:** MockMvc 기반

### 예상 결과
- **Pessimistic Lock:** 100% Success Rate, 느린 처리 속도
- **Optimistic Lock:** 90%+ Success Rate, 빠른 처리 속도, Retry 발생

---

## Sprint 1 목표 요약

✅ **Stock Entity + Repository** (JPA, 비즈니스 로직 포함)  
✅ **Service 전략 패턴으로 2가지 Lock 구현** (Pessimistic, Optimistic)  
✅ **동시성 테스트로 정합성 검증** (100% vs 90%+ Success Rate)  
✅ **REST API 완성** (Strategy 선택, DTO만 사용)  
✅ **기술적 차이가 명확한 코드** (쇼케이스 목적에 최적화)  
✅ **Sprint 2 준비 완료** (Redis Lock도 동일한 전략으로 추가 가능)  
