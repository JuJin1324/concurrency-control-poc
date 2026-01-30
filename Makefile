.PHONY: build up down ps logs clean mysql redis reset reset-1k reset-10k show-db show-redis warmup stats test-capacity test-contention test-stress

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

# Warm-up (시스템 예열)
# Usage: make warmup METHOD=lua-script
warmup:
	@echo "🔥 Warming up (200 iters)..."
	@k6 run -e METHOD=$(or $(METHOD),pessimistic) k6-scripts/warmup.js > /dev/null 2>&1
	@echo "✅ Warm-up Done"

# Capacity Test (처리량 측정)
# Usage: make test-capacity METHOD=lua-script VUS=100 ITERATIONS=1000
test-capacity: warmup
	@echo "🚀 Starting Capacity Test (METHOD=$(or $(METHOD),pessimistic), VUS=$(or $(VUS),100), ITERS=$(or $(ITERATIONS),1000))"
	k6 run -e METHOD=$(or $(METHOD),pessimistic) -e VUS=$(or $(VUS),100) -e ITERATIONS=$(or $(ITERATIONS),1000) k6-scripts/capacity.js

# Contention Test (경합/안정성 측정)
# Usage: make test-contention METHOD=lua-script VUS=5000 DURATION=30s
test-contention: warmup
	@echo "🚀 Starting Contention Test (METHOD=$(or $(METHOD),pessimistic), VUS=$(or $(VUS),1000), DUR=$(or $(DURATION),30s))"
	k6 run -e METHOD=$(or $(METHOD),pessimistic) -e VUS=$(or $(VUS),1000) -e DURATION=$(or $(DURATION),30s) k6-scripts/contention.js

# Stress Test (한계 탐색)
# Usage: make test-stress METHOD=lua-script TARGET_RPS=2000
test-stress: warmup
	@echo "🚀 Starting Stress Test (METHOD=$(or $(METHOD),pessimistic), TARGET_RPS=$(or $(TARGET_RPS),2000))"
	k6 run -e METHOD=$(or $(METHOD),pessimistic) -e TARGET_RPS=$(or $(TARGET_RPS),2000) k6-scripts/stress.js