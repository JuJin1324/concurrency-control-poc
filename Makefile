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

# 데이터 전체 리셋 (Default: 100개)
reset:
	@docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "TRUNCATE TABLE stock; INSERT INTO stock (product_id, quantity) VALUES ('PRODUCT-001', 100);" 2>/dev/null
	@docker compose exec redis redis-cli set stock:1 100 > /dev/null
	@echo "🔄 Reset: Stock = 100"

# 데이터 리셋 (100개 상품, 각 재고 100개씩 - Low Contention Scenario)
reset-100:
	@docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "TRUNCATE TABLE stock; INSERT INTO stock (product_id, quantity) VALUES ('PRODUCT-001', 100), ('PRODUCT-002', 100), ('PRODUCT-003', 100), ('PRODUCT-004', 100), ('PRODUCT-005', 100), ('PRODUCT-006', 100), ('PRODUCT-007', 100), ('PRODUCT-008', 100), ('PRODUCT-009', 100), ('PRODUCT-010', 100), ('PRODUCT-011', 100), ('PRODUCT-012', 100), ('PRODUCT-013', 100), ('PRODUCT-014', 100), ('PRODUCT-015', 100), ('PRODUCT-016', 100), ('PRODUCT-017', 100), ('PRODUCT-018', 100), ('PRODUCT-019', 100), ('PRODUCT-020', 100), ('PRODUCT-021', 100), ('PRODUCT-022', 100), ('PRODUCT-023', 100), ('PRODUCT-024', 100), ('PRODUCT-025', 100), ('PRODUCT-026', 100), ('PRODUCT-027', 100), ('PRODUCT-028', 100), ('PRODUCT-029', 100), ('PRODUCT-030', 100), ('PRODUCT-031', 100), ('PRODUCT-032', 100), ('PRODUCT-033', 100), ('PRODUCT-034', 100), ('PRODUCT-035', 100), ('PRODUCT-036', 100), ('PRODUCT-037', 100), ('PRODUCT-038', 100), ('PRODUCT-039', 100), ('PRODUCT-040', 100), ('PRODUCT-041', 100), ('PRODUCT-042', 100), ('PRODUCT-043', 100), ('PRODUCT-044', 100), ('PRODUCT-045', 100), ('PRODUCT-046', 100), ('PRODUCT-047', 100), ('PRODUCT-048', 100), ('PRODUCT-049', 100), ('PRODUCT-050', 100), ('PRODUCT-051', 100), ('PRODUCT-052', 100), ('PRODUCT-053', 100), ('PRODUCT-054', 100), ('PRODUCT-055', 100), ('PRODUCT-056', 100), ('PRODUCT-057', 100), ('PRODUCT-058', 100), ('PRODUCT-059', 100), ('PRODUCT-060', 100), ('PRODUCT-061', 100), ('PRODUCT-062', 100), ('PRODUCT-063', 100), ('PRODUCT-064', 100), ('PRODUCT-065', 100), ('PRODUCT-066', 100), ('PRODUCT-067', 100), ('PRODUCT-068', 100), ('PRODUCT-069', 100), ('PRODUCT-070', 100), ('PRODUCT-071', 100), ('PRODUCT-072', 100), ('PRODUCT-073', 100), ('PRODUCT-074', 100), ('PRODUCT-075', 100), ('PRODUCT-076', 100), ('PRODUCT-077', 100), ('PRODUCT-078', 100), ('PRODUCT-079', 100), ('PRODUCT-080', 100), ('PRODUCT-081', 100), ('PRODUCT-082', 100), ('PRODUCT-083', 100), ('PRODUCT-084', 100), ('PRODUCT-085', 100), ('PRODUCT-086', 100), ('PRODUCT-087', 100), ('PRODUCT-088', 100), ('PRODUCT-089', 100), ('PRODUCT-090', 100), ('PRODUCT-091', 100), ('PRODUCT-092', 100), ('PRODUCT-093', 100), ('PRODUCT-094', 100), ('PRODUCT-095', 100), ('PRODUCT-096', 100), ('PRODUCT-097', 100), ('PRODUCT-098', 100), ('PRODUCT-099', 100), ('PRODUCT-100', 100);" 2>/dev/null
	@echo "🔄 Reset: 100 products, each with stock = 100"

# 데이터 리셋 (N개 상품 - 충돌률 조절용)
# Usage: make reset-products PRODUCTS=20 QUANTITY=20000
reset-products:
	@PRODUCTS=$(or $(PRODUCTS),10); \
	QUANTITY=$(or $(QUANTITY),100); \
	VALUES=$$(for i in $$(seq 1 $$PRODUCTS); do printf "('PRODUCT-%03d', $$QUANTITY)" $$i; [ $$i -lt $$PRODUCTS ] && printf ", "; done); \
	docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "TRUNCATE TABLE stock; INSERT INTO stock (product_id, quantity) VALUES $$VALUES;" 2>/dev/null
	@echo "🔄 Reset: $(or $(PRODUCTS),10) products, each with stock = $(or $(QUANTITY),100)"

# 데이터 리셋 (1,000개 - High Load)
reset-1k:
	@docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "TRUNCATE TABLE stock; INSERT INTO stock (product_id, quantity) VALUES ('PRODUCT-001', 1000);" 2>/dev/null
	@docker compose exec redis redis-cli set stock:1 1000 > /dev/null
	@echo "🔄 Reset: Stock = 1,000"

# 데이터 리셋 (10,000개 - Extreme Load)
reset-10k:
	@docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "TRUNCATE TABLE stock; INSERT INTO stock (product_id, quantity) VALUES ('PRODUCT-001', 10000);" 2>/dev/null
	@docker compose exec redis redis-cli set stock:1 10000 > /dev/null
	@echo "🔄 Reset: Stock = 10,000"

# MySQL 재고 데이터 조회
show-db:
	@docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "SELECT * FROM stock;" 2>/dev/null

# Redis 재고 데이터 조회 (stock:1)
show-redis:
	@docker compose exec redis redis-cli get stock:1

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

# Best Fit Scenario 1: Complex Transaction (Pessimistic)
test-complex-transaction: warmup
	@make reset-products PRODUCTS=5 QUANTITY=10000
	@echo "🚀 Starting Complex Transaction Scenario (Pessimistic)"
	$(K6_CMD) /scripts/1-complex-transaction.js

# 인프라 완전 재시작 (격리된 테스트 환경 보장)
reset-infra:
	@echo "🧹 Cleaning up infrastructure..."
	@docker compose down -v > /dev/null 2>&1
	@echo "🏗️  Starting fresh infrastructure..."
	@docker compose up -d > /dev/null 2>&1
	@echo "⏳ Waiting for DB to be ready..."
	@sleep 10

# Low Contention Test (Scenario 2)
# Usage: make test-low-contention METHOD=optimistic PRODUCTS=50 VUS=300
test-low-contention: reset-infra warmup
	@make reset-products PRODUCTS=$(or $(PRODUCTS),100) QUANTITY=20000
	@echo "🚀 Starting Low Contention Test (METHOD=$(or $(METHOD),optimistic), PRODUCTS=$(or $(PRODUCTS),100), VUS=$(or $(VUS),100))"
	$(K6_CMD) -e METHOD=$(or $(METHOD),optimistic) -e PRODUCT_COUNT=$(or $(PRODUCTS),100) -e VUS=$(or $(VUS),100) /scripts/2-low-contention.js

# Legacy Low Contention Test (Sprint 1-6)
test-low-contention-v1: warmup
	@make reset-100
	@echo "🚀 Starting Legacy Low Contention Test"
	$(K6_CMD) -e METHOD=$(or $(METHOD),optimistic) -e VUS=$(or $(VUS),1000) -e ITERATIONS=$(or $(ITERATIONS),1000) /scripts/2-low-contention.js


