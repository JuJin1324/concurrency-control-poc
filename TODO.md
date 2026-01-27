# TODO

## 프로젝트 구조 정리

### 1. 문서 파일 정리

**현재 상태:** 루트에 흩어져 있음
- `how-diagram.md`
- `brainstorm.md`
- `SCAFFOLDING_REPORT.md`

**고민:** 한 곳에 모아두고 싶음 (예: `docs/planning/` 또는 `docs/`)

---

### 2. Docker 관련 파일 정리

**현재 상태:**
- `docker-compose.yml` - 루트
- `mysql-init/` - 루트 (docker-compose와 동떨어져 있음)

**고민:** `docker/` 디렉터리를 만들어서 관리할지?
```
docker/
├── docker-compose.yml
└── mysql-init/
    └── init.sql
```

---

### 3. 프로젝트 정합성 검증 (Alignment Check)

**목표:** 초기 기획(`how-diagram.md`, `brainstorm.md`)과 실제 수행(`agile/sprints`) 간의 괴리 검증
- [ ] Sprint 0 ~ 3의 수행 내용이 `how-diagram.md`의 목표(Why)와 일치하는지 전수 검사
- [ ] 특히 "대규모 트래픽", "정량 지표" 등 핵심 KPI 달성 여부 확인
- [ ] Sprint 4 (문서화) 진행 시 최종 점검

---

### 4. README.md 고도화 (The Face of Project & Onboarding)

**목표:** README.md를 "별도의 문서 없이도 누구나 5분 안에 실행 가능한 온보딩 문서"로 전환
- [ ] **Quick Start 최상단 배치:** `make build && make up` 등 원라이너 실행 명령어 강조
- [ ] **Sprint별 요약 섹션 추가:** Sprint 1(DB), Sprint 2(Redis), Sprint 3(Performance)의 핵심 결과 요약
- [ ] **성능 리포트 링크:** 상세 데이터(`docs/performance-test-result.md`)로 연결되는 요약 테이블 제공
- [ ] **실행 가이드 통합:** `Makefile` 사용법 및 환경 구축 가이드를 메인에 배치
- [ ] **AI 위임 가능:** Sprint 4 진행 시 AI에게 전체 히스토리를 요약하여 README 작성을 위임할 것

---

### 진행 방법

- [ ] AI와 함께 상의해서 진행
- [ ] 변경 시 CLAUDE.md, Makefile 등 관련 파일 업데이트 필요