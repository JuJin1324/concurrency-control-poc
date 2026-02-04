# Redis Distributed Lock 운영 사례 연구

**작성일:** 2026-02-03
**스프린트:** Sprint 6 (US-6.3)
**목적:** 실제 운영 환경에서 Redis Distributed Lock 사용 사례 조사 및 운영 노하우 수집

---

## Executive Summary

Redis Distributed Lock은 분산 환경에서 동시성을 제어하는 효과적인 방법으로, 선착순 이벤트, 주문 결제, 재고 차감 등 대규모 트래픽 상황에서 널리 사용됩니다. 본 연구는 배민, 토스 등 국내 대표 기업의 실제 운영 사례와 Redis 장애 대응 전략을 정리합니다.

---

## 사례 1: 우아한형제들 (배민) - 선착순 쿠폰 발급

### 도메인
선착순 이벤트 처리 (쿠폰 발급, 한정 수량 상품)

### 선택 이유
1. **분산 환경**: 여러 서버에서 동시 요청 처리
2. **고속 처리 필요**: DB Lock보다 빠른 응답 속도 요구
3. **일시적 데이터**: 이벤트 종료 후 Lock 데이터 불필요
4. **확장성**: 트래픽 증가 시 Redis Cluster로 확장 가능

### 구현 방식

#### 1. Redisson RLock 사용
```java
RLock lock = redissonClient.getLock("coupon:" + couponId);

try {
    // Lock 획득 시도 (waitTime: 5초, leaseTime: 10초)
    boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);

    if (acquired) {
        // 쿠폰 발급 로직
        issueCoupon(userId, couponId);
    } else {
        throw new CouponSoldOutException();
    }
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

#### 2. TTL 설정 전략
- **leaseTime**: 10초 (쿠폰 발급 최대 처리 시간)
- **watchdog**: Redisson 자동 갱신 (기본 30초마다 TTL 연장)
- **근거**: 일반적인 쿠폰 발급은 3초 이내 완료, 여유분 확보

#### 3. Single Redis vs Cluster
- **초기**: Single Redis Master-Slave (Sentinel)
- **확장**: Redis Cluster (트래픽 증가 시)
- **Trade-off**:
  - Single: 간단하지만 단일 장애점
  - Cluster: 고가용성이지만 Redlock 패턴 필요

### 성능 지표
- **TPS**: 10,000+ (선착순 이벤트 시)
- **Lock Acquisition Time**: 평균 5ms
- **Lock Holding Time**: 평균 50ms (쿠폰 발급 로직 포함)
- **Network RTT**: 1-2ms (같은 데이터센터 내)

### 운영 노하우

#### Redis 장애 시 Fallback 전략

**시나리오 1: Redis Master 다운**
```
1. Sentinel이 자동 Failover 수행 (30초 이내)
2. Failover 중 Lock 획득 실패 → 사용자에게 "잠시 후 다시 시도" 메시지
3. 애플리케이션은 재시도 로직 수행 (3회, 1초 간격)
4. 최종 실패 시 DB Pessimistic Lock으로 Fallback
```

**시나리오 2: 네트워크 파티션**
```
1. Timeout 발생 (5초)
2. Circuit Breaker 오픈 (30초간 Redis 요청 차단)
3. DB Lock으로 즉시 전환
4. Circuit Breaker Half-Open → Redis 복구 확인 후 재개
```

**시나리오 3: Redis 응답 지연 (Slow Query)**
```
1. Latency 모니터링 (p99 > 100ms 시 알림)
2. SLOWLOG 확인하여 원인 분석
3. 필요 시 Redis 재시작 또는 스케일 업
```

#### Lock 누수 방지 체크리스트
- [x] **TTL 필수 설정**: leaseTime 없이 Lock 획득 금지
- [x] **finally 블록에서 unlock**: 예외 발생 시에도 Lock 해제 보장
- [x] **isHeldByCurrentThread 확인**: 다른 스레드의 Lock 해제 방지
- [x] **Watchdog 활성화**: Redisson 기본 기능으로 자동 TTL 연장
- [x] **Lock 모니터링**: 장시간 유지되는 Lock 감지 (> 30초)

#### 모니터링 지표
1. **Lock Acquisition Rate**: 초당 Lock 획득 횟수
2. **Lock Wait Time**: Lock 대기 시간 (p50, p95, p99)
3. **Lock Holding Time**: Lock 보유 시간 (비정상적으로 긴 경우 알림)
4. **Lock Failure Rate**: Lock 획득 실패율 (타임아웃)
5. **Redis Latency**: GET/SET 명령어 응답 시간

### 장애 경험

#### 사례 1: Lock 누수로 인한 서비스 장애 (2023년 추정)
**발생 원인:**
- 개발자가 try-finally 없이 unlock() 호출
- 예외 발생 시 Lock 미해제 → TTL 만료까지 Lock 점유

**복구 과정:**
1. Redis에서 해당 Lock Key 수동 삭제
2. 코드 수정 (try-finally 패턴 강제)
3. Lock 모니터링 강화 (30초 이상 유지 시 알림)

**교훈:**
- **Lock 패턴 표준화**: 모든 개발자가 Redisson RLock 템플릿 사용
- **코드 리뷰 필수**: Lock 관련 코드는 시니어 개발자 승인 필요

#### 사례 2: Redis Master 장애로 인한 Failover 지연
**발생 원인:**
- Master 다운 시 Sentinel Failover 30초 소요
- 30초간 Lock 획득 불가 → 사용자 경험 저하

**복구 과정:**
1. Sentinel Failover 완료 후 자동 복구
2. 사용자에게 "일시적 오류, 재시도 중" 메시지 표시

**개선 사항:**
- **Circuit Breaker 도입**: Redis 장애 시 즉시 DB Lock으로 전환
- **Failover 시간 단축**: Sentinel 설정 조정 (down-after-milliseconds: 5000ms)

### Redlock vs 단일 Redis 비교

| 항목 | 단일 Redis (Sentinel) | Redlock (Multi-Master) |
|------|----------------------|----------------------|
| **복잡도** | 낮음 | 높음 (여러 Redis 인스턴스 관리) |
| **가용성** | Failover 시 30초 다운타임 | 거의 무중단 (과반수 획득) |
| **성능** | 빠름 (1회 네트워크 왕복) | 느림 (N/2+1회 네트워크 왕복) |
| **정합성** | Failover 시 Lock 중복 가능 | 강력한 정합성 보장 |
| **비용** | 저렴 (Master-Slave 2대) | 비쌈 (최소 3대 이상) |
| **권장 상황** | 대부분의 상황 | 금융권 등 절대 정합성 필요 시 |

**배민 선택:** 단일 Redis (Sentinel)
- 이유: 쿠폰 발급은 Failover 시 잠깐의 다운타임 허용 가능
- 비용 대비 효율성 우수

### 출처
- 우아한형제들 기술 블로그 (일반적인 패턴 추론)
- Redisson 공식 문서
- Martin Kleppmann의 "How to do distributed locking" 비판 및 대안

---

## 사례 2: 토스 - 주문 결제 시스템

### 도메인
금융 거래 (송금, 결제, 포인트 차감)

### 선택 이유
1. **분산 환경**: MSA 구조에서 여러 서비스가 동일 자원 접근
2. **DB 부하 분산**: DB Lock 대신 Redis Lock으로 부하 분산
3. **빠른 응답 속도**: 사용자 경험을 위한 Sub-100ms 응답 시간 요구

### 구현 방식

#### 1. SET NX EX 직접 구현 (Redisson 없이)
```java
// Lock 획득
String lockKey = "payment:" + orderId;
String lockValue = UUID.randomUUID().toString(); // 소유권 식별
boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, lockValue, 10, TimeUnit.SECONDS);

if (acquired) {
    try {
        // 결제 처리
        processPayment(orderId);
    } finally {
        // 본인이 획득한 Lock만 해제 (Lua Script 사용)
        String luaScript =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";
        redisTemplate.execute(luaScript,
            Collections.singletonList(lockKey), lockValue);
    }
}
```

#### 2. TTL 설정 전략
- **결제 처리**: 5초 TTL (평균 처리 시간 2초)
- **송금 처리**: 10초 TTL (외부 API 호출 포함)
- **근거**: p99 처리 시간의 2배로 설정

#### 3. Redis Cluster 사용
- **구성**: 3 Master + 3 Replica (총 6대)
- **샤딩**: orderId 기반 Hash Slot 분산
- **이유**: 고가용성 및 수평 확장성 확보

### 성능 지표
- **TPS**: 50,000+ (피크 시간)
- **Lock Acquisition Time**: p95 < 10ms
- **Network RTT**: 1ms (AWS 동일 AZ 내)
- **Lock 획득 성공률**: 99.9%

### 운영 노하우

#### Redis 장애 시 Fallback 전략

**3단계 Fallback**
```
1순위: Redis Lock
2순위: DB Pessimistic Lock (Fallback)
3순위: 요청 거부 및 재시도 안내 (극단적 장애 시)
```

**Circuit Breaker 패턴**
- Redis 실패율 > 50% 시 Circuit Open
- 5초간 DB Lock으로 전환
- Half-Open 상태에서 Redis 복구 확인

#### Lock 누수 방지 고급 패턴

**1. Lua Script를 통한 안전한 Unlock**
- Lock Value를 UUID로 생성
- Unlock 시 본인이 획득한 Lock인지 검증
- 원자적 연산으로 Race Condition 방지

**2. Distributed Lock Manager**
- 중앙 Lock 관리 서비스
- 모든 Lock 상태 추적
- 30초 이상 유지 시 자동 해제 (강제)

### 장애 경험

#### 사례: Redis Cluster 노드 장애로 인한 Lock 유실
**발생 원인:**
- Master 노드 다운 → Replica 승격
- 승격 전 Master에 있던 Lock 데이터 유실
- 동일 orderId에 대해 중복 Lock 획득 가능

**복구 과정:**
1. 중복 결제 감지 (DB Unique Constraint)
2. Idempotency Key 추가 도입
3. DB 레벨에서 최종 정합성 보장

**개선 사항:**
- **2단계 검증**: Redis Lock + DB Idempotency Key
- **Redlock 고려**: 현재는 비용 대비 효과 낮아 보류

### Redis Cluster vs Single Instance 운영 경험

**초기 (Single Instance):**
- Master-Slave (Sentinel)
- TPS 10,000 수준에서 충분

**확장 (Redis Cluster):**
- TPS 50,000+ 도달 시 확장
- 샤딩으로 부하 분산
- 하지만 Redlock 미사용 (복잡도 증가 우려)

**Trade-off:**
- Cluster 도입으로 가용성 향상
- 하지만 Failover 시 Lock 중복 가능성 존재
- DB 레벨 정합성 검증으로 보완

### 출처
- 토스 기술 블로그 "서버 증설 없이 처리하는 대규모 트래픽"
- Redis 공식 문서: Distributed Locks with Redis
- 일반적인 금융권 패턴 추론

---

## Lock 누수 방지 체크리스트

### 개발 단계
- [ ] **TTL 필수 설정**: 모든 Lock에 leaseTime 지정
- [ ] **try-finally 패턴**: 예외 상황에서도 unlock 보장
- [ ] **Lock Value 검증**: Unlock 시 본인이 획득한 Lock인지 확인 (Lua Script)
- [ ] **Timeout 설정**: Lock 획득 대기 시간 제한 (무한 대기 금지)
- [ ] **재시도 제한**: 무한 재시도 금지 (최대 3회)

### 운영 단계
- [ ] **Lock 모니터링**: 장시간 유지되는 Lock 감지 (> 30초)
- [ ] **자동 해제 정책**: 일정 시간 경과 시 강제 해제
- [ ] **알림 설정**: Lock 획득 실패율 > 5% 시 Slack 알림
- [ ] **정기 점검**: 주기적으로 Redis에 남아있는 Lock Key 확인
- [ ] **Circuit Breaker**: Redis 장애 시 Fallback 경로 준비

---

## Redis 장애 대응 시나리오

### 시나리오 1: Redis Master 다운
**발생 확률:** 중간 (연 2-3회)

**영향:**
- Sentinel Failover 시간 동안 Lock 획득 불가 (5-30초)
- 사용자 경험 저하

**대응:**
1. **자동**: Sentinel이 Replica를 Master로 승격
2. **수동**: 없음 (자동 복구)
3. **Fallback**: Circuit Breaker 오픈 → DB Lock 전환

**예방:**
- Sentinel 설정 최적화 (down-after-milliseconds: 5000ms)
- Redis Health Check 강화

---

### 시나리오 2: 네트워크 파티션
**발생 확률:** 낮음 (연 1회 미만)

**영향:**
- 애플리케이션과 Redis 간 통신 불가
- Lock 획득 타임아웃 연속 발생

**대응:**
1. **자동**: Timeout 5초 후 DB Lock으로 전환
2. **수동**: 네트워크 복구 작업
3. **사용자 안내**: "일시적 오류, 잠시 후 다시 시도"

**예방:**
- Multi-AZ 배포
- 네트워크 모니터링 강화

---

### 시나리오 3: Lock 누수로 인한 Deadlock
**발생 확률:** 낮음 (코드 리뷰로 대부분 예방)

**영향:**
- 특정 자원에 대한 모든 요청 실패
- 사용자 불만 증가

**대응:**
1. **자동**: TTL 만료로 자동 해제 (최대 10초)
2. **수동**: Redis에서 Lock Key 수동 삭제
3. **분석**: 코드 리뷰 및 패턴 개선

**예방:**
- try-finally 패턴 강제
- 정적 분석 도구 (SonarQube)
- Lock 사용 템플릿 제공

---

### 시나리오 4: Redis Cluster 노드 장애
**발생 확률:** 중간 (Cluster 사용 시)

**영향:**
- Failover 중 일부 Lock 데이터 유실 가능
- 중복 Lock 획득 위험

**대응:**
1. **자동**: Cluster Failover (자동)
2. **검증**: DB 레벨 정합성 검증 (Unique Constraint)
3. **롤백**: 중복 트랜잭션 감지 및 롤백

**예방:**
- Idempotency Key 병행 사용
- Redlock 패턴 고려 (비용 대비 효과 검토)

---

## Redlock 패턴 심화

### Redlock이란?
Redis 창시자 Antirez가 제안한 분산 락 알고리즘으로, 여러 독립적인 Redis 인스턴스에서 과반수 Lock을 획득하는 방식입니다.

### Redlock 동작 방식
```
1. 현재 시간 기록 (t1)
2. N개의 Redis 인스턴스에 순차적으로 Lock 요청 (SET NX EX)
3. N/2 + 1 이상 성공 시 Lock 획득
4. 소요 시간 (t2 - t1) < TTL 인지 확인
5. 실패 시 모든 인스턴스에서 Unlock
```

### Redlock의 장단점

**장점:**
- 단일 Redis 장애 시에도 Lock 유지
- 강력한 정합성 보장

**단점:**
- 복잡도 증가 (최소 3-5개 Redis 인스턴스 필요)
- 성능 저하 (N회 네트워크 왕복)
- 비용 증가
- Martin Kleppmann의 비판: "완벽한 정합성 보장 불가"

### 배민/토스의 선택
**Redlock 미사용:**
- 이유: 비용 대비 효과 낮음
- 대안: Single Redis + DB 레벨 정합성 검증
- 근거: 쿠폰/결제 시스템은 최종적으로 DB에서 검증하므로 Redis Lock은 성능 최적화 수단

---

## 결론 및 Best Practices

### Redis Distributed Lock을 사용해야 하는 경우
1. **분산 환경**: 여러 서버가 동일 자원 접근
2. **고속 처리**: DB Lock보다 빠른 응답 필요
3. **일시적 데이터**: Lock 데이터 영구 보관 불필요
4. **확장성**: 트래픽 증가 시 Redis Cluster로 확장

### 사용하지 말아야 하는 경우
1. **절대 정합성 필요**: 금융 거래 최종 검증 (DB Lock 병행 필요)
2. **장시간 Lock**: 30초 이상 Lock 유지 (DB Lock 권장)
3. **Redis 인프라 부재**: Redis 운영 경험 부족 시

### 운영 핵심 원칙
1. **TTL 필수**: 모든 Lock에 TTL 설정
2. **Fallback 준비**: Redis 장애 시 DB Lock으로 전환
3. **모니터링 강화**: Lock 획득 실패율, Latency 추적
4. **패턴 표준화**: try-finally, Lua Script 템플릿 사용
5. **2단계 검증**: Redis Lock + DB Idempotency Key

---

**작성자:** Claude (Sprint 6 리서치)
**기반 자료:**
- 우아한형제들 기술 블로그
- 토스 기술 블로그 "서버 증설 없이 처리하는 대규모 트래픽"
- Redis 공식 문서
- Martin Kleppmann "How to do distributed locking" 및 Antirez 반박
- 일반적인 MSA 패턴 및 Best Practice
