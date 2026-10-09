# 42 Clubs Backend

42 교육생을 위한 동아리 정보 탐색 및 운영 관리 플랫폼의 백엔드입니다.

## 개발 환경

- Java 17
- Spring Boot 3.5.16
- Gradle 8.14.3 (Wrapper 포함)
- Spring Web MVC, Bean Validation, JUnit 5

Java 17 JDK를 설치하고 IDE에서 이 폴더를 Gradle 프로젝트로 열어 주세요.
Gradle JVM도 Java 17로 설정합니다. 최초 빌드에는 의존성 다운로드를 위한 인터넷 연결이 필요합니다.

```sh
./gradlew build
./gradlew bootRun
```

Windows에서는 `gradlew.bat build`, `gradlew.bat bootRun`을 사용합니다.
애플리케이션은 기본 포트 `8080`으로 실행됩니다. 아직 API가 없으므로 `/` 요청의 404 응답은 정상입니다.

## 패키지 구조

단일 애플리케이션 안에 기능별 패키지를 두고, 각 기능 내부에 계층형 구조를 적용합니다.

```text
src/main/java/com/clubplatform/
├── ClubsApplication.java
├── global/
│   ├── config/
│   ├── exception/
│   └── security/
├── auth/
│   ├── controller/
│   ├── service/
│   └── dto/
├── user/
├── club/
├── activity/
└── notice/
```

`user`, `club`, `activity`, `notice`에는 각각 `controller`, `service`,
`repository`, `entity`, `dto` 패키지가 있습니다.
각 패키지의 `package-info.java`에 책임을 기록했습니다.
`auth`의 영속성 계층은 인증 방식이 정해진 뒤 필요할 때 추가합니다.

상세 규칙과 미결정 사항은 [아키텍처 문서](docs/architecture.md)를 참고하세요.

## 현재 구현 범위

프로젝트 실행 설정, 패키지 골격, 애플리케이션 컨텍스트 로딩 테스트를 제공합니다.
DB/JPA, 인증 제공자, Spring Security, 도메인 엔티티와 API는 아직 도입하지 않았습니다.
관리 API를 구현할 때는 인증과 동아리별 접근 권한 검증을 함께 적용해야 합니다.

## 브랜치

- `main`: 초기 상태 유지
- `develop`: 기본 구조 및 개발 변경사항 통합
- 이후 기능 작업은 `develop`에서 `feature/<기능명>` 브랜치를 만들어 진행

현재 저장소 폴더에서 브랜치를 전환하며 개발합니다. 별도 worktree는 필요하지 않습니다.
