# Sprint 0 - Iteration 2: Spring Boot 스캐폴딩 작업 리포트

**작성일:** 2026-01-22
**작업자:** Gemini Agent
**목표:** 애플리케이션의 기본 뼈대(Scaffolding)를 구축하고 아키텍처 규칙을 설정한다.

---

## 0. 트러블슈팅 및 버전 이슈 (Spring Boot 4.0)

프로젝트 생성 과정에서 Spring Boot 버전과 관련된 중요한 발견이 있었습니다.

1.  **초기 시도 실패:** `bootVersion=3.2.2`로 프로젝트 생성을 시도했으나, Spring Initializr에서 "지원되지 않는 버전(Too old)" 오류(`400 Bad Request`)가 발생했습니다.
2.  **원인 파악:** 현재 시점(2026년 1월)에서 Spring Boot 3.2.x는 이미 지원이 종료되었거나 호환성 범위를 벗어난 상태였습니다.
3.  **해결 및 발견:** 버전을 명시하지 않고 **최신 안정 버전(Default)**을 요청한 결과, **Spring Boot 4.0.1** 버전으로 프로젝트가 생성되었습니다.
4.  **결론:** 제 학습 데이터와 달리 현실 세계에는 이미 **Spring Boot 4**가 주류로 자리 잡았습니다. 이에 따라 프로젝트는 최신 기술 스택인 **Spring Boot 4.0.1 + Java 21**을 기반으로 구축되었습니다.

## 0.1 의존성 명칭 변경 확인 (starter-web -> starter-webmvc)

Spring Boot 4.0으로 프로젝트를 생성하면서 의존성 구조에 변화가 있음을 확인했습니다.

- **발견:** `spring-boot-starter-web` 대신 `spring-boot-starter-webmvc`가 의존성에 포함되었습니다.
- **원인:** Spring Boot 4.0의 **모듈화 전략**에 따라, 범용적인 `starter-web`은 Deprecated 되고 서블릿 기반의 웹 개발을 명시하는 `starter-webmvc`로 대체되었습니다.
- **의미:** 프레임워크가 더 가볍고 명확한 구조를 지향하며, 개발자가 사용하는 기술을 명확히 정의하도록 유도하고 있습니다. (예: WebMVC vs WebFlux)

---

## 1. 프로젝트 생성 정보

Spring Initializr를 통해 기본 프로젝트를 생성했습니다.

- **기반 기술:**
    - **Language:** Java 21 (LTS)
    - **Framework:** Spring Boot (Latest Stable)
    - **Build Tool:** Gradle (Groovy DSL)
- **메타데이터:**
    - **Group:** `com.concurrency`
    - **Artifact:** `concurrency-control-poc`
    - **Package:** `com.concurrency.poc`
- **핵심 의존성 (Dependencies):**
    - `spring-boot-starter-web`: REST API 구축
    - `spring-boot-starter-data-jpa`: DB 접근 (MySQL)
    - `spring-boot-starter-data-redis`: Redis 접근
    - `mysql-connector-j`: MySQL 드라이버
    - `lombok`: 보일러플레이트 코드 감소
    - `spring-boot-starter-validation`: 입력값 검증
    - `spring-boot-starter-actuator`: 헬스체크 및 모니터링

---

## 2. 디렉토리 및 패키지 구조

**Layered Architecture**에 따라 패키지를 구조화했습니다.
ArchUnit 테스트 통과를 위해 각 패키지에 `Placeholder` 클래스를 임시로 생성했습니다.

```
src/main/java/com/concurrency/poc/
├── controller/       # Presentation Layer
│   └── ControllerPlaceholder.java
├── service/          # Application Layer
│   └── ServicePlaceholder.java
├── domain/           # Domain Layer
│   └── DomainPlaceholder.java
└── repository/       # Infrastructure Layer
    └── RepositoryPlaceholder.java
```

---

## 3. 설정 파일 (`src/main/resources/application.yml`)

`application.properties`를 `application.yml`로 변경하고, **로컬 개발 환경**과 **Docker Compose 환경**을 모두 지원하도록 환경 변수 처리를 적용했습니다.

```yaml
spring:
  datasource:
    # 기본값은 로컬용(localhost), 환경변수 주입 시 Docker용(mysql)
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:13306/concurrency_db}
    username: ${SPRING_DATASOURCE_USERNAME:app_user}
    password: ${SPRING_DATASOURCE_PASSWORD:app_password}
  data:
    redis:
      # 기본값은 로컬용(localhost), 환경변수 주입 시 Docker용(redis)
      host: ${SPRING_REDIS_HOST:localhost}
      port: ${SPRING_REDIS_PORT:16379}
  jpa:
    hibernate:
      ddl-auto: validate # 스키마는 init.sql로 관리하므로 validate 사용
```

---

## 4. 아키텍처 규칙 (ArchUnit)

### 규칙 정의
`ADR-005`에 따라 계층 간 의존성 규칙을 코드로 강제했습니다.

1.  **Controller:** 누구에게도 의존되지 않음.
2.  **Service:** Controller에 의해서만 호출됨.
3.  **Repository:** Service에 의해서만 호출됨.
4.  **Domain:** **Service와 Repository 계층에서만 접근 가능** (Controller 접근 금지).
    - *Note: Controller는 Service와 통신 시 반드시 DTO를 사용해야 함.*

### 테스트 코드
`src/test/java/com/concurrency/poc/architecture/LayeredArchitectureTest.java`

```java
// 핵심 로직
layeredArchitecture()
    .consideringOnlyDependenciesInAnyPackage("com.concurrency.poc..")
    .layer("Controller").definedBy("..controller..")
    .layer("Service").definedBy("..service..")
    .layer("Repository").definedBy("..repository..")
    .layer("Domain").definedBy("..domain..")
    
    // ...
    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Repository", "Service");
```

**실행 결과:** `./gradlew test` -> **BUILD SUCCESSFUL**

---

## 5. 생성된 문서

1.  **`docs/adr/ADR-005-why-layered-architecture.md`**
    - 헥사고날 아키텍처 대신 Layered Architecture를 선택한 이유(단순성, PoC 목적)를 기록했습니다.

2.  **`docs/architecture/application.md`**
    - 애플리케이션 아키텍처와 패키지 구조, 의존성 규칙을 시각화(Mermaid)하여 정리했습니다.

3.  **`how-diagram.md`**
    - 프로젝트 진행 상태를 업데이트했습니다. (인프라 완료, 스캐폴딩 진행 중)

---

## 6. 다음 단계 (Next Steps)

이제 **Sprint 0의 Iteration 3 (전체 시스템 시각화)** 만 남았습니다.
하지만 이미 많은 다이어그램(`infrastructure.md`, `application.md`)이 작성되었으므로, **Sequence Diagram 4종**만 추가하면 Sprint 0가 완료됩니다.

그 후 **Sprint 1 (DB Lock 구현)** 으로 넘어가 실제 `Stock` 도메인 코드를 작성하게 됩니다.
