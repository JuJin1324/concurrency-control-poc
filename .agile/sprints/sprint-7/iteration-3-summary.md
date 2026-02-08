# Iteration 3: US-7.5 "The Alliance" Scenario Summary

## 1. 개요
- **시나리오:** Scenario 3 - "The Alliance" (연합군: Redis Distributed Lock + Optimistic Lock)
- **목표:** 고부하(500 VUs) 상황에서 비관적 락(Pessimistic Lock) 대비 DB Connection Pool 보호 능력 및 시스템 안정성 증명
- **환경 설정:**
  - DB Connection Pool Size: 10 (HikariCP)
  - 가상 사용자 (VUs): 500
  - 테스트 기간: 20s
  - 상품 수: 5개 (경합 집중 유도)
  - 트랜잭션 지연: 100ms (비즈니스 로직 시뮬레이션)

## 2. 테스트 결과

| 지표 | Pessimistic Lock 단독 사용 | Redis Lock + Optimistic 조합 |
|------|--------------------------------|----------------------------|
| **성공 횟수 (HTTP 200)** | ~1,148 | **~1,096** |
| **실패 횟수 (Technical)** | **~5,000 (81%)** | **~643 (37%)** |
| **주요 실패 원인** | Connection Refused, Network Error | Redis Lock Timeout (제어된 실패) |
| **시스템 상태** | 불안정 (애플리케이션 응답 거부 발생) | **안정 (유입량 제어 가능)** |
| **DB Pool 보호** | 실패 (모든 스레드가 Pool 점유 시도) | **성공 (Redis 계층 대기로 Pool 보호)** |

## 3. 핵심 인사이트

### 🛡️ DB Connection Pool 보호 (계층적 제어의 승리)
- **비관적 락(Pessimistic Lock):**
  - `@Transactional`이 시작되는 순간 DB Connection을 점유합니다.
  - 500명의 사용자가 동시에 몰리면 10개의 Connection이 즉시 고갈되고, 나머지 490명은 Connection을 획득하기 위해 WAS 레벨에서 무한 대기합니다.
  - 이 과정에서 시스템 리소스가 고갈되어 `Connection Refused` 같은 치명적인 인프라 장애로 이어집니다.
- **조합 방식 (Redis Lock + Optimistic Lock):**
  - **1차 제어(Redis):** 사용자가 몰려도 Redis에서 먼저 락을 기다립니다. 이때 DB Connection은 사용하지 않습니다.
  - **2차 검증(Optimistic):** Redis 락을 획득한 소수의 스레드만 DB 트랜잭션에 진입하므로, Pool이 고갈되지 않습니다.
  - 상품이 5개일 경우 실질적으로 최대 5개의 Connection만 활발히 사용되므로, 시스템 가용성이 획기적으로 향상됩니다.

### ⚖️ 트레이드오프: 성능 vs 안정성
- 단순 처리량(TPS) 면에서는 비관적 락이 근소하게 높을 수 있으나(락 획득 오버헤드 차이), **시스템 전체의 생존력(Availability)** 측면에서는 연합군 체계가 압도적으로 우수합니다.
- Redis 락 타임아웃을 통해 실패를 예측 가능하게 제어할 수 있다는 점이 실무에서 큰 장점입니다.

## 4. 결론
"The Alliance" 체계는 대규모 트래픽이 예상되는 환경에서 **DB 커넥션 고갈이라는 'SPOF(Single Point of Failure)'를 방지하는 가장 효과적인 전략**임을 확인했습니다. 비관적 락은 안정적이지만, 커넥션 풀이 작은 환경에서는 시스템 전체를 마비시킬 위험이 있습니다.

---
**다음 단계:** US-7.6 Redis as Primary Storage 시나리오 검증 진행 예정.
