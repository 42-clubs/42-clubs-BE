# MVP ERD — 확정 기준

[도메인 관계 및 업무 규칙](domain-model.md)을 바탕으로 MVP는 아래 10개 테이블로 구성합니다.
변경 순서는 [README의 SSOT 규칙](../README.md#문서-관리-ssot)을 따릅니다.
이 문서는 구현 기준입니다. 현재 `users`는 V1, `clubs`는 V2 마이그레이션과 해당 엔티티로 구현했으며,
나머지 테이블은 기능 구현 순서에 따라 추가합니다.

## 관계도

전체 관계도는 [README의 ERD](../README.md#erd)에서 관리합니다. 컬럼·타입·키·인덱스의 기준은 아래 명세입니다.

## 공통 규칙

- PK는 자동 생성 BIGINT를 사용합니다.
- 필수 관계 FK는 NOT NULL이며 내부 PK를 참조합니다. 아직 체크하지 않은 출결의 attendance_checked_by는 NULL을 허용합니다.
- 생성·수정 시각은 TIMESTAMPTZ, 실제 활동일과 모집 일정은 DATE를 사용합니다.
- 역할과 운영 상태는 VARCHAR로 저장하고 CHECK 제약조건으로 허용값을 제한합니다.
- FK에 연쇄 삭제를 설정하지 않습니다. 동아리는 삭제 대신 아카이브합니다.
- 아래 표의 필수 값은 NOT NULL입니다. 문자열의 공백 여부 등 입력 검증은 별도로 적용합니다.
- updated_at은 애플리케이션에서 갱신합니다. 권한 관계는 추가·제거로 관리하므로 club_managers에는 updated_at을 두지 않습니다.

## users

| 컬럼 | 타입 | 필수 | 제약 및 의미 |
| --- | --- | --- | --- |
| id | BIGINT | O | PK |
| intra_id | BIGINT | O | UNIQUE, 42 사용자 고유 ID |
| intra_login | VARCHAR(100) | O | Intra 로그인명, 계정 연결 기준으로 사용하지 않음 |
| display_name | VARCHAR(100) | O | 표시 이름 |
| role | VARCHAR(20) | O | USER / ADMIN, 기본값 USER |
| created_at | TIMESTAMPTZ | O | 계정 생성 시각 |
| updated_at | TIMESTAMPTZ | O | 수정 시각 |

블랙홀 확인에 따른 사용자 정보 삭제 방향과 후속 설계는 [이용 자격 정책](domain-model.md#사용자-이용-자격-및-정보-정리)을 따릅니다. 현재 명세의 필수 사용자 FK와 연쇄 삭제 없음은 유지하며, 삭제 범위·참조 처리 확정 후 스키마를 동기화합니다.
계정 생성·권한 부여 정책은 [도메인 문서](domain-model.md#사용자-역할과-동아리-관리-권한)를 참조합니다.

## clubs

| 컬럼 | 타입 | 필수 | 제약 및 의미 |
| --- | --- | --- | --- |
| id | BIGINT | O | PK |
| name | VARCHAR(100) | O | 동아리 이름, 현재 UNIQUE 없음 |
| summary | VARCHAR(200) | O | 목록용 짧은 소개 |
| description | TEXT | O | 상세 소개 |
| operation_status | VARCHAR(20) | O | ACTIVE / ARCHIVED |
| recruitment_planned | BOOLEAN | O | 모집 계획 여부, 기본값 false |
| recruitment_start_date | DATE | 조건부 | 모집 계획이 있으면 필수 |
| recruitment_end_date | DATE | X | 없으면 시작일부터 상시 모집 |
| recruitment_closed_early | BOOLEAN | O | 수동·운영 상태 변경에 따른 마감, 기본값 false |
| application_method | TEXT | 조건부 | 모집 계획이 있으면 지원 방법 필수 |
| application_url | VARCHAR(2048) | X | 외부 지원 링크 |
| created_at | TIMESTAMPTZ | O | 생성 시각 |
| updated_at | TIMESTAMPTZ | O | 수정 시각 |

### 모집 정보의 데이터 제약

- 모집 계획이 있으면 시작일과 지원 방법이 필수입니다.
- 종료일이 있으면 시작일도 존재하고 종료일 >= 시작일이어야 합니다.
- recruitment_closed_early가 true이면 recruitment_planned도 true여야 합니다.
- [모집 계획 취소](domain-model.md#모집-계획-취소--확정) 시 recruitment_planned와 recruitment_closed_early는 false, recruitment_start_date·recruitment_end_date·application_method·application_url은 NULL로 저장합니다. 이 초기화는 후속 모집 쓰기 유스케이스에서 처리하며, 현재 DB CHECK는 이 NULL 조합 전체를 강제하지 않습니다.

상태 변경과 아카이브·재모집 동작은 [도메인 정책](domain-model.md#게시-및-아카이브-정책--확정)을 따릅니다.
활동도·표시용 모집 상태는 파생 값이며 저장 컬럼을 두지 않습니다. 활동도의 날짜 경계와 수정 반영은 [집계 정책](domain-model.md#활동도-계산)을 따릅니다. 별도 아카이브 테이블이나 archived_at 컬럼은 없습니다.

## club_managers

| 컬럼 | 타입 | 필수 | 제약 및 의미 |
| --- | --- | --- | --- |
| id | BIGINT | O | PK |
| user_id | BIGINT | O | FK → users.id |
| club_id | BIGINT | O | FK → clubs.id |
| created_at | TIMESTAMPTZ | O | 권한 부여 시각 |

- UNIQUE(user_id, club_id)로 관리 권한 중복을 방지합니다.
- 동아리별 관리자는 0명 이상이며, 사용자도 여러 동아리를 관리할 수 있습니다.
- 별도 관리자 등급이나 동아리 회원 관계를 표현하지 않습니다.

## activities

| 컬럼 | 타입 | 필수 | 제약 및 의미 |
| --- | --- | --- | --- |
| id | BIGINT | O | PK |
| club_id | BIGINT | O | FK → clubs.id |
| author_id | BIGINT | O | FK → users.id, 최초 작성자 |
| organizer_id | BIGINT | O | FK → users.id, 지정된 활동 주최자 |
| title | VARCHAR(200) | O | 제목 |
| content | TEXT | O | 활동 내용 |
| activity_date | DATE | O | 실제 활동일, 활동도 계산 기준 |
| created_at | TIMESTAMPTZ | O | 등록 시각 |
| updated_at | TIMESTAMPTZ | O | 수정 시각 |
| deleted_at | TIMESTAMPTZ | X | NULL이면 미삭제, 값이 있으면 논리 삭제 시각 |

작성자와 주최자는 같아도 됩니다. 동아리 관리자가 주최자를 지정하며, 일반 사용자도 지정할 수 있습니다.
활동 본문 관리 권한과 주최자의 참가자·출결 관리 권한을 분리합니다.
활동 생성 시 주최자의 activity_participants 행도 같은 트랜잭션에서 생성하며 초기 출결 값은 아래 참가자 테이블의 기본값을 따릅니다. 업무 규칙은 [주최자 정책](domain-model.md#동아리-활동-주최자--확정)을 참조합니다.
주최자 변경 시 organizer_id 갱신과 필요한 activity_participants 행 생성을 같은 트랜잭션에서 처리합니다. 기존 참가 행·출결 필드와 author_id의 보존은 [변경 정책](domain-model.md#활동-주최자-변경--확정)을 따릅니다.

## notices

| 컬럼 | 타입 | 필수 | 제약 및 의미 |
| --- | --- | --- | --- |
| id | BIGINT | O | PK |
| club_id | BIGINT | O | FK → clubs.id |
| author_id | BIGINT | O | FK → users.id, 최초 작성자 |
| title | VARCHAR(200) | O | 제목 |
| content | TEXT | O | 공지 내용 |
| created_at | TIMESTAMPTZ | O | 등록 시각 |
| updated_at | TIMESTAMPTZ | O | 수정 시각 |
| deleted_at | TIMESTAMPTZ | X | NULL이면 미삭제, 값이 있으면 논리 삭제 시각 |

활동·공지는 등록 즉시 공개하며 임시저장 상태 컬럼을 두지 않습니다.
수정·삭제 권한은 현재 club_managers 관계로 검사하며 최초 작성자는 변경하지 않습니다.
활동·공지의 deleted_at은 향후 해당 테이블 최초 마이그레이션에 포함합니다. 현재 두 테이블은 미구현입니다. 참가·출결 행 보존과 조회 제한은 [삭제 정책](domain-model.md#활동공지-삭제와-기록-보존--확정)을 따릅니다.

## gatherings — 모임 단위

| 컬럼 | 타입 | 필수 | 의미 |
| --- | --- | --- | --- |
| id | BIGINT | O | PK |
| organizer_id | BIGINT | O | FK → users.id, 생성자·주최자 |
| schedule_type | VARCHAR(20) | O | ONE_TIME / FIXED_TERM / RECURRING |
| participation_mode | VARCHAR(20) | O | PER_SESSION / FIXED_MEMBERS |
| join_policy | VARCHAR(20) | O | FIRST_COME / APPROVAL |
| capacity | INTEGER | O | 고정 멤버 정원 또는 새 회차의 기본 정원, 주최자 포함, 1 이상 |

제목·소개·목적 분류·운영 기간·반복 규칙·전체 취소·고정 멤버 모집 기간의 컬럼은 상세 설계 대상입니다.
목적 분류와 진행 방식은 분리하며 기간제에도 반복 규칙을 적용할 수 있습니다.
반복 규칙은 실제 회차를 생성하기 위한 설정입니다. 이미 생성한 회차를 규칙 변경만으로 일괄 변경하지 않습니다.
참여 단위·신청 방식 변경은 일반 사용자 신청 이력이 생기기 전으로 제한하는 방향입니다.

## gathering_participants — 고정 멤버 가입

| 컬럼 | 타입 | 필수 | 의미 |
| --- | --- | --- | --- |
| id | BIGINT | O | PK |
| gathering_id | BIGINT | O | FK → gatherings.id |
| user_id | BIGINT | O | FK → users.id |
| status | VARCHAR(20) | O | PENDING / CONFIRMED / REJECTED / CANCELLED |
| created_at | TIMESTAMPTZ | O | 최초 가입 관계 생성 시각 |
| updated_at | TIMESTAMPTZ | O | 마지막 변경 시각 |

- UNIQUE(gathering_id, user_id). FIXED_MEMBERS에만 사용하며 PER_SESSION은 이 테이블에 행을 만들지 않습니다.
- 참여 방식에 따른 사용 제한과 주최자 등록은 FK만으로 보장되지 않으므로 서비스 트랜잭션에서 검증합니다.

## gathering_sessions — 실제 개최 회차

| 컬럼 | 타입 | 필수 | 의미 |
| --- | --- | --- | --- |
| id | BIGINT | O | PK |
| gathering_id | BIGINT | O | FK → gatherings.id |
| starts_at / ends_at | TIMESTAMPTZ | O | 실제 회차 시작·종료, 종료 > 시작 |
| location | VARCHAR(255) | O | 개최 장소 (길이 초안) |
| capacity | INTEGER | O | 주최자 포함 회차 정원, 1 이상 |
| recruitment_opens_at | TIMESTAMPTZ | 조건부 | PER_SESSION의 공개 시각 |
| recruitment_closes_at | TIMESTAMPTZ | 조건부 | PER_SESSION의 마감 시각 |
| cancelled_at | TIMESTAMPTZ | X | 회차 취소 시각 |

- PER_SESSION에서 공개 <= 시작 - 2시간, 공개 < 마감 < 시작을 검증합니다. 신규 공개 시 과거 시각 지정은 거부합니다.
- 조기 마감·실제 공개 시각 감사 정보는 API 상세 설계와 함께 컬럼을 보완합니다.
- 고정 멤버 회차는 별도 모집하지 않습니다. 모임 가입과 회차 대상자 반영은 분리합니다.
- 일회성도 회차 하나를 생성합니다. 반복 모임은 유한 범위의 회차를 생성하며 생성 범위·중복 방지 키는 후속 확정합니다.

## gathering_session_participants — 회차 참여·출결

| 컬럼 | 타입 | 필수 | 의미 |
| --- | --- | --- | --- |
| id | BIGINT | O | PK |
| session_id | BIGINT | O | FK → gathering_sessions.id |
| user_id | BIGINT | O | FK → users.id |
| status | VARCHAR(20) | O | PENDING / CONFIRMED / REJECTED / CANCELLED |
| attendance_status | VARCHAR(20) | O | UNCHECKED / PRESENT / LATE / ABSENT, 기본값 UNCHECKED |
| attendance_checked_by | BIGINT | X | FK → users.id, 마지막 체크 주최자 |
| attendance_checked_at | TIMESTAMPTZ | X | 마지막 체크 시각 |
| created_at / updated_at | TIMESTAMPTZ | O | 최초 생성·마지막 수정 |

- UNIQUE(session_id, user_id). gathering_id는 session_id로 알 수 있으므로 중복 저장하지 않습니다.
- PER_SESSION은 이 관계에서 신청·승인·재신청을 처리합니다. 모임 가입 행은 요구하지 않습니다.
- FIXED_MEMBERS는 확정된 모임 가입자를 회차 참가 대상으로 반영합니다. 반영 시점·회차 불참 정책은 후속 결정합니다.
- 출결은 CONFIRMED 대상에만 적용합니다. 미확인에서는 체크 사용자·시각이 NULL이며 실제 체크 시 함께 기록합니다.

## activity_participants

| 컬럼 | 타입 | 필수 | 제약 및 의미 |
| --- | --- | --- | --- |
| id | BIGINT | O | PK |
| activity_id | BIGINT | O | FK → activities.id |
| user_id | BIGINT | O | FK → users.id |
| attendance_status | VARCHAR(20) | O | UNCHECKED / PRESENT / LATE / ABSENT, 기본값 UNCHECKED |
| attendance_checked_by | BIGINT | X | FK → users.id, 마지막 출결 체크 사용자 |
| attendance_checked_at | TIMESTAMPTZ | X | 마지막 출결 체크 시각 |
| created_at | TIMESTAMPTZ | O | 참가자 등록 시각 |
| updated_at | TIMESTAMPTZ | O | 마지막 수정 시각 |

- UNIQUE(activity_id, user_id)로 중복을 방지합니다.
- 출결 최초 상태는 UNCHECKED이며 체크 사용자·시각은 NULL입니다. 체크 시 사용자·시각을 함께 기록합니다.
- 활동 주최자의 본인 출결 체크 시 user_id와 attendance_checked_by가 같은 사용자 ID일 수 있습니다. 허용 범위는 [주최자 정책](domain-model.md#동아리-활동-주최자--확정)을 따릅니다.
- 출결 사용자 FK는 기록 목적입니다. 실제 권한은 현재 activities.organizer_id 또는 gatherings.organizer_id로 검증합니다.

## 초기 인덱스

| 테이블 | 키 | 목적 |
| --- | --- | --- |
| users | UNIQUE(intra_id) | 42 계정 조회 및 중복 방지 |
| club_managers | UNIQUE(user_id, club_id) | 권한 확인, 사용자별 관리 동아리 조회 |
| club_managers | INDEX(club_id) | 동아리별 관리자 조회 |
| activities | INDEX(club_id, activity_date) | 동아리별 활동 조회와 활동도 집계 |
| notices | INDEX(club_id, created_at) | 동아리별 공지 조회 |
| gatherings | INDEX(organizer_id) | 주최한 소모임 조회 |
| gathering_participants | UNIQUE(gathering_id, user_id) | 중복 신청 방지 및 모임별 참여자 조회 |
| gathering_participants | INDEX(user_id) | 내 소모임 참여 조회 |
| gathering_sessions | INDEX(gathering_id, starts_at) | 모임별 일정 조회 |
| gathering_session_participants | UNIQUE(session_id, user_id) | 회차 참여 중복 방지 |
| gathering_session_participants | INDEX(user_id) | 내 회차 참여 조회 |
| activity_participants | UNIQUE(activity_id, user_id) | 참가자 중복 방지 및 활동별 명단 조회 |
| activity_participants | INDEX(user_id) | 사용자별 활동 참여 조회 |

PK에는 기본 인덱스가 존재합니다. 추가 인덱스는 API 조회 조건과 실행 계획을 확인하며 결정합니다.

## 확정 범위와 후속 설계

10개 테이블과 관계·핵심 키를 MVP 기준으로 삼습니다. 모임 목적·반복 규칙·고정 멤버 모집·취소 등 후속 컬럼은 도메인 정책 확정 후 보완해야 하므로 아직 전체 스키마가 구현 가능한 최종본은 아닙니다.
42 API 실제 연동 가능 여부 및 인증 세션/토큰 방식은 아직 검증·설계가 필요합니다.
블랙홀에 따른 사용자 정보 정리, 기록 보존 기한·영구 파기 절차 등 남은 정책은
[도메인 문서](domain-model.md)의 후속 항목으로 관리합니다.
남은 정책의 기준은 [도메인 문서의 미정 사항](domain-model.md#남은-결정-사항)이며 작업 순서는 README를 따릅니다.
