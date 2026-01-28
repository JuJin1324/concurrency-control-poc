.PHONY: build up down ps logs clean mysql redis reset reset-1k reset-10k show-db show-redis warmup stats

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
	@echo "🔄 Reset: Stock = 1,000 (High Load)"

# 데이터 리셋 (10,000개 - Extreme Load)
reset-10k:
	@docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "TRUNCATE TABLE stock; INSERT INTO stock (product_id, quantity) VALUES ('PRODUCT-001', 10000);" 2>/dev/null
	@docker compose exec redis redis-cli set stock:1 10000 > /dev/null
	@echo "🔄 Reset: Stock = 10,000 (Extreme Load)"

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
	@echo "🔥 Warming up..."
	@k6 run -e METHOD=$(or $(METHOD),pessimistic) --vus 100 --iterations 2000 k6-scripts/hell-test.js > /dev/null 2>&1
	@echo "✅ Warm-up Done"

