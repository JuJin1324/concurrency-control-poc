.PHONY: build up down ps logs clean mysql redis reset reset-100 reset-products reset-1k reset-10k show-db show-redis warmup stats test-capacity test-contention test-stress test-low-contention test-collision-rate

# 애플리케이션 빌드 및 이미지 생성
build:
	./gradlew clean bootJar
	docker compose build

# Docker Compose 실행 (백그라운드)
up:
	docker compose up -d

# Docker Compose 종료
down:
	docker compose down

# 컨테이너 상태 확인
ps:
	docker compose ps

# 로그 확인 (실시간)
logs:
	docker compose logs -f app

# 리소스 사용량 실시간 확인
stats:
	docker stats

# 초기화 (컨테이너 및 볼륨 삭제)
clean:
	docker compose down -v

# MySQL 접속
mysql:
	docker compose exec mysql mysql -u app_user -papp_password concurrency_db

# Redis 접속
redis:
	docker compose exec redis redis-cli

# --- [Data Management] ---

# 통합 데이터 리셋 (기본값: 1개 상품, 100개 재고)
# Usage: make reset-data PRODUCTS=5 QUANTITY=100000
reset-data:
	@echo "🔄 Resetting all data (Stock, Point, OrderHistory)..."
	@PRODUCTS=$(or $(PRODUCTS),1); \
	QUANTITY=$(or $(QUANTITY),100); \
	VALUES=$$(for i in $$(seq 1 $$PRODUCTS); do printf "('PRODUCT-%03d', $$QUANTITY)" $$i; [ $$i -lt $$PRODUCTS ] && printf ", "; done); \
	docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e " \
		TRUNCATE TABLE stock; \
		TRUNCATE TABLE point; \
		TRUNCATE TABLE order_history; \
		INSERT INTO stock (product_id, quantity) VALUES $$VALUES; \
		INSERT INTO point (user_id, balance) VALUES (1, 1000000); \
	" 2>/dev/null
	@docker compose exec redis redis-cli flushall > /dev/null
	@echo "🔄 Syncing to Redis..."
	@for i in $$(seq 1 $$PRODUCTS); do \
		curl -s -X POST http://localhost:8080/api/stock/$$i/sync > /dev/null 2>&1; \
	done
	@echo "✅ Reset Complete: $$PRODUCTS products ($$QUANTITY each), Redis Synced, Tables Truncated."

# 시나리오별 리셋 단축 타겟
reset:
	@make reset-data PRODUCTS=1 QUANTITY=100

reset-100:
	@make reset-data PRODUCTS=100 QUANTITY=100

reset-1k:
	@make reset-data PRODUCTS=1 QUANTITY=1000

reset-10k:
	@make reset-data PRODUCTS=1 QUANTITY=10000

reset-complex:
	@make reset-data PRODUCTS=1 QUANTITY=1000

reset-resource-protection:
	@make reset-data PRODUCTS=5 QUANTITY=100000

reset-low-contention:
	@make reset-data PRODUCTS=100 QUANTITY=100

reset-extreme:
	@make reset-data PRODUCTS=5 QUANTITY=100000

# --- [Display Utilities] ---

# 모든 관련 테이블 상태 통합 조회 (정합성 검증용)
show-db:
	@echo "\n📊 [Database Consistency Check]"
	@docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e " \
		SELECT 'STOCK' as table_name, id, quantity, version FROM stock WHERE id BETWEEN 1 AND 5; \
		SELECT 'TOTAL_STOCK' as table_name, SUM(quantity) as total_quantity, SUM(version) as total_version FROM stock WHERE id BETWEEN 1 AND 5; \
		SELECT 'POINT' as table_name, user_id, balance, version FROM point WHERE user_id = 1; \
		SELECT 'ORDER' as table_name, count(*) as 'total_history_count' FROM order_history; \
	" 2>/dev/null

# Redis 재고 데이터 조회 (stock:1~5) 및 합계
show-redis:
	@echo "\n📊 [Redis Stock Status]"
	@docker compose exec redis redis-cli mget stock:1 stock:2 stock:3 stock:4 stock:5 | awk 'BEGIN {sum=0; i=1} {if ($$0 != "") {print "stock:" i ": " $$0; sum+=$$0; i++}} END {print "------------------\nTotal Stock: " sum}'

# --- [Test Utilities] ---

# Docker K6 실행 명령어 정의 (내부망 실행)
K6_CMD = docker run --rm -i \
	--network concurrency-control-poc_concurrency-network \
	-v $(shell pwd)/k6-scripts:/scripts \
	-e BASE_URL=http://app:8080 \
	grafana/k6:latest run

# Warm-up (시스템 예열)
# Usage: make warmup METHOD=lua-script
warmup:
	@echo "🔥 Warming up (200 iters)..."
	@$(K6_CMD) -e METHOD=$(or $(METHOD),pessimistic) /scripts/warmup.js > /dev/null 2>&1
	@echo "✅ Warm-up Done"

# Capacity Test (처리량 측정)
# Usage: make test-capacity METHOD=lua-script VUS=100 ITERATIONS=1000
test-capacity: warmup
	@echo "🚀 Starting Capacity Test (METHOD=$(or $(METHOD),pessimistic), VUS=$(or $(VUS),100), ITERS=$(or $(ITERATIONS),1000))"
	$(K6_CMD) -e METHOD=$(or $(METHOD),pessimistic) -e VUS=$(or $(VUS),100) -e ITERATIONS=$(or $(ITERATIONS),1000) /scripts/capacity.js

# Contention Test (경합/안정성 측정 - Large Scale)
# Usage: make test-contention METHOD=lua-script VUS=5000 DURATION=30s
test-contention:
	@echo "🏗️  Scaling up infrastructure for 5,000 VUs (AWS t3.xlarge equivalent)..."
	docker compose down -v
	docker compose -f docker-compose.yml -f docker-compose.large.yml up -d
	@echo "⏳ Waiting for infrastructure to stabilize (15s)..."
	sleep 15
	@echo "🔥 Warming up (200 iters)..."
	@$(K6_CMD) -e METHOD=$(or $(METHOD),pessimistic) /scripts/warmup.js > /dev/null 2>&1
	@echo "✅ Warm-up Done"
	@make reset
	@echo "🚀 Starting Contention Test (METHOD=$(or $(METHOD),pessimistic), VUS=$(or $(VUS),5000), DUR=$(or $(DURATION),30s))"
	$(K6_CMD) -e METHOD=$(or $(METHOD),pessimistic) -e VUS=$(or $(VUS),5000) -e DURATION=$(or $(DURATION),30s) /scripts/contention.js

# Stress Test (한계 탐색)
# Usage: make test-stress METHOD=lua-script TARGET_RPS=2000
test-stress: warmup
	@echo "🚀 Starting Stress Test (METHOD=$(or $(METHOD),pessimistic), TARGET_RPS=$(or $(TARGET_RPS),2000))"
	$(K6_CMD) -e METHOD=$(or $(METHOD),pessimistic) -e TARGET_RPS=$(or $(TARGET_RPS),2000) /scripts/stress.js

# --- [Scenario 1: Complex Transaction] ---

test-complex-pessimistic: reset-infra warmup reset-complex
	@echo "🚀 Starting Complex Transaction Scenario (Pessimistic)"
	$(K6_CMD) -e METHOD=pessimistic /scripts/1-complex-transaction.js
	@make show-db

test-complex-optimistic-no-retry: reset-infra warmup reset-complex
	@echo "🚀 Starting Complex Transaction Scenario (Optimistic No-Retry)"
	$(K6_CMD) -e METHOD=optimistic-no-retry /scripts/1-complex-transaction.js
	@make show-db

test-complex-optimistic-retry: reset-infra warmup reset-complex
	@echo "🚀 Starting Complex Transaction Scenario (Optimistic Retry)"
	$(K6_CMD) -e METHOD=optimistic-retry /scripts/1-complex-transaction.js
	@make show-db

test-complex-transaction: test-complex-pessimistic

# 인프라 완전 재시작 (격리된 테스트 환경 보장)
reset-infra:
	@echo "🧹 Cleaning up infrastructure..."
	@docker compose down -v > /dev/null 2>&1
	@echo "🏗️  Starting fresh infrastructure..."
	@docker compose up -d > /dev/null 2>&1
	@echo "⏳ Waiting for DB to be ready..."
	@sleep 10

# --- [Scenario 2: Low Contention] ---

test-low-contention-pessimistic: reset-infra warmup reset-low-contention
	@echo "🚀 Starting Low Contention Scenario (Pessimistic)"
	$(K6_CMD) -e METHOD=pessimistic -e PRODUCT_COUNT=100 /scripts/2-low-contention.js
	@make show-db

test-low-contention-optimistic-no-retry: reset-infra warmup reset-low-contention
	@echo "🚀 Starting Low Contention Scenario (Optimistic No-Retry)"
	$(K6_CMD) -e METHOD=optimistic-no-retry -e PRODUCT_COUNT=100 /scripts/2-low-contention.js
	@make show-db

test-low-contention-optimistic-retry: reset-infra warmup reset-low-contention
	@echo "🚀 Starting Low Contention Scenario (Optimistic Retry)"
	$(K6_CMD) -e METHOD=optimistic-retry -e PRODUCT_COUNT=100 /scripts/2-low-contention.js
	@make show-db

test-low-contention: test-low-contention-pessimistic

# --- [Scenario 3: Resource Protection] ---

test-resource-protection-pessimistic: reset-infra warmup reset-resource-protection
	@echo "🚀 Starting Resource Protection Scenario (Pessimistic)"
	$(K6_CMD) -e METHOD=pessimistic /scripts/3-resource-protection.js
	@make show-db

test-resource-protection-redis-optimistic: reset-infra warmup reset-resource-protection
	@echo "🚀 Starting Resource Protection Scenario (Redis-Optimistic)"
	$(K6_CMD) -e METHOD=redis-optimistic /scripts/3-resource-protection.js
	@make show-db

test-resource-protection-optimistic: reset-infra warmup reset-resource-protection
	@echo "🚀 Starting Resource Protection Scenario (Optimistic No-Retry)"
	$(K6_CMD) -e METHOD=optimistic /scripts/3-resource-protection.js
	@make show-db

# --- [Scenario 4: Extreme Performance (No Delay)] ---

test-extreme-pessimistic: reset-infra warmup reset-extreme
	@echo "🚀 Starting Scenario 4 (Pessimistic - No Delay)"
	$(K6_CMD) -e METHOD=pessimistic /scripts/4-extreme-performance.js
	@make show-db

test-extreme-optimistic: reset-infra warmup reset-extreme
	@echo "🚀 Starting Scenario 4 (Optimistic - No Delay)"
	$(K6_CMD) -e METHOD=optimistic /scripts/4-extreme-performance.js
	@make show-db

test-extreme-redis: reset-infra warmup reset-extreme
	@echo "🚀 Starting Scenario 4 (Redis Lock - No Delay)"
	$(K6_CMD) -e METHOD=redis /scripts/4-extreme-performance.js
	@make show-db

test-extreme-lua: reset-infra warmup reset-extreme
	@echo "🚀 Starting Scenario 4 (Lua Script - No Delay)"
	$(K6_CMD) -e METHOD=lua /scripts/4-extreme-performance.js
	@make show-redis

# Legacy Low Contention Test (Sprint 1-6)
test-low-contention-v1: warmup
	@make reset-100
	@echo "🚀 Starting Legacy Low Contention Test"
	$(K6_CMD) -e METHOD=$(or $(METHOD),optimistic) -e VUS=$(or $(VUS),1000) -e ITERATIONS=$(or $(ITERATIONS),1000) /scripts/2-low-contention.js