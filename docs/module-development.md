# 기능 모듈 구현 가이드

다른 개발자가 기능의 진입점, 호출 방향, 상태 변경과 저장 위치를 따라갈 수 있도록 코드 흐름을 구성하는 가이드입니다.
여기서 모듈은 `club`, `activity`, `notice` 같은 기능별 패키지를 뜻합니다. 독립적인 Gradle 모듈이나 배포 단위는 아닙니다.
현재는 DB 기반·User·Club 엔티티, 동아리 공개 조회, 42 OAuth 로그인·내 정보 조회를 구현했습니다.
아래 동아리 조회 흐름은 실제 코드이며, 활동 등록 클래스·메서드 이름과 코드 흐름은 구현 예시입니다.

패키지 책임과 의존성 규칙의 기준은 [아키텍처 문서](architecture.md)입니다.
이 문서는 해당 규칙을 구현에 적용하는 방법과 리뷰 질문을 설명합니다.
업무 정책은 [도메인 문서](domain-model.md), DB 제약은 [ERD 명세](erd.md), 요청별 권한은 [API 초안](api-endpoints.md)을 확인합니다.

## 이 구조를 사용하는 배경

현재의 1인 개발과 MVP 범위에서는 단일 애플리케이션으로 실행·배포 부담을 줄이면서 기능별 책임을 구분하는 구조가 적합합니다.
기능별 패키지는 관련 코드와 변경 범위를 모으고, 내부 계층은 HTTP 처리·유스케이스·상태 변경·영속성 접근을 구분합니다.
연결된 기능을 같은 DB 트랜잭션에서 처리할 수 있지만, 기능별 독립 배포나 장애 격리를 제공하지는 않습니다.
단일 Gradle 모듈은 패키지 간 접근을 강제하지 않으므로 코드와 리뷰에서 경계를 유지해야 합니다.

## 구현 전에 호출 흐름 정하기

기능 작업을 시작할 때 다음 항목을 정하고, 리뷰에 필요한 내용은 PR에 기록합니다.

1. 진입점: 어떤 API 요청이 어떤 유스케이스를 실행하는가?
2. 소유 기능: 변경하는 데이터와 정책은 어느 기능의 책임인가?
3. 협력 기능: 다른 기능에서 어떤 검증 또는 조회 결과가 필요한가?
4. 변경 단위: 성공하거나 실패할 때 함께 반영·취소되어야 하는 변경은 무엇인가?
5. 실패 조건: 권한 부족, 잘못된 상태, 중복, 동시 요청을 어떻게 처리하는가?

클래스를 만들기 전에 `Controller → Service → 협력 서비스 / Entity / Repository` 형태로 흐름을 적으면
다른 기능의 내부에 접근하거나 서비스가 서로 호출하는 구조를 발견하기 쉽습니다.

## 실제 구현: 동아리 공개 조회

```text
GET /api/v1/clubs
  → ClubController.findClubs: 필터 변환, 페이지·크기 검증
  → ClubQueryService.findClubs [읽기 전용 트랜잭션]
      → ClubRepository: 운영 상태 조건과 ID 정렬로 페이지 조회
      → Club.recruitmentStatusOn: 조회 기준일의 모집 상태 계산
      → ClubSummaryResponse → ClubListResponse
  → 200 JSON

GET /api/v1/clubs/{clubId}
  → ClubController.findClub: ID 형식·범위 검증
  → ClubQueryService.findClub [읽기 전용 트랜잭션]
      → ClubRepository.findById
      → ClubDetailResponse: Entity의 모집 상태 계산 결과와 공개 필드 매핑
  → 200 JSON
```

`Clock`은 기술 설정인 `global/config/TimeConfig`에서 제공하며, Service가 요청의 날짜를 구해 Entity에 전달합니다.
Entity는 전달받은 날짜로 정책을 판단하므로 시스템 시각이나 Spring Bean을 직접 조회하지 않습니다.
DTO 매핑은 트랜잭션 안에서 끝내고, Controller에는 엔티티를 반환하지 않습니다.
목록은 현재 연관관계 없이 clubs만 조회하므로 다른 기능의 Repository에 접근하지 않습니다.

없는 동아리는 Service가 `ResourceNotFoundException`으로 알리고 `global/exception/GlobalExceptionHandler`가 404로 변환합니다.
입력 변환·범위 검증 오류는 같은 처리기가 400으로 변환합니다.
HTTP 계약은 [동아리 조회 명세](api-endpoints.md#동아리-공개-조회--구현-계약)를 따릅니다.

## 실제 구현: 42 로그인

```text
GET /oauth2/authorization/42
  → Spring Security: 인가 요청·state를 서버 세션에 저장, 42로 이동
GET /login/oauth2/code/42
  → Spring Security: state 검증·인가 코드 교환
  → FortyTwoOAuth2UserService: 42 프로필 조회·경산 이메일 도메인 검증
      → UserAccountService.synchronize42Profile [트랜잭션]
          → UserRepository: intra_id 기준 원자적 INSERT / ON CONFLICT UPDATE
          → UserIdentity: 내부 ID·기존 역할 반환
      → AuthenticatedUser: 내부 ID·역할만 포함
  → Spring Security: 세션 ID 교체·인증 저장·CSRF 초기화
  → AUTH_SUCCESS_URL로 이동
GET /api/v1/me
  → CurrentUserFilter: UserAccountService로 현재 DB 역할 확인
  → MeController → UserAccountService.findMe → MeResponse
```

인증은 user의 서비스와 DTO를 통해 협력하고 user의 Repository·엔티티를 직접 참조하지 않습니다. `global/security`의 인증 주체는 기능별 서비스에 의존하지 않습니다. 사용자 서비스를 참조하는 보안 설정·필터는 auth에 둡니다. 기존 계정 역할은 OAuth 프로필이 덮어쓰지 않으며, 로그인 후 변경된 역할도 다음 요청에서 반영합니다.

OAuth 인가 요청은 Spring Security의 기본 서버 세션 저장소를 사용합니다. 설정 근거는 [공식 OAuth 인가 요청 저장소 문서](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html)를 참고합니다.

## 예시: 동아리 활동 등록

```text
ActivityController.create(request, 로그인 사용자)
  → ActivityService.create(actorId, clubId, command) [트랜잭션 시작]
      → ClubPermissionService.requireManager(actorId, clubId)
          → club 내부 Repository로 관리 권한 확인
      → UserQueryService.requireExistingUser(organizerId)
          → user 내부 Repository로 지정 주최자 확인
      → Activity 생성: 최초 작성자·주최자·소속 동아리와 입력 값 설정
      → ActivityRepository.save(activity)
      → 응답에 필요한 결과 구성
    [트랜잭션 커밋]
  → Controller에서 HTTP 응답 생성
```

Controller에서는 요청 DTO의 형식과 필수 값을 검증하고, 인증 정보에서 얻은 사용자 식별자를 전달합니다.
Service는 유스케이스의 순서를 읽을 수 있게 구성합니다. 세부 정책은 해당 정책을 소유한 서비스나 Entity에 맡깁니다.
Entity는 자신의 값과 상태로 판단할 수 있는 변경 규칙을 처리하고, 다른 서비스를 호출하거나 HTTP·인증 정보를 조회하지 않습니다.

`ActivityService`가 `ClubManagerRepository`나 `UserRepository`를 직접 주입받으면 기능 경계를 건너뜁니다.
협력 기능에는 `requireManager`, `requireExistingUser`처럼 목적이 드러나는 메서드를 요청합니다.
전달 값은 ID나 필요한 조회 결과로 좁히고, 내부 Entity나 API 요청 DTO를 다른 기능의 공용 계약으로 사용하지 않도록 합니다.
JPA 연관관계의 구체적인 매핑은 별도 설계가 필요하며, 연관관계를 통해 다른 기능의 상태를 직접 변경하지 않습니다.

최초 작성자와 지정 주최자의 의미 및 권한은 [도메인 정책](domain-model.md#동아리-활동-주최자--확정)을 따릅니다.
실제 API 계약은 DTO 설계 시 확정합니다.

## 기능 사이의 호출 방향 관리

호출 방향을 정할 때는 어떤 기능이 유스케이스를 시작하고 어떤 기능이 정책·데이터를 제공하는지 구분합니다.
위 예시에서는 `activity → club`, `activity → user`로 협력합니다.
`club → activity` 호출도 필요해지면 기존 흐름과 함께 살펴보고 순환이 생기는지 확인합니다.

순환이 생겼을 때 지연 주입으로 연결만 해결하지 말고, 다음을 검토합니다.

- 각 서비스가 자기 기능의 책임만 수행하는가?
- 호출 대신 필요한 ID나 조회 결과를 전달할 수 있는가?
- 여러 기능의 변경을 조정할 유스케이스의 소유 위치를 다시 정해야 하는가?

새 조정 계층이나 이벤트는 필요성이 확인된 경우에 도입합니다.
즉시 성공 여부를 알아야 하는 검증을 비동기 이벤트로 바꾸면 실패 처리와 일관성도 달라지므로 별도 설계가 필요합니다.
공통 사용 여부만으로 업무 규칙을 `global`로 옮기지 않습니다. 동아리 권한 검증은 `club`이 소유합니다.

## 권한과 리소스 관계 확인

보안 인프라가 전달한 사용자 정보와 Service에서 조회한 대상 리소스를 기준으로 권한을 판단합니다.
본인 신청처럼 요청자가 본인인 기능에서는 요청 본문의 임의 사용자 ID를 본인 식별자로 사용하지 않습니다.
주최자가 다른 참가자를 지정하는 API 등은 해당 API 계약에 따라 대상 ID를 받되 요청자의 권한을 별도로 검증합니다.

중첩 경로에 부모 ID와 자식 ID가 함께 있으면 부모 권한 확인만으로 끝내지 않습니다.
예를 들어 동아리 A의 권한으로 동아리 B의 활동을 수정하지 못하도록, 조회 조건 또는 명시적인 검사로 소속 관계를 확인합니다.
기존 리소스 수정은 저장된 소속·주최자 정보로 검증하고, 요청 본문의 값만으로 판단하지 않습니다.

역할, 현재 동아리 관리 관계, 최초 작성자, 활동 주최자는 서로 다른 정보입니다.
각 API에서 필요한 검증은 [API 권한 초안](api-endpoints.md)과 [도메인 권한 정책](domain-model.md)을 기준으로 정합니다.

## 트랜잭션과 동시 요청

쓰기 유스케이스는 함께 반영되어야 할 상태 변경을 기준으로 Service에 트랜잭션 경계를 둡니다.
예를 들어 모임 생성과 주최자 참여 관계 생성은 한쪽만 저장되지 않도록 처리해야 합니다.
협력 서비스 호출도 기본 전파 설정과 같은 트랜잭션 관리자를 사용할 때 같은 트랜잭션에 참여하는지 확인합니다.
불필요한 독립 트랜잭션으로 부분 커밋이 발생하지 않도록 합니다.

Spring의 프록시 기반 `@Transactional`을 사용할 때 같은 객체 내부의 메서드 호출에는 새 트랜잭션 설정이 적용되지 않을 수 있습니다.
트랜잭션이 필요한 진입점을 명확히 하고, 롤백이 필요한 예외가 실제로 롤백을 유발하는지도 확인합니다.
외부 인증 API 호출처럼 DB 롤백으로 되돌릴 수 없는 작업은 별도의 실패 처리가 필요하며, 긴 네트워크 호출로 DB 트랜잭션을 오래 유지하지 않도록 설계합니다.

**트랜잭션만 선언해도 동시 요청의 정원 초과가 방지되는 것은 아닙니다.**
마지막 자리를 두 요청이 동시에 확인하면 둘 다 저장할 수 있으므로, 정원을 공유하는 모임·회차에 대한 잠금이나 원자적 갱신 전략을 정합니다.
낙관적 잠금을 선택한다면 모든 정원 변경 경로가 같은 버전 갱신에 참여하고 충돌을 처리해야 합니다.
참여 행 삽입만으로 부모의 버전이 자동 증가한다고 가정하지 않습니다.

중복 방지는 서비스 검사와 [ERD의 유니크 제약](erd.md)을 함께 사용합니다.
DB 제약 위반은 API의 적절한 오류로 변환하고, 이미 실패한 트랜잭션 안에서 계속 쓰기를 시도하지 않습니다.
재신청은 [상태 전이 정책](domain-model.md#신청-상태와-재신청)에 따라 기존 관계를 재사용합니다.

## 조회와 응답 흐름

조회 Service에서 필요한 데이터를 가져오고 응답에 필요한 결과를 구성합니다.
API DTO 매핑은 해당 기능 안에서 처리하며, HTTP 상태와 헤더 결정은 Controller에 둡니다.
Controller나 JSON 직렬화 과정이 지연 로딩으로 추가 조회를 수행하는 흐름에 의존하지 않습니다.

목록 API는 페이지네이션과 필요한 필드를 먼저 정한 뒤 쿼리를 설계합니다.
DTO 변환 중 연관관계 접근으로 N+1 쿼리가 발생하는지 확인하고, 필요한 경우 조회 전용 projection이나 적절한 fetch 전략을 사용합니다.
컬렉션 fetch join과 페이지네이션을 함께 사용할 때는 실제 쿼리와 결과 크기를 확인합니다.
공개 응답에 참가자 명단·출결 등 제한된 정보가 섞이지 않도록 응답 DTO를 구분합니다.

## 테스트와 리뷰

아래 질문으로 코드의 흐름과 실패 처리를 확인합니다.

| 검토 항목 | 확인 질문 / 검증 사례 |
| --- | --- |
| 진입점과 책임 | Controller에서 업무 규칙이나 DB 접근을 처리하지 않는가? Service에서 실행 순서를 읽을 수 있는가? |
| 기능 경계 | 다른 기능의 Repository를 주입하지 않는가? 협력 메서드의 목적과 입출력이 분명한가? |
| 호출 방향 | 새 의존성을 추가하면 기존 호출과 순환이 생기지 않는가? |
| 권한 | 타 동아리 수정, 잘못된 부모·자식 조합, 권한 해제 후 수정이 거부되는가? |
| 상태 변경 | 허용되지 않는 상태 전이가 거부되고 재신청이 기존 관계를 재사용하는가? |
| 원자성 | 중간 실패 시 함께 변경한 데이터가 모두 롤백되는가? |
| 동시성 | 정원의 마지막 자리에 동시 신청·승인해도 초과 확정되지 않는가? |
| DB와 조회 | 실제 PostgreSQL에서 제약·쿼리가 검증되는가? 목록 크기에 따라 쿼리 수가 불필요하게 늘지 않는가? |
| 응답 | Entity가 직접 노출되지 않고 공개 응답에 제한된 정보가 섞이지 않는가? |

Entity의 상태 규칙은 단위 테스트로, Service의 유스케이스와 권한 분기는 협력 객체를 이용한 테스트로 확인합니다.
롤백·DB 제약·잠금·동시성은 실제 PostgreSQL 통합 테스트로 확인해야 하며 Mockito 테스트만으로 보장하지 않습니다.
테스트 및 DB 운영 원칙은 [아키텍처 문서](architecture.md#구현-및-검증-방향)를 따릅니다.

PR에는 변경한 유스케이스, 새 기능 간 의존성, 트랜잭션·동시성 처리와 검증 결과를 필요한 만큼 기록합니다.
업무상 미정인 부분은 구현자가 임의로 확정하지 않고 [도메인 문서의 미정 사항](domain-model.md#남은-결정-사항)을 확인해 정책과 구현을 함께 정리합니다.
문서와 구현 변경 순서 및 빌드 검사는 [README의 문서 관리 규칙](../README.md#문서-관리-ssot)을 따릅니다.
