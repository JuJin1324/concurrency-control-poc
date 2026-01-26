.PHONY: up down ps logs clean mysql redis reset-db reset-redis show-db show-redis

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
	docker compose logs -f

# 초기화 (컨테이너 및 볼륨 삭제)
clean:
	docker compose down -v

# MySQL 접속
mysql:
	docker compose exec mysql mysql -u app_user -papp_password concurrency_db

# Redis 접속
redis:
	docker compose exec redis redis-cli

# MySQL 데이터 리셋 (테이블 초기화 + 재고 100개)
reset-db:
	@docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "TRUNCATE TABLE stock; INSERT INTO stock (product_id, quantity) VALUES ('PRODUCT-001', 100);" 2>/dev/null
	@echo "MySQL Stock reset: id=1, PRODUCT-001, quantity=100"

# Redis 데이터 리셋 (stock:1 = 100)
reset-redis:
	@docker compose exec redis redis-cli set stock:1 100 > /dev/null
	@echo "Redis Stock reset: stock:1 = 100"

# MySQL 재고 데이터 조회
show-db:
	@docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "SELECT * FROM stock;" 2>/dev/null

# Redis 재고 데이터 조회 (stock:1)
show-redis:
	@docker compose exec redis redis-cli get stock:1

