# 42 Clubs Backend

42 교육생을 위한 동아리 정보·활동·소모임 플랫폼의 백엔드입니다.
Java 17, Spring Boot 3.5, Spring Security, JPA, PostgreSQL 17.6, Flyway를 사용합니다.
정확한 의존성 버전은 [build.gradle](build.gradle)과 [Gradle Wrapper](gradle/wrapper/gradle-wrapper.properties)가 기준입니다.

## 빠른 시작

Docker Compose로 Nginx·백엔드·PostgreSQL을 함께 실행합니다. 컨테이너 실행에는 호스트 JDK가 필요하지 않습니다.

```sh
docker compose up -d --build --wait
```

Nginx 진입 주소는 `http://localhost:8080`입니다. [Compose 구성·볼륨·홈서버·IDE 실행 안내](docs/docker-compose.md)를 참고합니다. 42 인증을 설정하지 않아도 동아리 공개 조회를 실행할 수 있습니다.

```sh
curl 'http://localhost:8080/api/v1/clubs?page=0&size=20'
```

새 DB에는 동아리 데이터가 없어 목록이 비어 있습니다. `/`의 404는 정상입니다.
애플리케이션 시작 시 Flyway가 마이그레이션을 적용하고 Hibernate가 매핑을 검증합니다.

### 개발 환경 및 실행

- 기본 Compose는 Nginx만 외부에 노출합니다. IDE 개발 시 `compose.dev.yaml`을 함께 사용해 DB를 `127.0.0.1:5432`에 노출합니다. 기본 DB·계정·비밀번호는 개발용이며 [환경변수 문서](docs/environment-variables.md#데이터베이스)에서 확인합니다.
- 기본값은 별도 설정 없이 사용할 수 있습니다. 변경 값은 [.env.example](.env.example)을 참고해 셸 또는 IDE에 설정합니다. Spring Boot는 `.env`를 자동으로 읽지 않습니다.
- Compose 볼륨은 재시작 후에도 유지됩니다. DB 이름·계정 변경은 기존 볼륨을 재초기화하지 않습니다. 중지는 `docker compose stop`을 사용합니다.
- Windows에서는 `gradlew.bat`을 사용합니다. 최초 빌드에는 의존성 다운로드가 필요합니다.

### 42 로그인 실행

서비스용 42 OAuth 앱의 자격증명을 설정한 뒤 `AUTH_ENABLED=true`로 실행합니다.
[로컬 로그인 절차](docs/environment-variables.md#프론트엔드-없이-로컬-로그인-테스트)에서 콜백·성공 URL 설정과 프론트엔드 없이 확인하는 방법을 안내합니다.
프론트엔드 연결은 [인증 API 계약](docs/api-endpoints.md#인증--구현-계약)을 따릅니다.

## 검증과 포맷

```sh
./gradlew build          # 컴파일·테스트·포맷 검사
./gradlew spotlessApply  # 포맷 자동 수정
./gradlew spotlessCheck  # 포맷만 검사
```

통합 테스트는 Testcontainers가 별도 PostgreSQL을 생성하므로 Docker가 실행 중이어야 합니다.
로컬 Compose DB를 먼저 실행할 필요는 없으며 Docker가 없으면 테스트가 실패합니다.
Spotless는 Google Java Format AOSP 스타일을 적용하며 빌드 중에는 파일을 자동 수정하지 않습니다.

## 현재 상태

| 영역 | 상태 |
| --- | --- |
| 실행·DB 기반 | 완료: Java 17, Spring Boot, JPA, Flyway, PostgreSQL Compose, Spotless·Lombok |
| 동아리 공개 조회 | 완료: 목록·상세, 페이지네이션·운영 상태 필터·모집 표시 |
| 인증·내 정보 | 완료: 42경산 OAuth, 세션·CSRF·로그아웃·USER/ADMIN/SUPER_ADMIN 검사; 실제 42 자격증명 연동 확인 필요 |
| DB 스키마 | V1~V10 구현: 사용자·동아리·권한·부원·공지·활동/사진/참가·소모임/회차/참가·사진 정리 저널·공지 공개 범위·운영 공지·회차 제목 |
| 역할·회장 권한 기반 | 완료: SUPER_ADMIN의 ADMIN 운영 권한 포함, ACTIVE 동아리별 회장 관계 검사, [초기 DB 입력 절차](docs/initial-data-setup.md); 사용자 검색·SUPER_ADMIN의 ADMIN/회장 권한 관리 API 구현 완료 |
| 부원·활동·공지·소모임 | 완료: 부원 등록·명단, 동아리 공지 공개 범위·운영 공지, 활동·사진, 소모임·회차, 선착순 참가·취소·기본 출결; 사진 첨부는 기본 로컬 저장, [저장소 설정](docs/environment-variables.md#활동-사진-저장소) 필요 |
| Swagger/OpenAPI | 미도입: Boot 3.5 호환 springdoc 2.8.x의 패치 버전 결정 필요 |
| CI·배포 | GitHub Actions CI 미도입; Nginx·백엔드·PostgreSQL Compose 구현; 공개 배포·HTTPS·CD 미정 |

[MVP 구현 계획](docs/mvp-implementation-plan.md)의 0~7단계와 업무 API 34개를 구현했습니다. 실제 42 로그인·프론트엔드 연동, 운영 초기 데이터 입력과 사진 저장 디렉터리·이미지 조회 주소 설정은 배포 환경에서 확인해야 합니다.
사진은 기본적으로 호스트 실행 시 `./data/photos`, Compose에서는 `photo_data` 볼륨에 저장하며 `http://localhost:8080/api/v1/photos/activities/{filename}`으로 조회합니다. AWS 계정·자격증명 없이 로컬 평가가 가능합니다. 다른 호스트·포트에서는 `PHOTO_LOCAL_BASE_URL`을 변경합니다. 호스트 실행에서는 S3를 `PHOTO_STORAGE_MODE=s3`로 선택할 수 있고 기본 Compose는 로컬 볼륨을 사용합니다.
메인 운영 공지는 비로그인 공개이며 동아리 공지는 기본 비공개입니다. 공지·활동 상세와 활동 참가·출결 경로는 clubId 아래로 통일했습니다. 기존 경로는 제거했으므로 [전환 계약](docs/api-endpoints.md#공지활동-경로-전환)을 확인합니다.
소모임 회차 상세·참가·출결도 `/api/v1/gatherings/{gatheringId}/sessions/{sessionId}` 아래로 통일했습니다. 기존 경로는 [소모임 회차 전환 계약](docs/api-endpoints.md#소모임-회차-경로-전환)을 따릅니다.
글 수정·일정 변경 등 확장은 [후속 설계](docs/future-design.md)에서 확인합니다.

## 문서 안내

| 알고 싶은 내용 | 문서 |
| --- | --- |
| 현재 MVP 범위·권한·참가 정책·보류 사항 | [도메인](docs/domain-model.md) |
| 구현된 전체 엔드포인트·권한·요청/응답·오류 | [API 명세](docs/api-endpoints.md) |
| 프론트 연동용 생성·조회·참가·출결 예시 | [MVP API 연결 예시](docs/mvp-api-examples.md) |
| 기능별 엔드포인트 구현 순서·DB 작업·완료 조건 | [MVP 구현 계획](docs/mvp-implementation-plan.md) |
| 초기 동아리·회장·최고 관리자 DB 입력 | [초기 데이터 절차](docs/initial-data-setup.md) |
| 실제 DB·관계도·제약조건 | [ERD](docs/erd.md) |
| 패키지·계층·의존성·DB 운영 규칙 | [아키텍처](docs/architecture.md) |
| 실제 코드 흐름과 구현 시 확인 사항 | [모듈 가이드](docs/module-development.md) |
| Docker 전체 실행·Nginx·사진/DB 볼륨 | [Compose 실행](docs/docker-compose.md) |
| 환경변수·로컬 로그인·문제 해결 | [환경변수](docs/environment-variables.md) |
| 현재 범위에서 제외된 정책·API·스키마 | [후속 확장 참고 설계](docs/future-design.md) |
| 로그인 구현 커밋·검증 기록 | [로그인 구현 기록](docs/login-implementation.md) |

## 문서 관리 (SSOT)

각 문서는 위 표의 주제를 담당합니다. 같은 규칙을 여러 문서에 복사하지 않고 기준 위치를 링크합니다.
현재 MVP 범위·업무 정책은 도메인, HTTP 계약은 API 명세, 저장 구조는 ERD를 기준으로 대조합니다. 구현 계획·연동 예시·과거 구현 기록은 기준 문서의 계약을 확장하지 않습니다. 후속 설계의 “확정”은 해당 기능 도입 시 적용할 정책이며 현재 MVP에 포함됐다는 뜻이 아닙니다.
실행·검증 명령과 구현 상태는 README, 협업·커밋·브랜치 규칙은 [AGENTS.md](AGENTS.md)를 기준으로 합니다.
환경변수의 이름·용도·기본값·필수 조건·사용 대상·로컬 설정은 [환경변수 문서](docs/environment-variables.md)와 `.env.example`에 함께 반영합니다. 실제 기본값은 application.yml·compose.yaml이 기준입니다.

기능 변경은 **도메인 → ERD → API → 마이그레이션·코드·테스트** 순서로 진행합니다.
관계도는 ERD, 구조도는 아키텍처 문서에서 관리하며 스키마 영향이 없으면 ERD 변경은 생략합니다.
기존 확장 설계를 채택할 때는 해당 내용을 현재 기준 문서로 옮겨 중복을 남기지 않습니다.
공유 DB에 적용된 스키마 변경 이력의 원본은 [Flyway 마이그레이션](src/main/resources/db/migration)입니다.

## 작업 방식

기본 작업 브랜치는 최신 `develop`이며 검증한 변경을 기능·정책 단위로 커밋·push합니다.
PR은 필요할 때 `develop`을 대상으로 만들고, 기존 PR 병합·`main` 반영·릴리스는 별도 요청에 따라 진행합니다.
릴리스 시 develop → main은 일반 merge로 이력을 연결하고 태그를 남깁니다. 세부 규칙은 [AGENTS.md](AGENTS.md)를 따릅니다.
