# 캠페인 등록 API SPEC

## 1. 목적과 범위

이 문서는 3단계 캠페인 등록 화면과 내부 관리자용 매체 등록 기능의 MVP API 계약을 정의한다.

1. 이미지 또는 영상 소재와 캠페인 기본 정보 입력
2. 광고를 연결할 매체 선택
3. 입력 정보 최종 확인 후 캠페인 등록

MVP에서 업로드한 소재를 실제 매체에 송출하거나 변환·분석하지 않는다. 서비스의 핵심은 하나의
Vision SQS 데이터를 캠페인별 대시보드 데이터로 연결하는 것이다.

이 범위에서는 다음 기능을 만들지 않는다.

- 단계별 임시 저장과 캠페인 DRAFT
- 파일 업로드 완료 API
- 파일 형식·용량·코덱·재생 길이·해상도 검사
- 소재와 매체 해상도 호환 검사
- 썸네일 생성, 트랜스코딩, 실제 광고 송출
- 최종 확인 전용 API
- 캠페인 수정·삭제 API

프론트는 1~3단계 입력 상태를 메모리에 보관한다. 최종 확인 화면의 "정보 수정"은 이전 단계로
돌아가는 프론트 동작이며 서버 API를 호출하지 않는다. 새로고침하면 작성 중 상태가 사라지는 것을
MVP에서 허용한다.

---

## 2. 확정된 설계

### 2-1. 캠페인 소재

- 캠페인 하나에는 이미지 또는 영상 소재 정확히 하나가 필수다.
- `creativeType`은 `IMAGE` 또는 `VIDEO`다.
- `campaign-creatives/` 경로의 소재는 인증 없이 조회할 수 있는 public-read로 제공한다.
- 익명 쓰기·수정·삭제는 허용하지 않고, 업로드는 서버가 발급한 Presigned PUT URL로만 수행한다.
- 서버는 S3 업로드 URL, 공개 조회 URL과 서명된 `creativeToken`을 발급한다.
- 최종 등록 시 토큰의 서명·만료·업로더를 확인하지만 S3 객체 존재 여부는 다시 조회하지 않는다.
- 파일 내용과 메타데이터는 검사하지 않는다.
- 같은 사용자가 유효기간 안에 동일한 `creativeToken`으로 여러 캠페인을 등록하는 것을 허용한다.

### 2-2. 캠페인과 매체

- 캠페인 하나는 매체 하나를 필수로 선택한다.
- 하나의 매체는 기간이 겹치지 않는 여러 캠페인에서 재사용할 수 있다.
- 동일 매체의 두 캠페인 집행 기간은 겹칠 수 없다.
- 시작일과 종료일은 KST 달력 날짜이며 양 끝 날짜를 모두 포함한다.
- 과거·오늘·미래 시작일을 모두 허용한다.
- 과거 캠페인을 나중에 등록해도 기존 `campaign_id = null` Vision 데이터는 백필하지 않는다.

### 2-3. 공용 Vision 소스

모든 매체는 MVP에서 동일한 Vision 장비 코드를 사용한다.

```text
deviceCode = adscope-cam-01
boardCode  = board_gangnam_01
```

SQS 메시지 한 건은 해당 이벤트 날짜에 캠페인이 존재하는 각 매체로 fan-out된다. 서로 다른 매체에
동시에 캠페인이 있으면 동일한 Vision 수치가 각 캠페인에 복제 저장된다. 캠페인이 없는 매체에는
행을 만들지 않는다.

이 방식은 실제 장소별 측정이 아니라 MVP Mock 정책이다. 여러 캠페인의 수치를 합산하면 동일한
원본 인원이 중복 계산되므로, 캠페인 간 합산 지표에는 사용하지 않는다.

### 2-4. 권한

- 캠페인 소재 업로드 URL 발급: 로그인 사용자
- 매체 목록/지역 목록: 로그인 사용자
- 캠페인 최종 등록: 해당 팀의 ACTIVE `OWNER` 또는 `ADMIN`
- `MEMBER`: 캠페인 등록 불가
- 관리자용 매체 등록: MVP 확정사항에 따라 인증·인가 없음

`POST /api/v1/admin/media-units`는 배포 환경에서도 공개 호출될 수 있다. 경로에 `admin`이 들어간
것은 보안 기능이 아니다. 이 위험을 MVP에서 명시적으로 수용하며, 운영 전에는 관리자 인증 또는
네트워크 접근 제한을 추가해야 한다.

**에러 코드 정정**: `MEMBER`가 등록을 시도하는 상황에 `TEAM_403_002`(`TEAM_MANAGEMENT_FORBIDDEN`,
"팀원을 관리할 권한이 없습니다")를 쓰려고 했는데, 이 코드는 팀원 역할 변경/강퇴 전용이라
메시지가 상황과 안 맞는다. 캠페인 등록·삭제 권한에는 `TeamErrorCode`의 아래 전용 값을 사용한다:

```java
CAMPAIGN_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "TEAM_403_003", "캠페인을 등록하거나 삭제할 권한이 없습니다.")
```

`campaign-page-api-spec.md`의 캠페인 삭제 API도 같은 상황(`MEMBER`가 삭제 시도)에 같은
코드를 쓴다 — 두 문서가 같은 에러 코드를 공유하므로 한쪽에서만 정의하면 된다.

---

## 3. API 목록

| 기능 | HTTP | 엔드포인트 | 인증 |
|---|---|---|---|
| 캠페인 소재 업로드 URL 발급 | POST | `/api/v1/campaign-creatives/upload-url` | 로그인 필요 |
| 매체 목록·검색·지역 필터 | GET | `/api/v1/media-units` | 로그인 필요 |
| 매체 지역 목록 | GET | `/api/v1/media-units/regions` | 로그인 필요 |
| 관리자용 매체 등록 | POST | `/api/v1/admin/media-units` | 없음 |
| 캠페인 최종 등록 | POST | `/api/v1/teams/{teamId}/campaigns` | OWNER/ADMIN |

별도의 1단계 저장 API, 검색 전용 API, 매체 상세 API, 최종 확인 API는 만들지 않는다.

---

## 4. 캠페인 소재 업로드 URL 발급 API

### Request

```http
POST /api/v1/campaign-creatives/upload-url
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "creativeType": "VIDEO",
  "originalFilename": "nike-summer.mp4",
  "contentType": "video/mp4"
}
```

### Request Fields

| 필드 | 필수 | 타입 | 설명 |
|---|---:|---|---|
| `creativeType` | Y | string | `IMAGE` 또는 `VIDEO` |
| `originalFilename` | Y | string | 원본 파일명, 최대 255자 |
| `contentType` | Y | string | Presigned PUT의 `Content-Type`, 최대 100자 |

파일명은 표시·추적용 메타데이터일 뿐 S3 키에 직접 사용하지 않는다. 경로 조작을 막기 위해 S3 키는
서버가 UUID로 생성한다.

```text
campaign-creatives/{uploaderId}/{uuid}
```

`creativeType` 외에는 파일 종류를 검증하지 않는다. `creativeType = IMAGE`인데 실제 바이트가 영상인
경우도 MVP에서는 허용한다.

### 처리 순서

1. JWT `sub`에서 `uploaderId`를 확인한다.
2. 서버가 S3 키를 생성한다.
3. 해당 키에 대한 Presigned PUT URL을 생성한다. 쓰기 URL의 만료 시간은 1시간이다.
4. 공개 조회 base URL과 S3 키를 조합해 만료되지 않는 `creativeUrl`을 만든다.
5. 아래 payload를 HMAC-SHA-256으로 서명해 `creativeToken`을 만든다.
6. Presigned PUT URL, 공개 조회 URL과 토큰을 응답한다.

```text
v1.{base64url(payload)}.{base64url(hmacSha256(payload, secret))}

payload = {
  purpose: "campaign-creative",
  s3Key,
  uploaderId,
  creativeType,
  originalFilename,
  expiresAtEpochSecond
}
```

JWT 서명 키나 `TokenHasher`를 재사용하지 않는다. 별도
`CAMPAIGN_CREATIVE_UPLOAD_TOKEN_SECRET` 환경변수를 사용한다.

공개 조회 URL의 base는 `CAMPAIGN_CREATIVE_PUBLIC_BASE_URL` 환경변수로 관리한다. S3 버킷 정책 또는
CDN 정책은 `campaign-creatives/*`에 대한 익명 `GetObject`만 허용하고, 익명 `PutObject`와
`DeleteObject`는 허용하지 않는다. Presigned PUT URL은 업로드 전용이며 공개 조회 URL로 재사용하지
않는다.

이 토큰은 Presigned URL 발급 시점에 함께 발급되므로 실제 PUT 성공을 보증하지 않는다. 최종 등록
API도 S3 `HeadObject`를 호출하지 않는다. 클라이언트가 PUT하지 않고 최종 등록하면 DB가 존재하지
않는 키를 참조할 수 있으며, 이 위험을 파일 검증을 하지 않는 MVP 정책으로 수용한다.

### Response

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "uploadUrl": "https://s3.ap-northeast-2.amazonaws.com/...",
    "creativeUrl": "https://cdn.example.com/campaign-creatives/42/550e8400-e29b-41d4-a716-446655440000",
    "method": "PUT",
    "requiredHeaders": {
      "Content-Type": "video/mp4"
    },
    "creativeToken": "v1.eyJwdXJwb3NlIjoiY2FtcGFpZ24tY3JlYXRpdmUiLC4uLn0.signature",
    "expiresAt": "2026-07-13T15:00:00+09:00"
  }
}
```

프론트는 `uploadUrl`에 파일을 PUT한 뒤 `creativeToken`을 보관해 최종 등록 요청에 사용한다. PUT이
완료된 후 미리보기와 조회에는 `creativeUrl`을 사용한다. `creativeUrl`은 공개 URL이므로 접근 토큰을
붙이지 않는다.

### 에러 케이스

| 상황 | HTTP / 코드 |
|---|---|
| 로그인하지 않음 | `401` |
| 필수 필드 누락, 잘못된 `creativeType`, 문자열 길이 초과 | `400 / COMMON_400_002` |
| Presigned URL 생성 실패 | `500 / COMMON_500_001` |

---

## 5. 관리자용 매체 등록 API

클라이언트 캠페인 등록 화면에서는 사용하지 않는다. 운영 관리자가 Swagger 또는 내부 도구로 매체
마스터 데이터를 입력하기 위한 API다.

매체 사진 파일 업로드 API는 이 범위에 포함하지 않는다. 관리자는 미리 준비한 공개 이미지 또는
CDN URL을 `photoUrl`에 직접 입력한다.

### Request

```http
POST /api/v1/admin/media-units
Content-Type: application/json

{
  "mediaName": "삼성동 신라스테이 전광판",
  "photoUrl": "https://cdn.example.com/media-units/samsung-station.jpg",
  "locationAddress": "서울 강남구 영동대로 506",
  "sido": "서울특별시",
  "sigungu": "강남구",
  "latitude": 37.5090123,
  "longitude": 127.0631145,
  "widthMm": 81000,
  "heightMm": 20000,
  "resolutionWidthPx": 1312,
  "resolutionHeightPx": 1664,
  "shapeTypes": ["FLAT", "VERTICAL"]
}
```

### Request Fields

| 필드 | 필수 | 타입 | 설명 |
|---|---:|---|---|
| `mediaName` | Y | string | 매체 이름, 최대 200자 |
| `photoUrl` | Y | string | 목록/최종 확인 화면용 이미지 URL, 최대 2048자 |
| `locationAddress` | Y | string | 표시용 전체 주소, 최대 500자 |
| `sido` | Y | string | 시/도, 최대 20자 |
| `sigungu` | Y | string | 시/군/구, 최대 50자 |
| `latitude` | Y | decimal | 지도 표시용 위도, `-90` 이상 `90` 이하 |
| `longitude` | Y | decimal | 지도 표시용 경도, `-180` 이상 `180` 이하 |
| `widthMm` | Y | integer | 물리 가로 규격(mm), 1 이상 |
| `heightMm` | Y | integer | 물리 세로 규격(mm), 1 이상 |
| `resolutionWidthPx` | Y | integer | 해상도 가로(px), 1 이상 |
| `resolutionHeightPx` | Y | integer | 해상도 세로(px), 1 이상 |
| `shapeTypes` | Y | array | 1개 이상, 중복 불가 |

매체 형태는 다음 세 값만 사용한다.

| enum | 화면 표시 |
|---|---|
| `FLAT` | 평면형 |
| `VERTICAL` | 세로형 |
| `CORNER` | 곡선형/코너형 |

`boardCode`, `deviceCode`, `status`는 요청받지 않고 서버가 다음 값으로 저장한다.

```text
boardCode  = board_gangnam_01
deviceCode = adscope-cam-01
status     = ACTIVE
```

문자열은 앞뒤 공백을 제거하고 빈 문자열을 거부한다. 매체명 중복은 허용한다. 주소 문자열을 파싱해
`sido`/`sigungu`를 만들지 않고 관리자가 보낸 구조화된 값을 그대로 저장한다.

### Response

HTTP 상태는 `201 Created`다.

```json
{
  "isSuccess": true,
  "code": "COMMON_201_001",
  "message": "리소스가 성공적으로 생성되었습니다.",
  "result": {
    "mediaUnitId": 12,
    "mediaName": "삼성동 신라스테이 전광판",
    "boardCode": "board_gangnam_01",
    "deviceCode": "adscope-cam-01",
    "status": "ACTIVE"
  }
}
```

### 에러 케이스

| 상황 | HTTP / 코드 |
|---|---|
| 필수 필드 누락, 범위·길이 오류, 빈 `shapeTypes` | `400 / COMMON_400_002` |
| 알 수 없는 형태 값 또는 형태 중복 | `400 / COMMON_400_002` |
| DB 저장 관련 내부 오류 | `500 / COMMON_500_001` |

인증 오류는 정의하지 않는다.

---

## 6. 매체 목록·검색·지역 필터 API

하나의 GET API가 전체 목록, 키워드 검색, 시/구 필터와 캠페인 기간 가용성 확인을 모두 담당한다.
매체 수가 적은 MVP를 전제로 페이지네이션 없이 조건에 맞는 ACTIVE 매체를 전부 반환한다.

### Request

```http
GET /api/v1/media-units
  ?keyword=삼성동
  &sido=서울특별시
  &sigungu=강남구
  &executionStartDate=2026-07-11
  &executionEndDate=2026-07-12
Authorization: Bearer {accessToken}
```

| 파라미터 | 필수 | 설명 |
|---|---:|---|
| `keyword` | N | 매체명 또는 전체 주소의 대소문자 무시 부분 일치, 최대 100자 |
| `sido` | N | 시/도 정확히 일치 |
| `sigungu` | N | 시/군/구 정확히 일치. 지정하려면 `sido`도 필요 |
| `executionStartDate` | Y | 선택 캠페인 시작일 `yyyy-MM-dd` |
| `executionEndDate` | Y | 선택 캠페인 종료일 `yyyy-MM-dd` |

`keyword`는 앞뒤 공백 제거 후 빈 문자열이면 없는 것과 같다. `%`, `_`는 와일드카드가 아니라 일반
문자로 검색한다. 종료일이 시작일보다 빠르면 `400`이다.

### 가용성 규칙

다음 조건을 만족하는 기존 캠페인이 하나라도 있으면 `available = false`다.

```text
existing.mediaUnitId = mediaUnit.id
existing.status != REGISTRATION_FAILED
existing.executionStartDate <= requestedEndDate
existing.executionEndDate >= requestedStartDate
```

과거 종료된 `AFTER_EXECUTION` 캠페인도 같은 과거 날짜에 새 캠페인을 등록할 때는 충돌 대상으로
본다. 하나의 매체·날짜에 캠페인을 하나만 유지해 Vision 귀속을 모호하지 않게 하기 위해서다.

### Response

목록 정렬은 `mediaName` 오름차순, 동명이면 `mediaUnitId` 오름차순이다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "mediaUnits": [
      {
        "mediaUnitId": 12,
        "mediaName": "삼성동 신라스테이 전광판",
        "photoUrl": "https://cdn.example.com/media-units/samsung-station.jpg",
        "locationAddress": "서울 강남구 영동대로 506",
        "sido": "서울특별시",
        "sigungu": "강남구",
        "latitude": 37.5090123,
        "longitude": 127.0631145,
        "widthMm": 81000,
        "heightMm": 20000,
        "resolutionWidthPx": 1312,
        "resolutionHeightPx": 1664,
        "shapeTypes": ["FLAT", "VERTICAL"],
        "available": true,
        "unavailableReason": null
      }
    ]
  }
}
```

기간 충돌이면 `unavailableReason = "PERIOD_CONFLICT"`다. 목록에서 제외하지 않고 반환해 프론트가
비활성 표시할 수 있게 한다. 매체가 없으면 에러가 아니라 `mediaUnits: []`를 반환한다.

### 에러 케이스

| 상황 | HTTP / 코드 |
|---|---|
| 로그인하지 않음 | `401` |
| 날짜 누락·형식 오류·역전 | `400 / CAMPAIGN_400_001` |
| `sigungu`만 전달, keyword 길이 초과 | `400 / COMMON_400_002` |

---

## 7. 매체 지역 목록 API

ACTIVE 매체 데이터에 실제로 존재하는 시/도와 시/군/구만 반환한다. 프론트가 대한민국 전체 지역
코드를 별도로 하드코딩하지 않아도 된다.

### Request

```http
GET /api/v1/media-units/regions
Authorization: Bearer {accessToken}
```

### Response

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "regions": [
      {
        "sido": "서울특별시",
        "sigungu": ["강남구", "서초구", "송파구"]
      },
      {
        "sido": "부산광역시",
        "sigungu": ["해운대구"]
      }
    ]
  }
}
```

시/도와 시/군/구는 각각 오름차순 정렬하고 중복을 제거한다. 등록된 ACTIVE 매체가 없으면
`regions: []`다.

---

## 8. 캠페인 최종 등록 API

1~2단계에서 프론트가 보관한 값과 소재 업로드 토큰을 한 번에 보내 캠페인을 생성한다.

### Request

```http
POST /api/v1/teams/{teamId}/campaigns
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "creativeToken": "v1.eyJwdXJwb3NlIjoiY2FtcGFpZ24tY3JlYXRpdmUiLC4uLn0.signature",
  "campaignName": "0711 나이키 썸머 프로모션 홍보 영상",
  "brandName": "나이키 코리아",
  "executionStartDate": "2026-07-11",
  "executionEndDate": "2026-07-12",
  "dailyTargetPlayCount": 100,
  "description": "브랜드 인지도 확대를 주요 목표로 설정",
  "mediaUnitId": 12
}
```

### Request Fields

| 필드 | 필수 | 타입 | 설명 |
|---|---:|---|---|
| `creativeToken` | Y | string | 4절 업로드 URL API가 발급한 서명 토큰 |
| `campaignName` | Y | string | 캠페인명, 최대 200자 |
| `brandName` | Y | string | 브랜드명, 최대 200자 |
| `executionStartDate` | Y | date | 집행 시작일, KST 달력 날짜 |
| `executionEndDate` | Y | date | 집행 종료일, KST 달력 날짜 |
| `dailyTargetPlayCount` | Y | integer | 하루 목표 송출 횟수, 1 이상, 별도 상한 없음 |
| `description` | N | string | 메모, 최대 2000자 |
| `mediaUnitId` | Y | number | 선택한 ACTIVE 매체 ID |

캠페인명·브랜드명·메모는 앞뒤 공백을 제거한다. 캠페인명과 브랜드명은 정규화 후 빈 문자열을
거부하고, 빈 메모는 `null`로 저장한다. 캠페인명 중복은 허용한다.

### creativeToken 검증

트랜잭션 시작 전에 다음을 검증한다.

1. 토큰 버전과 `purpose = campaign-creative`
2. HMAC 서명
3. UTC 기준 만료 시각
4. 토큰의 `uploaderId`와 JWT `sub` 일치
5. `s3Key`가 `campaign-creatives/{uploaderId}/` prefix에 속함
6. `creativeType`이 `IMAGE` 또는 `VIDEO`

검증 성공 후 토큰에서 `creativeType`, `s3Key`, `originalFilename`을 꺼내 DB에 저장한다. 토큰 원문은
DB에 저장하지 않고, S3 객체 확인도 하지 않는다.

### 처리 순서

0. 트랜잭션 시작 전에 요청값과 `creativeToken`을 검증한다.
1. `teams` 행을 `PESSIMISTIC_WRITE`로 잠근다.
2. 팀이 `ACTIVE`인지, 요청자가 해당 팀의 ACTIVE `OWNER`/`ADMIN`인지 확인한다.
3. `media_units` 행을 `PESSIMISTIC_WRITE`로 잠근다.
4. 매체 상태가 `ACTIVE`인지 확인한다.
5. 같은 매체에 기간이 겹치는 `REGISTRATION_FAILED` 외 캠페인이 있는지 조회한다.
6. 충돌이 없으면 `campaigns` 행을 `REGISTERED` 상태로 저장한다.

잠금 순서는 항상 `teams -> media_units`다. 같은 매체에 서로 다른 팀이 동시에 캠페인을 등록해도
3단계의 매체 잠금에서 직렬화되며, 뒤 요청은 앞 요청이 커밋한 캠페인을 보고 `409`가 된다.

`MediaUnitRepository`에는 `TeamRepository.findByIdForUpdate()`와 동일한 방식의 잠금 조회를
명시적으로 추가한다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select m from MediaUnit m where m.id = :id")
Optional<MediaUnit> findByIdForUpdate(@Param("id") Long id);
```

캠페인 등록 처리 1~6 중 실패하면 해당 캠페인 등록 트랜잭션 전체를 롤백한다. 이는 11절의 SQS
매체별 독립 트랜잭션과 별개의 규칙이다. S3에 먼저 업로드된 파일은 롤백되지 않으며 orphan 정리
대상이다.

### Response

HTTP 상태는 `201 Created`다.

```json
{
  "isSuccess": true,
  "code": "COMMON_201_001",
  "message": "리소스가 성공적으로 생성되었습니다.",
  "result": {
    "campaignId": 31,
    "teamId": 7,
    "campaignName": "0711 나이키 썸머 프로모션 홍보 영상",
    "status": "REGISTERED",
    "creativeType": "VIDEO",
    "creativeUrl": "https://cdn.example.com/campaign-creatives/42/550e8400-e29b-41d4-a716-446655440000",
    "mediaUnitId": 12
  }
}
```

### 에러 케이스

| 상황 | HTTP / 코드 |
|---|---|
| 필수값 누락, 종료일이 시작일보다 빠름, 횟수 0 이하 | `400 / CAMPAIGN_400_001` |
| creativeToken 서명·만료·업로더·purpose 오류 | `400 / CAMPAIGN_400_002` |
| 요청자가 팀의 ACTIVE 멤버가 아님 | `403 / TEAM_403_001` |
| 요청자가 MEMBER임 | `403 / TEAM_403_003` |
| 팀이 없음 | `404 / TEAM_404_001` |
| 매체가 없음 | `404 / MEDIA_404_001` |
| 팀 또는 매체가 ACTIVE가 아님 | `409 / TEAM_409_001` 또는 `MEDIA_409_001` |
| 동일 매체의 캠페인 기간 충돌 | `409 / CAMPAIGN_409_001` |

프론트가 6절에서 `available = true`를 확인했더라도 최종 API는 기간 충돌을 다시 검사한다. 목록 조회와
최종 클릭 사이에 다른 사용자가 먼저 등록할 수 있기 때문이다.

---

## 9. 캠페인 상태 전이

최종 등록 API가 만든 최초 상태는 항상 `REGISTERED`다.

```text
REGISTERED
  -> BEFORE_EXECUTION
  -> IN_EXECUTION
  -> AFTER_EXECUTION

REGISTRATION_FAILED
```

별도 스케줄러가 1분마다 현재 KST 날짜와 집행 기간을 비교해 상태를 보정한다.

| 조건 | 상태 |
|---|---|
| `today < executionStartDate` | `BEFORE_EXECUTION` |
| `executionStartDate <= today <= executionEndDate` | `IN_EXECUTION` |
| `today > executionEndDate` | `AFTER_EXECUTION` |

따라서 `REGISTERED`는 생성 직후부터 다음 상태 보정 실행 전까지의 짧은 중간 상태다. 과거 기간으로
등록하면 `REGISTERED -> AFTER_EXECUTION`으로 바뀐다.

현재는 외부 매체 등록 작업이 없으므로 이 API에서 `REGISTRATION_FAILED`가 만들어지지 않는다.
향후 외부 등록 연동 또는 Mock 실패 처리를 추가할 때 사용할 예약 상태다. 일반 DB 예외는 실패 행을
남기지 않고 트랜잭션 전체를 롤백한다.

---

## 10. DB 마이그레이션 요구사항

다음 신규 마이그레이션에서 반영한다. 기존 V1~V6 파일은 수정하지 않는다.

### 적용 전 데이터 확인

아래 조회 결과가 모두 0인지 먼저 확인한다. `campaign_count`는 신규 소재 `NOT NULL` 컬럼을 바로
추가할 수 있는지 확인하기 위한 값이다.

```sql
SELECT COUNT(*) AS campaign_count FROM campaigns;
SELECT COUNT(*) FROM campaigns WHERE media_unit_id IS NULL;
SELECT COUNT(*) FROM media_units
WHERE sido IS NULL OR sigungu IS NULL OR latitude IS NULL OR longitude IS NULL;
```

0이 아니라면 임의 위치나 임의 매체로 채우지 않는다. 실제 값으로 백필하거나 해당 테스트 데이터를
정리한 뒤 마이그레이션한다. 현재 운영 데이터가 없다는 전제에서는 바로 `NOT NULL`을 적용한다.

### campaigns 소재 컬럼 및 매체 필수 참조

```sql
ALTER TABLE campaigns
    ADD COLUMN creative_type VARCHAR(20) NOT NULL AFTER description,
    ADD COLUMN creative_storage_key VARCHAR(1024) NOT NULL AFTER creative_type,
    ADD COLUMN creative_original_filename VARCHAR(255) NOT NULL AFTER creative_storage_key,
    MODIFY COLUMN media_unit_id BIGINT UNSIGNED NOT NULL;
```

기존 `image_url`은 대시보드 호환을 위해 nullable legacy 컬럼으로 남긴다. 신규 등록 API는 값을 넣지
않는다. 기존 캠페인 행이 있는 환경은 소재 컬럼을 nullable로 추가 -> 백필 -> `NOT NULL` 적용의
단계적 마이그레이션이 필요하다.

`Campaign.mediaUnit`의 `@JoinColumn`에도 `nullable = false`를 지정하고 생성 메서드에서 매체를
필수로 받는다. 매체를 나중에 연결하는 기존 `assignMediaUnit()`은 삭제한다.

### media_units 필수 위치 컬럼

```sql
ALTER TABLE media_units
    MODIFY COLUMN sido VARCHAR(20) NOT NULL,
    MODIFY COLUMN sigungu VARCHAR(50) NOT NULL,
    MODIFY COLUMN latitude DECIMAL(10, 7) NOT NULL,
    MODIFY COLUMN longitude DECIMAL(10, 7) NOT NULL;
```

`MediaUnit` 엔티티의 네 필드에도 `nullable = false`를 지정한다. API 검증과 DB 제약을 동일하게
유지해 관리 API 이외의 저장 경로에서도 불완전한 매체가 만들어지지 않게 한다.

### media_units 장비 코드 유니크 제거

```sql
ALTER TABLE media_units
    DROP INDEX uk_media_units_board_code,
    DROP INDEX uk_media_units_device_code;

CREATE INDEX ix_media_units_vision_codes_status
    ON media_units (board_code, device_code, status);
```

JPA의 `boardCode`/`deviceCode` `unique = true`도 제거한다. DB 컬럼은 NOT NULL을 유지한다.

### 기간 충돌 조회 인덱스

기존 인덱스를 그대로 사용한다.

```text
ix_campaigns_media_unit_status
(media_unit_id, status, execution_start_date, execution_end_date)
```

MySQL은 일반 CHECK 제약만으로 다른 행과의 기간 겹침을 막을 수 없으므로, 애플리케이션이 매체 행을
잠근 뒤 충돌 조회를 수행한다.

### 에러 코드 추가

| enum | HTTP | 코드 |
|---|---:|---|
| `INVALID_CAMPAIGN_REQUEST` | 400 | `CAMPAIGN_400_001` |
| `INVALID_CREATIVE_TOKEN` | 400 | `CAMPAIGN_400_002` |
| `CAMPAIGN_PERIOD_CONFLICT` | 409 | `CAMPAIGN_409_001` |
| `MEDIA_UNIT_NOT_FOUND` | 404 | `MEDIA_404_001` |
| `MEDIA_UNIT_NOT_ACTIVE` | 409 | `MEDIA_409_001` |

기존 대시보드 전용 `DASHBOARD_400_001`과 혼용하지 않는다.

---

## 11. SQS Vision fan-out 변경

현재 `MediaUnitRepository.findByBoardCodeAndDeviceCode()`는 한 매체를 반환하지만 공용 장비 정책에서는
여러 매체를 반환해야 한다.

```java
List<MediaUnit> findAllByBoardCodeAndDeviceCodeAndStatus(
        String boardCode,
        String deviceCode,
        MediaUnitStatus status
);
```

메시지 처리 순서는 다음과 같다.

1. JSON Schema와 고정 `board_id`/`device_id`를 검증한다.
2. 코드가 일치하는 모든 ACTIVE 매체를 조회한다.
3. `timestamp`를 KST 날짜로 변환한다.
4. 각 매체에서 해당 날짜와 겹치는 캠페인을 조회한다.
5. 캠페인이 정확히 하나면 해당 `media_unit_id`, `campaign_id`로 Vision 행을 독립 트랜잭션에서 저장한다.
6. 캠페인이 없으면 해당 매체는 정상 건너뛰기로 처리한다.
7. 캠페인이 2개 이상이면 해당 매체를 모호한 데이터로 건너뛰고 ERROR 로그와 모니터링 알림을 남긴다.
8. 모든 매체의 처리 결과를 모아 SQS 메시지 ACK 여부를 결정한다.

현재 `VisionSummaryIngestService.ingest()`의 `boolean` 반환값은 fan-out의 부분 성공을 표현할 수
없으므로 저장·중복·건너뜀·실패 건수를 담는 `VisionFanOutResult` 형태로 변경한다. SQS Consumer는
이 결과의 `hasRetryableFailure`를 보고 `DeleteMessage` 호출 여부를 결정한다. 현재
`resolveCampaign()`의 "WARN 후 `candidates.get(0)` 사용" 로직은 제거한다.

### 매체별 독립 트랜잭션

fan-out 전체를 하나의 DB 트랜잭션으로 묶지 않는다. Vision 저장 전용 메서드를 별도 Spring Bean으로
분리하고 매체 하나마다 `@Transactional(propagation = Propagation.REQUIRES_NEW)`를 적용한다. 따라서
한 매체의 저장 실패가 이미 성공한 다른 매체의 저장을 롤백하지 않는다. 외부 반복 메서드에는
`@Transactional`을 붙이지 않으며, 같은 클래스 내부 호출로 `REQUIRES_NEW` 프록시가 우회되지 않게 한다.

매체별 내부 처리 결과는 다음 다섯 상태로 구분한다.

| 결과 | 의미 | 재수신 필요 |
|---|---|---:|
| `SAVED` | 새 Vision 행 저장 완료 | N |
| `DUPLICATE` | `(media_unit_id, event_time)`이 이미 존재함 | N |
| `NO_CAMPAIGN` | 이벤트 날짜에 캠페인이 없음 | N |
| `AMBIGUOUS_CAMPAIGN` | 같은 매체와 날짜에 캠페인이 2개 이상임 | N |
| `RETRYABLE_FAILURE` | DB 연결·타임아웃 등 일시적 인프라 오류 | Y |

`DUPLICATE`는 SQS의 at-least-once 전달에서 발생하는 정상 멱등 처리다. `NO_CAMPAIGN`은 MVP의
백필 없음 정책에 따라 이후 캠페인이 등록되더라도 다시 귀속하지 않는다. `AMBIGUOUS_CAMPAIGN`은
재시도해도 자동으로 해결되지 않으므로 해당 매체에는 임의의 첫 번째 캠페인을 선택하지 않는다.
다른 정상 매체 처리는 계속한다.

### SQS ACK 규칙

- 모든 매체 결과가 `SAVED`, `DUPLICATE`, `NO_CAMPAIGN`, `AMBIGUOUS_CAMPAIGN` 중 하나면
  `DeleteMessage`를 호출해 ACK한다.
- 하나라도 `RETRYABLE_FAILURE`이면 `DeleteMessage`를 호출하지 않는다.
- JSON 파싱 실패, 고정 board/device 불일치, ACTIVE 매체 매핑 0건처럼 메시지 또는 설정 자체가
  잘못된 경우도 ACK하지 않고 SQS redrive policy에 따라 DLQ로 보낸다.

예를 들어 A 저장 성공, B DB 오류, C 저장 성공이면 A와 C는 커밋된 상태로 남고 메시지는 ACK하지
않는다. 다음 수신에서 A와 C는 `DUPLICATE`, B는 `SAVED`가 되며 그때 메시지를 ACK한다. 이 때문에
부분 성공을 허용하더라도 `(media_unit_id, event_time)` 유니크 키는 반드시 유지한다.

매체 N개에 동시에 캠페인이 있으면 원본 메시지 한 건당 최대 N개 행이 생긴다. 캠페인이 없는 매체를
제외해 불필요한 데이터 증가를 줄인다.

---

## 12. 구현 체크리스트

### 캠페인 소재

- S3 키는 서버 UUID로 생성하고 사용자 입력 파일명을 경로에 사용하지 않는다.
- `campaign-creatives/*`는 public-read이고 익명 쓰기·수정·삭제는 차단한다.
- Presigned PUT과 `creativeToken`은 1시간 후 만료된다.
- `CAMPAIGN_CREATIVE_PUBLIC_BASE_URL`과 S3 키로 공개 `creativeUrl`을 생성한다.
- HMAC secret은 JWT 키와 분리한다.
- 토큰에는 purpose, 키, 업로더, 소재 타입, 파일명, 만료 시각을 포함한다.
- S3 객체 존재·파일 바이트·메타데이터는 검사하지 않는다.
- 미참조 `campaign-creatives/` 객체는 최소 2일 후 정리하는 배치를 운영 보완사항으로 둔다.

### 매체

- 관리자 등록 POST만 SecurityConfig에서 `permitAll` 처리한다.
- GET 목록/지역 API는 인증을 유지한다.
- 매체 등록 시 공용 board/device 코드를 서버가 주입한다.
- `sido`, `sigungu`, `latitude`, `longitude`를 DB와 엔티티 모두 필수로 만든다.
- 위·경도, 물리 규격, 해상도, 형태 배열을 모두 응답한다.
- 목록 검색에서 `%`, `_`를 일반 문자로 처리한다.
- 기간 가용성 조회와 최종 등록이 동일한 겹침 공식을 사용한다.

### 캠페인

- 팀 행 잠금 후 멤버십·역할을 다시 확인한다.
- `MediaUnitRepository.findByIdForUpdate()`를 추가해 매체 행을 비관적 쓰기 잠금으로 조회한다.
- 매체 행 잠금 후 상태·기간 충돌을 다시 확인한다.
- 잠금 순서를 `teams -> media_units`로 통일한다.
- `campaigns.media_unit_id`와 엔티티 연관관계를 필수로 만들고 `assignMediaUnit()`을 제거한다.
- creativeToken에서 꺼낸 순수 S3 키만 DB에 저장하고 응답 URL은 base URL과 키로 만든다.
- 생성 상태는 REGISTERED이고 REGISTRATION_FAILED는 이 API에서 만들지 않는다.
- 과거 캠페인 등록 시 Vision 데이터 백필을 수행하지 않는다.

### SQS

- board/device 유니크 가정을 제거하고 List 매핑으로 변경한다.
- 캠페인이 있는 매체에만 동일 메시지를 fan-out한다.
- 매체별 저장을 별도 Bean의 `REQUIRES_NEW` 트랜잭션으로 실행한다.
- 매체·이벤트 시각 유니크 키로 중복을 정상 처리한다.
- 한 매체에 캠페인이 2개 이상이면 임의 선택하지 않고 영구 건너뛰기로 분류한다.
- 하나라도 재시도 가능한 실패가 있으면 ACK하지 않고, 그 외 결과만 있으면 ACK한다.

---

## 13. 필수 테스트

### 업로드 URL/토큰

- IMAGE/VIDEO 각각 정상 발급
- 인증 없음 401
- Presigned PUT 업로드 후 `creativeUrl` 익명 GET 성공
- Presigned URL 없는 익명 PUT·DELETE 거부
- 토큰 정상, 서명 위변조, 만료, 다른 사용자, 잘못된 purpose/prefix
- 실제 S3 객체가 없어도 유효 토큰이면 최종 등록되는 MVP 정책 확인

### 매체 등록/조회

- 인증 없이 등록 성공 및 `201`
- 고정 board/device와 ACTIVE 상태 저장
- 여러 매체가 같은 board/device로 등록 가능
- 필수값, 숫자 범위, 형태 배열 검증
- `sido`, `sigungu`, `latitude`, `longitude` NULL 저장이 DB 제약으로 거부됨
- 키워드, 시/도, 시/군/구, 복합 필터와 정렬
- 기간 충돌 available false, 경계 날짜가 닿는 경우도 충돌
- 등록 매체 없음 빈 배열

### 캠페인 최종 등록

- OWNER/ADMIN 성공, MEMBER 403, 미소속 403
- 비활성 팀·매체 409, 없는 팀·매체 404
- 이미지/영상 토큰 각각 성공
- 날짜 역전, 횟수 0, 문자열 길이 초과
- 동일 매체 비겹침 성공, 겹침 409
- 서로 다른 팀의 같은 매체 동시 등록에서 하나만 성공
- `media_unit_id = null` 저장이 DB 제약으로 거부됨
- 캠페인 등록 실패 시 해당 캠페인 등록 트랜잭션의 row 전체 롤백
- 과거·오늘·미래 시작일 모두 허용

### 상태와 SQS

- REGISTERED에서 날짜별 BEFORE/IN/AFTER 전이
- 공용 메시지가 동시에 캠페인이 있는 여러 매체에 복제 저장
- 캠페인 없는 매체에는 저장하지 않음
- SQS 중복 수신 시 행이 늘지 않고 ACK
- A·C 저장 성공/B 저장 실패 시 A·C 커밋 유지 및 미ACK
- 재수신 시 A·C 중복 처리, B 저장 성공 후 ACK
- 한 매체에 캠페인이 2개 이상이면 그 매체만 미저장하고 다른 매체 처리 후 ACK
- ACTIVE 매체 매핑 0건과 파싱 실패는 미ACK 후 DLQ 이동

---

## 14. 추후 범위

- 캠페인 Draft/자동 저장
- 실제 파일 검증, 썸네일, 트랜스코딩, 광고 송출
- 캠페인 수정·삭제·재등록
- 관리자 인증 및 매체 수정·비활성화 API
- 실제 매체별 Vision 장비 분리
- 공용 Vision 원본을 한 번만 저장하는 source/assignment 정규화
- 캠페인 간 합산 시 동일 Vision 원본 중복 제거
