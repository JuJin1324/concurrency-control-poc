# [Deep Dive] Optimistic Lock Mechanics

**Parent Document:** [Optimistic Lock 운영 가이드](../optimistic-lock-ops.md)

낙관적 락은 사실 "락(Lock)"이 아닙니다. **"충돌 감지(Conflict Detection)"** 메커니즘에 가깝습니다. 이 미묘한 차이가 시스템의 성격을 결정합니다.

---

## 1. 작동 원리: CAS (Compare-And-Swap)

낙관적 락은 하드웨어 수준의 원자적 연산인 CAS를 데이터베이스 레벨로 확장한 것입니다.

### 1.1 SQL Level Logic
```sql
-- 1. 조회 (Read)
SELECT id, price, version FROM product WHERE id = 1; 
-- 결과: version = 1

-- 2. 애플리케이션 로직 수행 (No Lock)
-- ... (가격 계산 등) ...

-- 3. 수정 (Compare & Swap)
UPDATE product
SET price = 2000, version = version + 1
WHERE id = 1 AND version = 1; -- 핵심 조건
```

### 1.2 결과 해석
*   **Affected Rows = 1:** 성공. 버전이 2로 업데이트됨.
*   **Affected Rows = 0:** 실패. 내가 처리하는 동안 누군가 이미 버전을 2로 올렸음. (`version = 1` 조건 불만족) -> **`OptimisticLockException` 발생.**

---

## 2. 해결하는 문제: 갱신 손실 (Lost Update)

### 2.1 갱신 손실 시나리오 (No Locking)
**[전제]** 잔액이 1,000원인 **하나의 계좌**를 두 명의 사용자(Alice, Bob)가 동시에 접근하는 상황.

1.  **Alice 읽기:** 계좌 잔액 1,000원을 읽음. (메모리에 `balance=1,000` 저장)
2.  **Bob 읽기:** 동일한 계좌의 잔액 1,000원을 읽음. (메모리에 `balance=1,000` 저장)
3.  **Alice 출금:** 1,000원에서 500원을 출금하여 500원으로 업데이트 성공. (DB 잔액: 500원)
4.  **Bob 입금:** (재앙의 시작) Bob은 자신이 읽었던 **과거의 잔액(1,000원)**을 기준으로 300원을 더해 1,300원을 만듦. 그대로 DB에 업데이트 시도.
    *   `UPDATE account SET balance = 1,300 WHERE id = 1;`
5.  **결과:** Alice가 정당하게 차감한 500원 내역은 완전히 무시되고, Bob의 덮어쓰기로 인해 잔액은 1,300원이 됨.
    *   **Alice의 작업이 유실(Lost)되었습니다.**

### 2.2 낙관적 락의 방어
4번 단계에서 Bob의 `UPDATE` 문은 `WHERE version = 1` 조건을 만족하지 못해 실패합니다(이미 Alice가 2로 올림). 시스템은 Bob에게 "데이터가 변경되었습니다. 다시 조회하세요"라고 알릴 수 있습니다.

---

## 3. 오해와 진실

### 3.1 락이 걸리는가?
*   **아니오.** 조회(`SELECT`) 시점에는 아무런 락이 걸리지 않습니다.
*   **예.** 수정(`UPDATE`) 쿼리가 실행되는 그 찰나의 순간에는 DB의 Row Lock이 걸립니다. 하지만 비관적 락처럼 트랜잭션 내내 잡고 있는 것이 아니므로 동시성이 월등히 높습니다.

### 3.2 ABA 문제 (ABA Problem)
*   **개념:** 값이 A -> B -> A로 변경되었을 때, 단순 값 비교만으로는 변경 사실을 모르는 문제.
*   **해결:** `version`은 1 -> 2 -> 3으로 **단조 증가(Monotonic Increment)**하기 때문에, 값이 다시 원래대로 돌아오더라도 버전은 다르므로 충돌을 감지할 수 있습니다.
