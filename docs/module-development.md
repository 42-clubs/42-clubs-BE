# 기능 구현 가이드

`club`, `activity`, `notice`는 기능별 패키지이며 별도 배포·Gradle 모듈은 아닙니다.
[아키텍처 규칙](architecture.md)을 실제 코드에 적용하는 예시와 점검 항목입니다. 업무 범위는 [현재 MVP](domain-model.md#축소-mvp--2026-10-14-확정)를 먼저 확인합니다.

## 구현 순서

1. [도메인](domain-model.md)에서 해당 기능이 현재 범위인지와 미정 정책을 확인합니다.
2. 소유 기능·협력 기능·권한·함께 반영할 변경·실패 조건을 정합니다.
3. [ERD](erd.md)와 [API](api-endpoints.md)의 필요한 계약을 확정합니다.
4. Controller → Service → 협력 서비스 / Entity / Repository 순서로 구현하고 검증합니다.

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

## 실제 구현: 동아리 회장 권한 검사

`ClubPermissionService.requireManager(actorId, clubId)`는 현재 DB에서 대상 동아리 존재(없으면 404), ACTIVE 상태와 해당 사용자의 ClubManager 관계(없거나 ARCHIVED면 403)를 확인합니다. USER/ADMIN/SUPER_ADMIN 모두 같은 관계 조건을 적용하고 제거된 회장 관계는 다음 요청부터 반영합니다. 다른 기능은 이 서비스를 호출하며 club Repository를 직접 참조하지 않습니다. 부원 명단 권한은 ClubMembershipService, 참가·출결 주최자 권한은 ActivityParticipationService·SessionParticipationService에서 검사합니다.

## 실제 구현: 공지 권한과 조회 필터

- NoticeService는 ClubPermissionService.requireNoticeWriter로 ACTIVE 동아리 회장 또는 운영진의 작성을 허용합니다. 활동 작성의 requireManager는 기존 회장 조건을 유지합니다.
- 공지 조회는 canReadPrivateNotices로 현재 부원·회장·운영 권한을 확인하고, NoticeRepository가 소속·삭제·공개 범위를 WHERE 조건으로 필터링한 뒤 페이지·전체 개수를 계산합니다. 접근 불가 상세는 404입니다.
- AnnouncementService는 user의 requireAdmin으로 작성 권한을 검사하며 공개 조회에 동아리 조건을 두지 않습니다.
- 활동 상세·참가·취소·출결은 ActivityRepository에서 clubId·activityId·미삭제 조건을 함께 조회합니다. 다른 동아리 ID 조합은 실제 데이터 조회·변경 전에 404입니다.

## 실제 구현: 동아리 활동 등록

```text
ActivityController.create → ActivityCreationService.create
  → ClubPermissionService.requireManager, 입력·사진 검증
  → ImageUploadJournal.reserve [독립 트랜잭션] → S3 업로드
  → ActivityWriter.create [트랜잭션]
      → 회장·일정 재검증 → 활동·주최자 참가·사진 저장 → 업로드 저널 제거
  → 실패 시 PhotoCleanup.remove, 남은 저널은 주기적 정리 재시도
```

외부 저장소 호출은 활동 저장 트랜잭션 밖에서 수행합니다. 저널을 업로드 전에 기록하여 업로드 실패·DB 실패·프로세스 중단 후에도 파일 정리를 재시도할 수 있습니다.

협력 기능에는 `requireManager`, `requireExistingUser`처럼 목적이 드러나는 메서드를 요청하며 다른 기능의 Repository를 직접 주입하지 않습니다.

최초 작성자와 지정 주최자의 의미 및 권한은 [도메인 정책](domain-model.md#동아리-활동-주최자--확정)을 따릅니다.
실제 필드·응답·오류 계약은 [API 명세](api-endpoints.md)와 [연동 예시](mvp-api-examples.md)를 따릅니다.

## 권한·호출 방향 점검

- 현재 DB의 사용자 역할·관리 관계와 저장된 리소스 소속을 기준으로 검증합니다. 요청 본문의 userId나 clubId만 신뢰하지 않습니다.
- 동아리 A의 관리자가 B의 활동을 수정하지 못하도록 부모·자식 소속도 검사합니다. 본인 참가의 사용자 ID는 인증 정보에서 가져옵니다.
- activity → club/user 같은 협력 방향을 유지하고 역방향 호출이 생기면 책임을 재검토합니다. 지연 주입만으로 순환을 감추지 않습니다.
- 즉시 결과가 필요한 검증을 비동기 이벤트로 바꾸거나 업무 규칙을 global로 옮기려면 별도의 필요성과 일관성 설계가 있어야 합니다.

## 트랜잭션과 동시 요청

쓰기 유스케이스는 함께 반영되어야 할 상태 변경을 기준으로 Service에 트랜잭션 경계를 둡니다.
예를 들어 모임 생성과 주최자 참여 관계 생성은 한쪽만 저장되지 않도록 처리해야 합니다.
협력 서비스 호출도 기본 전파 설정과 같은 트랜잭션 관리자를 사용할 때 같은 트랜잭션에 참여하는지 확인합니다.
불필요한 독립 트랜잭션으로 부분 커밋이 발생하지 않도록 합니다.

Spring의 프록시 기반 `@Transactional`을 사용할 때 같은 객체 내부의 메서드 호출에는 새 트랜잭션 설정이 적용되지 않을 수 있습니다.
트랜잭션이 필요한 진입점을 명확히 하고, 롤백이 필요한 예외가 실제로 롤백을 유발하는지도 확인합니다.
외부 인증 API 호출처럼 DB 롤백으로 되돌릴 수 없는 작업은 별도의 실패 처리가 필요하며, 긴 네트워크 호출로 DB 트랜잭션을 오래 유지하지 않도록 설계합니다.

**트랜잭션만 선언해도 동시 요청의 정원 초과가 방지되는 것은 아닙니다.**
마지막 자리를 두 요청이 동시에 확인하면 둘 다 저장할 수 있으므로, 정원을 공유하는 활동·모임·회차에 대한 잠금이나 원자적 갱신 전략을 정합니다.
낙관적 잠금을 선택한다면 모든 정원 변경 경로가 같은 버전 갱신에 참여하고 충돌을 처리해야 합니다.
참여 행 삽입만으로 부모의 버전이 자동 증가한다고 가정하지 않습니다.

중복 방지는 서비스 검사와 [ERD의 유니크 제약](erd.md)을 함께 사용합니다.
DB 제약 위반은 API의 적절한 오류로 변환하고, 이미 실패한 트랜잭션 안에서 계속 쓰기를 시도하지 않습니다.
재참가는 [상태 전이 정책](domain-model.md#선착순-참가와-취소)에 따라 기존 관계를 재사용합니다.

## 조회와 응답 흐름

조회 Service에서 필요한 데이터를 가져오고 응답에 필요한 결과를 구성합니다.
API DTO 매핑은 해당 기능 안에서 처리하며, HTTP 상태와 헤더 결정은 Controller에 둡니다.
Controller나 JSON 직렬화 과정이 지연 로딩으로 추가 조회를 수행하는 흐름에 의존하지 않습니다.

목록 API는 페이지네이션과 필요한 필드를 먼저 정한 뒤 쿼리를 설계합니다.
DTO 변환 중 연관관계 접근으로 N+1 쿼리가 발생하는지 확인하고, 필요한 경우 조회 전용 projection이나 적절한 fetch 전략을 사용합니다.
컬렉션 fetch join과 페이지네이션을 함께 사용할 때는 실제 쿼리와 결과 크기를 확인합니다.
공개 응답에 참가자 명단·출결 등 제한된 정보가 섞이지 않도록 응답 DTO를 구분합니다.

## 검증 체크리스트

| 대상 | 확인할 사례 |
| --- | --- |
| 권한 | 타 동아리 접근, 부모·자식 불일치, 권한 해제 직후 요청 거부 |
| 상태·원자성 | 중복 참가, 취소·재참가 관계 재사용, 중간 실패 시 전체 롤백 |
| 동시성 | 마지막 한 자리에 동시 참가해도 정원 초과 없음 |
| 조회 | 페이지 크기 증가 시 불필요한 쿼리 증가 없음, 공개 응답에 제한 정보 없음 |
| DB | 실제 PostgreSQL의 제약·쿼리·잠금·마이그레이션 검증 |

Entity 규칙은 단위 테스트, Service 유스케이스는 협력 객체 테스트, DB·롤백·동시성은 PostgreSQL 통합 테스트로 검증합니다.
최종 검증 명령과 문서 동기화 순서는 [README](../README.md#검증과-포맷)를 따릅니다.
