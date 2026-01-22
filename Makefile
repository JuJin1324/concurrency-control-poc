.PHONY: up down ps logs clean mysql redis init

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

# 데이터 리셋 (재고 100개로 복구)
# 테스트 반복 실행을 위해 사용
reset:
	docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "UPDATE stock SET quantity=100 WHERE product_id='PRODUCT-001';"
	@echo "Stock reset to 100 for PRODUCT-001"
