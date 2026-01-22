# ADR-005: 왜 Layered Architecture를 선택했는가?

**날짜:** 2026-01-22
**상태:** 승인됨 (Accepted)

## 배경 (Context)

본 프로젝트는 동시성 제어 기술을 검증하기 위한 PoC(Proof of Concept)입니다.
도메인 로직은 "재고 차감" 하나로 매우 단순하지만, 이를 처리하는 기술적 방법론(DB Lock, Redis Lock 등)은 다양합니다.

우리는 다음과 같은 아키텍처 옵션을 고려했습니다:
1.  **Layered Architecture (계층형 아키텍처):** 전통적인 Controller -> Service -> Repository 구조.
2.  **Hexagonal Architecture (Ports and Adapters):** 도메인을 외부 의존성으로부터 철저히 격리.
3.  **Clean Architecture:** 헥사고날과 유사하지만 더 엄격한 규칙.

## 결정 (Decision)

우리는 **Layered Architecture (Strict)** 를 선택합니다.

구체적인 구조는 다음과 같습니다:
- **Presentation Layer:** `controller` (Web 요청 처리)
- **Application Layer:** `service` (비즈니스 흐름 제어, 트랜잭션 관리)
- **Domain Layer:** `domain` (비즈니스 엔티티 및 로직)
- **Infrastructure Layer:** `repository` (DB 접근)

특히 **Presentation Layer(Controller)는 Domain Layer에 직접 접근할 수 없으며, 반드시 Service Layer가 제공하는 DTO(Data Transfer Object)를 통해 통신**해야 합니다.

## 근거 (Rationale)

1.  **관심사의 분리 (Separation of Concerns):** API 스펙(Controller)과 내부 비즈니스 모델(Domain)을 분리하여, 도메인 변경이 API에 영향을 주거나 API 변경이 도메인을 오염시키는 것을 방지합니다.
2.  **유지보수성 강화:** PoC 단계일지라도 도메인 로직의 순수성을 지키는 것이 향후 기능 확장이나 리팩토링 시 유리합니다.
3.  **명확한 경계:** ArchUnit을 통해 엄격한 경계를 설정함으로써, 개발 과정에서 발생할 수 있는 우발적인 의존성 위반을 방지합니다.

## 대안 비교 (Alternatives)

| 아키텍처 | 장점 | 단점 | 평가 |
|----------|------|------|------|
| **Strict Layered** | 도메인 격리, 유지보수성 높음 | DTO 매핑 코드 증가 | **채택 (권장)** |
| **Relaxed Layered** | 개발 속도 빠름, 코드량 적음 | 도메인 누수 위험 | 기각 (불안정) |
| **Hexagonal** | 도메인 완전 격리, 유연성 | 구현 복잡도 높음 | 기각 (과함) |

## 결과 (Consequences)

- **긍정적:** 도메인 모델이 외부 변경으로부터 보호됩니다. ArchUnit 테스트를 통해 아키텍처 규칙이 지속적으로 검증됩니다.
- **부정적:** Entity <-> DTO 변환 코드가 추가되어 개발량이 다소 증가합니다. (Java 17+ Record 활용으로 완화 가능).
