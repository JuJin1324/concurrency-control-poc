.PHONY: build up down ps logs clean mysql redis reset show-db show-redis stats

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

# 데이터 전체 리셋 (MySQL + Redis)
reset:
	@docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "TRUNCATE TABLE stock; INSERT INTO stock (product_id, quantity) VALUES ('PRODUCT-001', 100);" 2>/dev/null
	@docker compose exec redis redis-cli set stock:1 100 > /dev/null
	@echo "All environments reset: PRODUCT-001 Stock = 100 (MySQL & Redis)"

# MySQL 재고 데이터 조회
show-db:
	@docker compose exec mysql mysql -u app_user -papp_password concurrency_db -e "SELECT * FROM stock;" 2>/dev/null

# Redis 재고 데이터 조회 (stock:1)
show-redis:
	@docker compose exec redis redis-cli get stock:1