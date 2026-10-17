# 로컬 시연 데이터

운영 초기 계정 등록은 [initial-data-setup.md](initial-data-setup.md)를 따른다.
이 스크립트는 별도의 로컬 시연 DB용이며 Flyway나 앱 시작 시 자동 실행되지 않는다.

## 포함하는 데이터

- 가상 사용자 12명: ADMIN 1명, 동아리 회장과 일반 부원. 실제 개인정보·로그인 자격증명 없음.
- 동아리 8개: 음악·AI·러닝·사진·보드게임·영화·요리·게임. 게임 동아리는 ARCHIVED.
- 회장·부원 관계, 공개 동아리 공지 8개, 운영 공지 3개.
- 활동 15개와 사진 15장: 지난 활동·출석 완료, 모집 중인 다음 활동, 외부인 참가 허용/불허.
- 소모임 8개와 회차 16개: 과거/미래 회차, 정원 마감 1개, 취소 1개, 주최자 포함 참가·출석 기록.

이름은 `[DEMO]`로 구분한다. 가상 사용자 intra_id는 -420001~-420012로 실제 양수 42 ID와 분리한다.
이 계정으로 OAuth 로그인할 수 없으며 실제 계정과 연결하거나 권한을 부여하지 않는다.
실제 사용자 권한은 기존 운영 절차를 따른다.

## 입력 전 파일 확인

저장소 루트에서 Python 3.8 이상으로 실행한다. 기본 이미지 경로는 형제 FE 디렉토리다.

```sh
python3 scripts/db/seed_demo.py
```

기본 실행은 **DB에 연결하지 않는다**. 무시되는 build/demo-seed/ 아래에
seed.sql과 activities/*.png를 생성한다. 파일을 검토한 후 입력한다.

```sh
python3 scripts/db/seed_demo.py --covers-dir ../42-clubs-FE/public/covers --output-dir build/demo-seed
```

사용하는 커버: activity-jam, activity-rag, activity-running,
club-photo, club-board, club-movie, club-cook, club-game.
PNG → JPEG → JPG → SVG 순서로 찾는다. PNG 외 파일은 macOS 기본 sips로 PNG로 변환한다.
다른 OS는 같은 이름의 PNG를 준비한다. 원본 FE 이미지는 수정하지 않는다.
활동별 UUID 파일 이름은 고정되어 재실행해도 파일 이름이 늘어나지 않는다.

현재 BE 스키마에는 동아리·소모임 커버 필드가 없다. 임의의 필드를 추가하지 않으며
**활동 사진만** 기존 로컬 사진 저장소와 연결한다. 소모임 API 대표 이미지는 기존처럼 null이다.

## 로컬 Compose에 입력

먼저 앱을 실행해 **V13까지** 마이그레이션을 완료한다.
Compose 프로젝트와 로컬 DB가 의도한 대상인지 확인하고 다른 쓰기 작업이 없는 시간에 실행한다.
설정은 [Compose 문서](docker-compose.md)를 따른다.

```sh
docker compose up -d --build --wait
python3 scripts/db/seed_demo.py --apply
```

--apply는 이 저장소의 Compose 설정으로 backend와 postgres 서비스에 접근한다.
기존 DB 계정은 컨테이너 환경에서 읽으며 비밀번호를 명령행에 넣지 않는다.
호스트 실행용 DB나 S3 모드에 적용하는 옵션은 제공하지 않는다.

사진은 /app/data/photos/activities/에 먼저 복사하고 SQL은 단일 트랜잭션으로 실행한다.
실패하면 DB 입력은 모두 롤백된다. 먼저 복사한 파일은 남을 수 있지만,
activity_images에 없는 사진은 공개 조회되지 않는다. 현재 compose.yaml의 기본 로컬 사진 볼륨을 전제로 한다.
생성 SQL의 세션 시간대는 Asia/Seoul이다.

## 재실행과 기존 데이터

- ID 시퀀스로 입력한다. 임의 PK·시퀀스 재설정·TRUNCATE·DELETE·UPDATE를 사용하지 않는다.
- 재실행하면 이미 있는 행·시각·상태를 보존하며 중복을 만들지 않는다.
- 같은 이름의 비시연 동아리·소모임 또는 예약된 음수 ID 충돌은 오류로 중단한다.
  시연 이름이 여러 개라 식별할 수 없어도 트랜잭션이 실패한다.
- 첫 입력 시점 기준 상대 시각으로 일정을 만든다. 재실행해도 기존 일정을 미래로 옮기지 않는다.
  며칠 뒤 새 시연을 준비하려면 별도 로컬 DB를 준비한다. DB 삭제 기능은 제공하지 않는다.
- API 호출이 아닌 SQL fixture다. 회장=주최자, 회장/부원 관계, 주최자 자동 참가,
  정원, 모집 마감/시작/종료 순서를 맞췄다.
- 시연 데이터가 실제 데이터로 오인되지 않도록 운영 DB에는 실행하지 않는다.

## 검증

```sh
python3 -B scripts/db/test_seed_demo.py
./gradlew build
```

첫 명령은 별도 PostgreSQL 17.6 Docker 컨테이너를 사용한다. 포트를 공개하지 않고 실제 Compose DB에 접속하지 않는다.
전체 마이그레이션 적용, 재실행 시 행·ID·시각 보존, 기존 데이터 보존, 충돌 롤백,
참가 정원/부원 제약, PNG 형식·크기·SQL 키 연결을 검증하고 컨테이너를 정리한다.
이미지 검증에는 형제 FE 커버 폴더가 필요하다.
