# Sprint 5 Retrospective: 한계 돌파 및 테스트 엔지니어링

**기간:** 2026-01-30 ~ 2026-02-02
**목표:** 10,000 VUs 테스트 실패 원인을 해결하고, 목적 중심(Capacity/Contention/Stress)의 새로운 테스트 표준을 수립하여 시스템 성능을 재측정한다.

---

## 🚀 성과 요약 (Highlights)

### 1. 테스트 엔지니어링 체계 확립
- 기존의 모호했던 `High/Extreme/Hell` 구분을 폐기하고, 목적이 명확한 **Capacity(용량)**, **Contention(경합)**, **Stress(임계점)** 3단계 테스트 표준을 수립했습니다.
- **격리성(Isolation)** 확보: 매 테스트마다 인프라를 재시작(`make clean && make up`)하는 자동화 파이프라인을 구축하여 데이터 신뢰도를 100%로 끌어올렸습니다.

### 2. 정량적 성능 지표 확보 (Benchmark)
- **Lua Script:** 압도적 1위 입증. (Capacity: 1,583 TPS, Contention: 5,000 VUs 방어, Stress: 2,000 RPS 안정)
- **Redis Lock:** 네트워크 병목(Knee Point) 확인. (1,000 RPS에서 Latency 33ms로 증가)
- **Optimistic Lock:** 'Fast Fail' 효과로 품절 상황에서 비관적 락보다 우수한 성능 입증.

### 3. 문서화 고도화
- **`performance-v2.md`:** 모든 실험 결과를 집대성한 최종 리포트 작성.
- **`test-guides/`:** 각 시나리오별 상세 가이드 및 템플릿 표준화 완료.

---

## 🛑 아쉬운 점 (Lowlights)

- **Redis Lock 최적화 한계:** 분산 락의 구조적 한계(RTT)로 인해 드라마틱한 성능 개선은 어렵다는 것을 확인했습니다. (Scale-up 외엔 답이 없음)
- **초기 시행착오:** 테스트 격리성을 간과하여 초반 데이터에 노이즈가 섞였던 점. (Iteration 5에서 수정됨)

---

## 📝 교훈 (Lessons Learned)

> **"측정할 수 없으면 개선할 수 없고, 격리되지 않으면 신뢰할 수 없다."**

1.  **Isolation is King:** 성능 테스트에서 가장 중요한 변수는 '이전 테스트의 잔재'입니다. 인프라 리셋은 선택이 아닌 필수입니다.
2.  **Context Matters:** "낙관적 락이 느리다"는 반은 맞고 반은 틀립니다. 재고가 있을 땐(충돌) 느리지만, 없을 땐(읽기) 빠릅니다. 상황(Context)에 따라 정답은 바뀝니다.

---

## 🔜 Next Steps

- **프로젝트 종료:** PoC 범위 내의 모든 기술적 검증이 완료되었습니다.
- **포트폴리오화:** `README.md`와 `performance-v2.md`를 중심으로 GitHub 프로필에 핀 고정 및 블로그 포스팅 준비.
