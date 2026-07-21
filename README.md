# klljs backend

컴퓨터 비전 기반 옥외광고(OOH) 효과 측정 플랫폼의 백엔드다. 팀·캠페인·매체를 관리하고,
매체에 설치된 비전 디바이스가 SQS로 보내는 유동/노출/주목 인구 데이터를 받아 캠페인별
홈 대시보드(깔대기 그래프, 시간대별 노출도, 시청시간, 성별·연령 통계 등)로 보여준다.

- **Backend**: 이 저장소
- **Frontend**: [Shinhan-KLLJS/frontend](https://github.com/Shinhan-KLLJS/frontend) ([배포](https://frontend-rouge-two-52.vercel.app))
- **AI/Vision**: [Shinhan-KLLJS/ai](https://github.com/Shinhan-KLLJS/ai)
- **사업자등록증 OCR**: [Shinhan-KLLJS/biz-ocr](https://github.com/Shinhan-KLLJS/biz-ocr)

<!-- TODO(팀): 프로젝트 소개/소속(해커톤·공모전 등) 문구를 원하는 톤으로 다듬어 주세요. -->

---

## 목차

- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [아키텍처](#아키텍처)
- [ERD](#erd)
- [API 문서 (Swagger)](#api-문서-swagger)
- [로컬 실행](#로컬-실행)
- [환경변수](#환경변수-운영)
- [테스트](#테스트)
- [DB 마이그레이션](#db-마이그레이션)
- [CI/CD](#cicd)
- [문서](#문서)

---

## 주요 기능

| 도메인 | 기능 |
|---|---|
| 인증 | 카카오 소셜 로그인(OAuth2), Access/Refresh Token 발급·회전·재사용 탐지, 로그아웃 |
| 사용자 | 내 정보 조회 |
| 팀 | 사업자등록증 업로드(OCR) 기반 팀 생성, 팀명 수정 |
| 팀원 관리 | 초대 코드 발급/합류, 역할(OWNER/ADMIN/MEMBER) 변경, 팀원 삭제·나가기 |
| 매체 | 옥외광고 매체(전광판 등) 등록·검색·지역 필터 |
| 캠페인 등록 | 소재(이미지/영상) 업로드, 매체·기간 선택, 최종 등록 |
| 캠페인 페이지 | 팀 캠페인 목록/상세 조회, 캠페인명 수정, 삭제 |
| 홈 대시보드 | 캠페인 목록/상세/송출정보, 실시간·시간별 노출·주목 흐름 그래프, 평균 시청시간, 성별·연령 시청 비율, 시간대별 노출도 히트맵, 깔대기 그래프(유동→노출→주목→전환) |
| 유동인구 적재(관리자) | 서울시 250m 격자 생활인구 데이터 수동 적재 |

전체 API 목록과 상세 설명은 [API 문서(Swagger)](#api-문서-swagger)에서 도메인 단위로 확인할 수 있다.

---

## 기술 스택

| 구분 | 내용 |
|---|---|
| Language / Runtime | Java 21 |
| Framework | Spring Boot 4.1.0 (Web, Data JPA, Security, Validation, OAuth2 Client/Resource Server, Actuator) |
| 인증 | 카카오 OAuth2 로그인 + 자체 JWT(Access) / HttpOnly 쿠키(Refresh, 1회용 회전) |
| DB | MySQL 8 (운영, RDS) / H2 in-memory (로컬) |
| 마이그레이션 | Flyway (운영만 적용, 로컬은 Hibernate `ddl-auto=create-drop`) |
| API 문서 | springdoc-openapi (Swagger UI) |
| AWS | S3(소재·사업자등록증 업로드), SQS(비전 데이터 수신), Lambda(OCR 직접 invoke), CloudWatch(로그·대시보드), EC2/ALB/RDS |
| 기타 라이브러리 | PDFBox(사업자등록증 PDF 검증), proj4j(위경도 ↔ 서울시 250m 격자좌표 변환) |
| 빌드 | Gradle |
| 배포 | Docker → Docker Hub → EC2(SSM Run Command) |
| CI/CD | GitHub Actions |

---

## 아키텍처

### 서버 아키텍처

<!-- TODO(팀): docs/architecture/server-architecture.png 를 커밋하면 아래 이미지가 그대로 표시됩니다. -->
![서버 아키텍처](docs/architecture/server-architecture.png)

### 배포(CI/CD) 아키텍처

`main` 브랜치에 푸시되면 GitHub Actions가 Docker 이미지를 빌드해 Docker Hub에 올리고,
private subnet에 있는 EC2에 SSH 대신 **AWS SSM Run Command**로 배포 스크립트를 전달한다.
배포 스크립트는 `/actuator/health`가 정상 응답할 때까지 기다렸다가, 실패하면 직전에 성공했던
이미지로 자동 롤백한다(단, 되돌릴 수 없는 컬럼/테이블 삭제 마이그레이션이 낀 배포는 자동
롤백이 안전하지 않아 수동 대응이 필요하다).

<!-- TODO(팀): docs/architecture/deployment-architecture.png 를 커밋하면 아래 이미지가 그대로 표시됩니다. -->
![배포 아키텍처](docs/architecture/deployment-architecture.png)

---

## ERD

<!-- TODO(팀): ERD 이미지를 docs/images/erd.png 로 추가하면 아래에 표시됩니다. -->
![ERD](docs/images/erd.png)

마크다운으로 작성된 최신 ERD와 설계 결정 배경은 [docs/mvp-database-erd.md](docs/mvp-database-erd.md)에 있다
(Mermaid로 그려져 있어 GitHub에서 별도 이미지 없이도 바로 렌더링된다).

---

## API 문서 (Swagger)

- **로컬**: 서버 실행 후 http://localhost:8080/swagger-ui/index.html
- **운영**: 배포된 서버의 `/swagger-ui/index.html`

인증/사용자/팀/팀원 관리/사업자등록증/매체/캠페인 등록/캠페인 페이지/홈 대시보드/유동인구
적재(관리자) 순서로 도메인 단위로 정리되어 있다.

---

## 로컬 실행

사전 준비물은 JDK 21뿐이다. 로컬 프로필은 H2 인메모리 DB를 쓰므로 Docker나 별도 DB 서버가
필요 없다.

```bash
git clone https://github.com/Shinhan-KLLJS/backend.git
cd backend
./gradlew bootRun            # Windows CMD: gradlew.bat bootRun
```

기본적으로 `local` 프로필로 뜨며(`application.yml`), 뜰 때 다음이 자동으로 이뤄진다.

- H2 인메모리 스키마 자동 생성(`ddl-auto=create-drop`) — Flyway는 로컬에서 비활성화
- 테스트용 시드 데이터 생성: 사용자 1명(OWNER) + 팀 1개 + 매체 1개 + 캠페인 1개
- 그 사용자로 즉시 쓸 수 있는 Access Token을 애플리케이션 로그에 출력
  (`Local dashboard test Authorization header: Bearer ...`)
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:klljsdb;MODE=MySQL`, user `sa`, 비밀번호 없음)

로그에 출력된 토큰을 `Authorization: Bearer {token}` 헤더에 넣으면 Swagger UI나 curl로 바로
API를 호출해볼 수 있다. 시드 데이터가 필요 없으면 `LOCAL_DASHBOARD_MOCK_DATA_ENABLED=false`
환경변수로 끌 수 있다.

카카오 로그인 자체(`/oauth2/authorization/kakao`)를 로컬에서 끝까지 테스트하려면
`KAKAO_REST_API_KEY`/`KAKAO_CLIENT_SECRET`/`KAKAO_REDIRECT_URI` 환경변수로 실제 카카오
앱 값을 넣어야 한다. 넣지 않아도 서버는 정상적으로 뜬다(플레이스홀더 값 사용).

---

## 환경변수 (운영)

로컬은 `application-local.yml`에 실행 가능한 기본값이 이미 채워져 있어 별도 설정이 필요
없다. 아래는 `prod` 프로필(운영 배포)에 주입해야 하는 값들이다 — 실제 배포는 GitHub
Actions CD가 AWS SSM Parameter Store에서 읽어와 컨테이너 환경변수로 주입한다
(`.github/workflows/cd.yml` 참고).

| 변수 | 용도 |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | RDS MySQL 접속 정보 |
| `KAKAO_REST_API_KEY` / `KAKAO_CLIENT_SECRET` / `KAKAO_REDIRECT_URI` | 카카오 OAuth2 앱 정보 |
| `APP_FRONTEND_URL` / `ADDITIONAL_ALLOWED_ORIGINS` | CORS·리다이렉트 허용 오리진 |
| `APP_SWAGGER_ORIGIN` | Swagger UI 자신의 오리진(CSRF 신뢰 목록에 자동 포함) |
| `REFRESH_COOKIE_SAME_SITE` | Refresh Token 쿠키 SameSite (기본 `Lax`) |
| `JWT_SECRET` | 자체 Access/Refresh Token 서명 키(HMAC) |
| `VISION_SQS_QUEUE_URL` | 비전 디바이스 데이터 수신 SQS 큐 |
| `CAMPAIGN_CREATIVE_BUCKET` / `_PUBLIC_BASE_URL` / `_UPLOAD_TOKEN_SECRET` | 캠페인 소재 업로드용 S3 |
| `BUSINESS_REGISTRATION_BUCKET` / `_UPLOAD_TOKEN_SECRET` / `_OCR_FUNCTION` | 사업자등록증 업로드·OCR용 S3/Lambda |

민감한 값(시크릿·비밀번호)은 전부 SSM `SecureString`으로 저장하고, 코드에는 기본값을 두지
않아 값이 없으면 기동 자체가 실패하도록 되어 있다.

---

## 테스트

```bash
./gradlew test
```

일반 테스트는 H2로 돈다. 잠금(락) 동작을 검증하는 동시성 테스트는 H2(READ COMMITTED)와
MySQL InnoDB(REPEATABLE READ)의 격리 수준 차이로 H2에서는 재현되지 않는 버그가 있어
`mysql-concurrency` 태그로 분리되어 있고, 기본 `test` 실행에서는 제외된다. 실제 MySQL로
검증하려면:

```bash
docker run -d --name klljs-mysql-test -p 13308:3306 \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=klljs_concurrency_test \
  mysql:8.0.46

./gradlew mysqlConcurrencyTest

docker rm -f klljs-mysql-test
```

---

## DB 마이그레이션

운영 DB 스키마는 `src/main/resources/db/migration`의 Flyway 마이그레이션(V1~V13)으로 관리하고,
애플리케이션은 `ddl-auto=validate`로 스키마와의 불일치만 검증할 뿐 직접 바꾸지 않는다. 로컬은
반대로 Flyway 없이 Hibernate `ddl-auto=create-drop`으로 매번 새로 만든다.

**검증은 반드시 실제 MySQL로 한다** — 로컬 프로필은 Flyway 자체를 안 타고, H2는 잠금/제약
동작이 MySQL과 달라 Flyway·FK·동시성 관련 버그를 못 잡는다. 새 마이그레이션을 추가하면
`mysql:8.0.46` Docker 컨테이너에 `V1`부터 전체 이력을 처음부터 적용해보고 통과하는지 확인할 것
(신규 스테이징 환경, MySQL 기반 로컬 셋업, 마이그레이션만으로 하는 DR 복구가 모두 이 경로를
탄다 - `V12__recreate_vision_summary_5s.sql`가 한동안 이 경로에서 "Table already exists"로
막혀 있었던 사례가 있다).

**이미 운영에 적용된 마이그레이션 파일은 원칙적으로 수정하지 않는다.** 불가피하게 수정하면
(예: `V12`처럼 사고 복구용으로 작성된 마이그레이션을 신규 환경에서도 안전하게 만들 때)
파일 내용이 바뀌어 checksum이 바뀌므로, 그 버전이 이미 적용된 환경(운영)에서는 다음 배포 시
Flyway validate가 checksum mismatch로 실패해 앱이 기동하지 않는다. 이런 수정을 배포하기 전에는
운영 DB에 대해 먼저 `flyway repair`(또는 동등하게 `flyway_schema_history`의 해당 버전 checksum을
새 파일 기준으로 수동 갱신)를 실행해야 한다 - repair는 메타데이터만 갱신하고 SQL을 실행하지
않으므로 운영 스키마 자체에는 영향이 없다.

---

## CI/CD

- **CI** (`.github/workflows/ci.yml`): `main`/`dev`로의 push·PR마다 `./gradlew test` 실행
- **CD** (`.github/workflows/cd.yml`): `main` push 시 Docker 이미지 빌드·푸시 → EC2에 SSM
  Run Command로 배포 → 헬스체크 실패 시 직전 이미지로 자동 롤백

---

## 문서

| 문서 | 내용 |
|---|---|
| [docs/mvp-database-erd.md](docs/mvp-database-erd.md) | 전체 ERD와 테이블별 설계 결정 |
| [docs/team-creation-api-spec.md](docs/team-creation-api-spec.md) | 팀 생성(사업자등록증 업로드·OCR) API 설계 |
| [docs/campaign-registration-api-spec.md](docs/campaign-registration-api-spec.md) | 캠페인 등록(소재 업로드·최종 등록) API 설계 |
| [docs/campaign-page-api-spec.md](docs/campaign-page-api-spec.md) | 캠페인 페이지(목록/상세/수정/삭제) API 설계 |
| [docs/server-verification-spec.md](docs/server-verification-spec.md) | 사업자등록증 진위확인 명세 (MVP 범위 제외, 보류) |
| [docs/dv-112-open-decisions.md](docs/dv-112-open-decisions.md) | 구현 중 발생한 팀 결정 필요 안건 모음 |

---

## 팀원

<!-- TODO(팀): 이름/역할/GitHub 링크 등을 추가해 주세요. -->
