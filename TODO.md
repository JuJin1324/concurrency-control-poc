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

### 진행 방법

- [ ] AI와 함께 상의해서 진행
- [ ] 변경 시 CLAUDE.md, Makefile 등 관련 파일 업데이트 필요
