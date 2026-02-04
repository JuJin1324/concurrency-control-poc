# Sprint 6: 심화 연구 및 실무 사례 분석 (Deep Dive & Case Study)

**기간:** 2026-02-03 ~ 2026-02-17 (14일)
**목표:** 각 동시성 제어 방식의 실제 현업 도입 사례를 연구하고, 개발 중심에서 운영 중심으로 관점을 전환하여 시니어 개발자의 통찰력을 증명한다.
**Phase:** Phase 5 (Deep Dive)

---

## Sprint Goal

> **"개발만 하는 개발자가 아닌, 운영까지 고려하는 시니어 개발자의 시각을 확보한다."**

---

## 워크플로우 철학

> **Sprint 0-5 vs Sprint 6 관점 전환**

**Sprint 0-5 (개발 중심):**
- "어떻게 구현하는가?" - 코드 작성, 테스트, 성능 측정
- 기술적 검증 완료 (✅ PoC 목표 100% 달성)

**Sprint 6 (운영 중심):**
- "어떻게 운영하는가?" - 실무 사례, 장애 대응, 모니터링, 트레이드오프
- 실무 적용 가능성 증명 (이직 시장 어필력 강화)

---

## Tasks

### Iteration 1: 지식 자산화 - 실무 사례 리서치 (Research & Analysis)

**목표:** 각 방식의 실제 현업 도입 사례를 조사하고, 운영 관점의 인사이트를 정리한다.

**🔄 Revised Workflow (Efficiency Optimized):**
기존의 산발적인 리서치 자료 나열 방식은 인지 부하가 크므로, 프로세스를 개선한다.
1.  **Agent 선행 분석:** Agent가 Deep Research 및 기존 자료를 먼저 소화.
2.  **Ops 문서 초안 작성:** `docs/operations/TEMPLATE.md` 기반으로 구조화된 가이드 문서(Hub & Spoke) 작성.
3.  **사용자 리뷰:** 사용자는 잘 정리된 Ops 문서만 집중적으로 리뷰.
4.  **수정 및 확정:** 피드백 반영 후 태스크 완료.
5.  **Ops 문서 작업 원칙 (Never Shrink):** 문서를 수정할 때 기존의 상세한 내용(사례, 코드, 로직)을 절대 축소하거나 삭제하지 않는다. 새로운 인사이트는 '추가(Append)'하는 방식으로 반영하여 정보의 총량을 보존한다.

#### US-6.1: Pessimistic Lock 운영 사례 연구
- [x] 금융권 계좌 이체 시스템 사례 조사 (은행, 핀테크)
- [x] 재고 관리 시스템 사례 조사 (커머스)
- [x] 운영 시 고려사항 정리 (Deadlock, Timeout, Monitoring)
- [x] 장애 시나리오 및 대응 방안 작성
- [x] **Output:** `docs/operations/pessimistic-lock-ops.md` (및 하위 상세 문서)

---

#### US-6.2: Optimistic Lock 운영 사례 연구
- [ ] **[선행]** `docs/operations/optimistic-lock-ops.md` 초안 작성 (TEMPLATE 기반)
- [ ] 위키 편집 시스템 사례 조사 (Confluence, Notion)
- [ ] 자원 경합이 낮은 웹 서비스 사례 조사
- [ ] 운영 시 고려사항 정리:
  - 충돌 발생률 모니터링 방법
  - Retry 전략 (횟수, 간격, Backoff)
  - 사용자 경험 (UX) 영향 분석
- [ ] 언제 Pessimistic으로 전환해야 하는지 기준 수립

**Acceptance Criteria:**
- Ops 문서 내 충돌률에 따른 전략 전환 기준 정의
- Ops 문서 내 Retry 로직 Best Practice 정리

---

#### US-6.3: Redis Lock 운영 사례 연구
- [ ] **[선행]** `docs/operations/redis-lock-ops.md` 초안 작성 (TEMPLATE 기반)
- [ ] 배민 선착순 쿠폰 사례 조사
- [ ] 토스 주문 결제 시스템 사례 조사
- [ ] 분산 환경 Lock 패턴 조사 (Redlock, Single Redis)
- [ ] 운영 시 고려사항 정리:
  - Redis 장애 시 Fallback 전략
  - Lock 누수 방지 (TTL, Watchdog)
  - 네트워크 지연(RTT) 영향 분석
- [ ] Redis Cluster vs Single Instance 트레이드오프 분석

**Acceptance Criteria:**
- Ops 문서 내 Redis 장애 시 대응 시나리오 3가지 이상 정리
- Ops 문서 내 Lock 누수 방지 체크리스트 작성

---

#### US-6.4: Lua Script 운영 사례 연구
- [ ] **[선행]** `docs/operations/lua-script-ops.md` 초안 작성 (TEMPLATE 기반)
- [ ] 초고부하 선착순 이벤트 사례 조사 (티켓팅, 쿠폰 발급)
- [ ] Redis + DB 동기화 전략 조사 (Eventual Consistency)
- [ ] 운영 시 고려사항 정리:
  - Lua Script 버전 관리 방법
  - Script 성능 모니터링 (SLOWLOG)
  - DB 동기화 실패 시 보상 트랜잭션
- [ ] Script 복잡도 증가 시 한계점 분석

**Acceptance Criteria:**
- Ops 문서 내 Script 버전 관리 Best Practice 정리
- Ops 문서 내 DB 동기화 실패 복구 시나리오 작성

---

**✅ Iteration 1 완료 조건:**
- 4가지 방식 각각 2개 이상의 실제 사례 확보
- 운영 체크리스트 4개 작성 완료
- 장애 시나리오 및 대응 방안 정리 완료

---

### Iteration 2: 지식 통합 - 실무 적용 가이드 문서화 (Documentation)

**목표:** 리서치 결과를 체계적으로 정리하여 실무 적용 가능한 가이드를 작성한다.

#### US-6.5: 방식별 운영 가이드 작성
- [ ] `docs/operations/pessimistic-lock-ops.md` 작성
  - 도입 시 체크리스트
  - 모니터링 지표 및 알림 설정
  - 장애 대응 플레이북
- [ ] `docs/operations/optimistic-lock-ops.md` 작성
- [ ] `docs/operations/redis-lock-ops.md` 작성
- [ ] `docs/operations/lua-script-ops.md` 작성

**Acceptance Criteria:**
- 각 문서 3,000자 이상 (실질적 내용)
- 실제 운영 시 참고 가능한 수준의 디테일

---

#### US-6.6: 의사결정 트리 (Decision Tree) 작성
- [ ] "어떤 상황에 어떤 방식을 선택할 것인가" 플로우차트 작성
- [ ] 판단 기준:
  - 트래픽 규모 (TPS)
  - 충돌 빈도
  - 데이터 정합성 요구 수준
  - 분산 환경 여부
  - 운영 복잡도 허용 범위
- [ ] Mermaid Diagram으로 시각화

**Acceptance Criteria:**
- Decision Tree를 보고 5분 안에 방식 선택 가능
- 최소 10개 이상의 판단 노드 포함

---

#### US-6.7: 통합 케이스 스터디 리포트 작성
- [ ] `docs/reports/case-study-v1.md` 작성
  - 4가지 방식의 실무 도입 사례 통합
  - 각 사례별 성능 지표, 장애 경험, 교훈
  - 운영 관점의 비교 매트릭스
- [ ] 표 형식으로 정리:
  - 회사명 / 도메인 / 선택 방식 / 이유 / 결과

**Acceptance Criteria:**
- 최소 8개 이상의 실제 사례 포함 (각 방식당 2개)
- 운영 비교 매트릭스 작성 완료

---

**✅ Iteration 2 완료 조건:**
- 운영 가이드 4개 작성 완료
- Decision Tree 시각화 완료
- 케이스 스터디 리포트 작성 완료

---

### Iteration 3: 포트폴리오 업그레이드 - README 강화 및 회고 (Portfolio Enhancement)

**목표:** 기존 문서들을 운영 관점으로 업그레이드하고, 이직 시장 어필력을 극대화한다.

#### US-6.8: README 업그레이드 (운영 섹션 추가)
- [ ] 기존 README에 "운영 가이드" 섹션 추가
- [ ] Decision Tree 다이어그램 삽입
- [ ] 각 방식별 운영 가이드 링크 추가
- [ ] "이 프로젝트의 차별점" 섹션 강화:
  - ✅ 성능 비교 (Sprint 0-5)
  - ✅ 운영 사례 분석 (Sprint 6)
  - ✅ 실무 적용 가능

**Acceptance Criteria:**
- README 길이 2배 증가 (실질적 내용 추가)
- "운영까지 고려한 프로젝트"임을 명확히 표현

---

#### US-6.9: 최종 회고 작성 (Project Retrospective)
- [ ] 전체 프로젝트 회고 작성 (`docs/retrospective/project-final.md`)
- [ ] 내용:
  - Sprint 0-6 전체 여정 요약
  - What/Why/How 재정의 과정
  - PoC 범위 확정의 가치
  - 개발 중심 → 운영 중심 전환의 의미
  - 이직 시장 어필 포인트 정리
  - 향후 확장 가능성 (Option)

**Acceptance Criteria:**
- 회고 문서 3,000자 이상 작성
- "왜 이 프로젝트가 이직용 포트폴리오로 적합한가" 명확히 설명

---

**✅ Iteration 3 완료 조건:**
- README 업그레이드 완료
- 프로젝트 최종 회고 작성 완료

---

## Sprint 6 Definition of Done

### Iteration 1: 실무 사례 리서치 ✅
- [ ] 4가지 방식 각각 2개 이상의 실제 사례 확보
- [ ] 운영 체크리스트 4개 작성
- [ ] 장애 시나리오 및 대응 방안 정리

### Iteration 2: 실무 적용 가이드 문서화 ✅
- [ ] 운영 가이드 4개 작성 완료
- [ ] Decision Tree 시각화 완료
- [ ] 케이스 스터디 리포트 작성 완료

### Iteration 3: 포트폴리오 업그레이드 ✅
- [ ] README 운영 섹션 추가 완료
- [ ] 프로젝트 최종 회고 작성 완료

### 최종 검증
- [ ] 모든 문서 교차 검토 (오타, 링크 깨짐)
- [ ] GitHub 프로필 핀 고정 준비 완료
- [ ] 이직 시장 제출 가능 상태 확보

---

## Blockers

- 없음

---

## Notes

### Sprint 6의 가치

**기술적 차별화:**
- ❌ "구현만 했습니다" (주니어 수준)
- ✅ "구현하고, 운영까지 고려했습니다" (시니어 수준)

**이직 시장 어필:**
- 실무 사례 기반 인사이트
- 운영 가이드 및 Decision Tree 제공
- "대규모 트래픽 처리 경험" 증명 완료

**학습 효과:**
- 단순 구현 경험 → 실무 적용 가능한 통찰력
- "왜 이 방식을 선택했는가"에 대한 명확한 근거
- 장애 대응 시나리오까지 고려한 설계 능력

---

## 확장 가능성 (Option)

Sprint 6 완료 후 시간이 남으면 선택:

1. **모니터링 대시보드 구축:**
   - Prometheus + Grafana 연동
   - 4가지 방식별 실시간 메트릭 시각화

2. **Chaos Engineering 실험:**
   - Redis 장애 시나리오 재현
   - DB Deadlock 재현 및 복구 실험

3. **컨퍼런스 발표 자료 작성:**
   - 사내 스터디 또는 외부 컨퍼런스 발표용 PPT
   - "동시성 제어 A to Z" 튜토리얼

---

**Sprint 시작 조건:**
- Sprint 5 완료 (✅ 완료됨)
- 사용자 확정 대기 중
