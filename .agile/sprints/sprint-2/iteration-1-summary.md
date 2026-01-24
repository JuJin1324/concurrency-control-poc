# Iteration 1 Summary: Redis Distributed Lock

**Sprint:** Sprint 2 - Redis 구현
**Iteration:** 1/3
**완료일:** 2026-01-24
**상태:** ✅ 완료

---

## 구현 내용

### 1. Redisson 설정

**build.gradle 의존성 추가:**
```gradle
implementation 'org.redisson:redisson-spring-boot-starter:4.1.0'
```

- Redisson 4.1.0은 Spring Boot 4.0을 지원
- Spring Boot 자동 설정으로 별도 설정 파일 불필요

---

### 2. RedisLockStockService 구현

**위치:** `src/main/java/com/concurrency/poc/service/RedisLockStockService.java`

**핵심 설계 결정:**

| 항목 | 결정 | 이유 |
|------|------|------|
| Lock 획득 대기 시간 | 5초 | 동시 요청이 많을 때 대기 허용 |
| Lock 유지 시간 | 3초 | 재고 차감은 빠른 작업 |
| Transaction 관리 | TransactionTemplate | Lock 내부에서 트랜잭션 실행 필요 |

**Lock과 Transaction 순서:**
```
1. Lock 획득 (Redis)
2. Transaction 시작 (DB)
3. 재고 조회 및 차감
4. Transaction 커밋
5. Lock 해제
```

> Transaction이 Lock 안에서 실행되어야 커밋 후 Lock이 해제됩니다.
> 그렇지 않으면 다른 스레드가 커밋되지 않은 데이터를 읽을 수 있습니다.

---

### 3. 동시성 테스트 결과

**테스트:** `RedisLockStockServiceTest`

**조건:**
- 초기 재고: 100개
- 동시 요청: 100개 (스레드)
- 각 요청당 차감: 1개

**결과:**
```
=== Redis Lock 테스트 결과 ===
성공: 100, 실패: 0
Success Rate: 100.0%
최종 재고: 0
```

**Acceptance Criteria 충족:**
- [x] 동시 요청 시 재고가 음수가 되지 않음
- [x] Success Rate 100% 달성
- [x] 통합 테스트 통과

---

## Pessimistic Lock과의 비교

| 특성 | Pessimistic Lock | Redis Distributed Lock |
|------|------------------|------------------------|
| Lock 위치 | DB Row | Redis |
| 확장성 | 단일 DB | 다중 인스턴스 가능 |
| DB 부하 | 높음 (FOR UPDATE) | 낮음 (Redis가 Lock 관리) |
| 복잡도 | 낮음 (@Lock 어노테이션) | 중간 (TransactionTemplate 필요) |
| Success Rate | 100% | 100% |

---

## 생성된 파일

| 파일 | 설명 |
|------|------|
| `RedisLockStockService.java` | Redis Distributed Lock 구현체 |
| `RedisLockStockServiceTest.java` | 동시성 통합 테스트 |

---

## 다음 Iteration 예고

**Iteration 2: Redis Lua Script 구현**
- Redis에 재고 데이터 저장 구조 설계
- Lua Script로 원자적 재고 차감
- Lock 없이 정합성 보장

---

## 사용자 피드백

> 이 섹션은 Iteration 완료 후 사용자가 작성합니다.

### 코드 리뷰 의견
- build.gradle 에 `implementation 'org.springframework.boot:spring-boot-starter-data-redis'` 가 있음에도 불구하고 왜 `redisson` 라이브러리를 추가했는가? 이 redisson 라이브러리의 목적은 무엇인가?
```
 * <h3>Pessimistic Lock과의 비교</h3>
 * <ul>
 *   <li>Pessimistic: DB Row Lock → DB 부하 증가, 단일 DB에서만 유효</li>
 *   <li>Redis Lock: 분산 락 → DB 부하 감소, 다중 인스턴스에서 유효</li>
 * </ul>
``` 
- 위의 코드를 보고 분산 락이 뭔지 확실하게 이해하게 됨. 그 전까지 분산 락이 정확히 뭔지 몰랐음. 
  내가 이해한 바로는 분산된 DB 환경에서 락을 보장해주는 것이 맞는건가?
- RedisLockStockService 의 경우 Redis Lock 을 사용하므로 인해서 락 획득 후 DB 트랜잭션을 얻기 위해 Spring 의 Transactional 애노테이션이 아닌 직접 transactionTemplate 을 사용한 것 같은데 그로 인해서 try/catch 블록을 Service 레이어에서 직접 사용해야하도록 변경된 거 같은데 이게 맞는지 확인 부탁하고,
서비스 레이어에서 이러한 트랜잭션 관련 코드를 감출 수 있는 방법이 없을지 및 Spring 혹은 Redisson 라이브러리 등에서 이런 기술 코드를 매끄럽게 숨기기 위한 제공하는 방식이 없는지 확인 바람.
- LOCK_PREFIX 의 경우 따로 네이밍 컨벤션이 존재하는지 확인 바람.
- test/java/com.concurrency.poc.service 아래의 테스트를 모두 돌려봤는데 PessimisticLockStockService 가 176ms, RedisLockStockServiceTest 가 353ms, OptimisticLockStockService 가 7sec 92ms 로
예상 외로 PessimisticLockStockServiceTest 가 가장 우세했는데 너가 볼 때 왜 RedisLock 보다 DB 비관락이 더 빠르게 나왔는지 추론 요청함. 

### 아키텍처 수정 요청
- (피드백 입력)

### 기타 의견
- (피드백 입력)
