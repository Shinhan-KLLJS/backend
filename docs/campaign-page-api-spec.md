# 캠페인 페이지 API SPEC

## 1. 목적과 범위

팀의 "캠페인 페이지"(목록 조회 화면)에 필요한 API를 정의한다.

1. 팀 캠페인 목록 조회 (이름/집행일 정렬, 상태 필터, 캠페인명 검색)
2. 캠페인 상세정보 보기 (팝업)
3. 캠페인 삭제

화면에 있는 "리포트 추출" 버튼은 MVP 범위가 아니다. 캠페인 수정·재등록 API도 이번 범위가
아니다 — `campaign-registration-api-spec.md` 14절에서 이미 추후 범위로 남겨뒀고, 이번 문서는
그중 "삭제"만 앞당겨 다룬다.

이 문서는 `campaign-registration-api-spec.md`(등록)와 `docs/home-dashboard-api-spec.md`(홈
대시보드)에 이미 있는 `Campaign`/`MediaUnit` 엔티티와 상태 모델을 그대로 재사용한다 — 새
테이블이나 컬럼 타입 변경은 없다.

---

## 2. 확정된 설계

### 2-1. 목록의 "금일/누적 송출" 두 숫자

화면 예시 "12/200"에서:

- **금일(첫 번째 숫자)**: 오늘 하루 동안의 추정 송출 횟수. `DashboardCampaignDeliveryService`가
  이미 쓰고 있는 계산(06:00부터 15초 간격, 다운타임 없다고 가정)과 완전히 같은 공식이다.
  집행 시작 전이면 0, 집행 기간이 이미 끝난 뒤(`AFTER_EXECUTION`)라면 그 캠페인의
  `dailyTargetPlayCount`와 같은 값이 된다(과거 날짜는 항상 목표를 100% 채웠다고 가정하는
  기존 로직 그대로).
- **누적(두 번째 숫자)**: 그 캠페인 등록 시 설정한 하루 목표 송출 횟수
  (`campaigns.daily_target_play_count`)다. 계산값이 아니라 저장된 값을 그대로 보여주는
  것이라 상태와 무관하게 항상 같은 값이다.

이름은 "금일/누적"이지만 실제로는 "오늘 추정치 / 하루 목표치"다. 집행 완료된 캠페인이
"123/123"처럼 분자·분모가 같아 보이는 이유도 이 정의로 설명된다(과거 날짜는 항상
목표만큼 채운 것으로 추정하므로).

기존 `DashboardCampaignDeliveryResponse`의 `isEstimated = true`와 같은 이유로, 이 값도 실제
송출 로그가 아니라 추정치라는 사실은 동일하다.

### 2-2. 캠페인 삭제 = 하드 삭제 (DB row 완전 제거)

MVP 단순성을 우선해서 하드 삭제로 확정한다. `campaignRepository.delete(campaign)`(또는
`deleteById`) 한 번이면 되고, `Campaign` 엔티티에 `vision_summary_5s`로의 `@OneToMany` 매핑이
없으므로 애플리케이션에서 별도 cascade 처리도 필요 없다.

**받아들이는 트레이드오프**: `vision_summary_5s.campaign_id`는 `ON DELETE SET NULL`이라 그
캠페인이 이미 모아둔 Vision 측정 데이터 자체는 지워지지 않지만, 삭제하는 순간
`campaign_id`가 `NULL`로 바뀌어 그 데이터가 어느 캠페인 것이었는지 다시는 알 수 없게 된다
(캠페인 실적 조회/리포트 기능이 지금은 아예 없어서 당장 실질적인 영향은 없다 - 나중에
그런 기능을 만들 때는 삭제된 캠페인의 과거 데이터는 애초에 대상이 될 수 없다는 점을
전제해야 한다). 이 결정은 되돌릴 수 없다 — "삭제 취소(복원)" 기능은 만들 수 없다(12절).

삭제는 상태(집행 전/중/완료)와 무관하게 항상 허용한다(화면 목업과 일치). 권한은 캠페인
등록과 동일하게 해당 팀의 ACTIVE `OWNER`/`ADMIN`만 가능하다고 가정한다 — `MEMBER`가 등록은
못 하는데 삭제는 되는 건 이상하므로. (이 부분만 제가 임의로 정했습니다 — 다르게 가야
하면 알려주세요.)

**에러 코드 정정**: 이전 버전에서 이 상황에 `TEAM_403_002`(`TEAM_MANAGEMENT_FORBIDDEN`,
"팀원을 관리할 권한이 없습니다")를 재사용하려고 했는데, 이 코드는 팀원 역할 변경/강퇴
전용이라 메시지가 상황과 안 맞는다. `TeamErrorCode`에 아래 값을 새로 추가해야 한다(아직
코드에는 없음, 문서만 먼저 반영):

```java
CAMPAIGN_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "TEAM_403_003", "캠페인을 등록하거나 삭제할 권한이 없습니다.")
```

`campaign-registration-api-spec.md`의 캠페인 등록 API도 같은 상황(`MEMBER`가 등록 시도)에
같은 코드를 써야 하므로 그 문서도 같이 고쳤다.

### 2-3. 필터·정렬 매핑

| 화면 탭/옵션 | 내부 조건 |
|---|---|
| 전체 | 이 팀 소유 캠페인 전체 (`REGISTRATION_FAILED`는 아직 이 시스템에서 만들어지지 않는 상태라 사실상 안 나타남 - "확인 필요 항목" 참고) |
| 집행 전 | `REGISTERED`, `BEFORE_EXECUTION` |
| 집행 중 | `IN_EXECUTION` |
| 집행 완료 | `AFTER_EXECUTION` |
| 이름순 | `campaignName` 오름차순(가나다순) |
| 집행 최신순 | `executionStartDate` 내림차순 |
| 집행 오래된순 | `executionStartDate` 오름차순 |

"집행 전"에 `REGISTERED`도 같이 묶은 이유: `REGISTERED`는 등록 직후부터 1분 주기 스케줄러가
보정하기 전까지의 짧은 중간 상태라(`campaign-registration-api-spec.md` 9절), 사용자 눈에는
어차피 "아직 시작 안 한 캠페인"으로 보이는 게 자연스럽다.

삭제된 캠페인은 하드 삭제라 테이블에 아예 없으므로, 이 필터 로직에 "삭제 제외" 조건을
추가할 필요가 없다 — 존재하지 않는 행은 어떤 조건에도 걸리지 않는다.

---

## 3. API 목록

| 기능 | HTTP | 엔드포인트 | 인증 |
|---|---|---|---|
| 팀 캠페인 목록 조회 | GET | `/api/v1/teams/{teamId}/campaigns` | 팀 ACTIVE 멤버 |
| 캠페인 상세정보 조회 | GET | `/api/v1/teams/{teamId}/campaigns/{campaignId}` | 팀 ACTIVE 멤버 |
| 캠페인 삭제 | DELETE | `/api/v1/teams/{teamId}/campaigns/{campaignId}` | OWNER/ADMIN |

`campaign-registration-api-spec.md`의 `POST /api/v1/teams/{teamId}/campaigns`와 같은 경로
체계를 따른다. 기존 `GET /api/v1/dashboard/campaigns`(홈 대시보드용)와는 별개의 API다 —
응답 필드가 다르고(팀명, 매체 주소, 금일/누적 송출 등 홈 대시보드엔 없는 필드가 필요),
용도도 다르다(팀 관리 화면 vs 홈 대시보드 기본 선택).

---

## 4. 팀 캠페인 목록 조회 API

### Request

```http
GET /api/v1/teams/{teamId}/campaigns
  ?status=IN_EXECUTION
  &keyword=나이키
  &sort=NAME
Authorization: Bearer {accessToken}
```

| 파라미터 | 필수 | 설명 |
|---|---:|---|
| `status` | N | `BEFORE_EXECUTION`\|`IN_EXECUTION`\|`AFTER_EXECUTION`. 없으면 전체. `BEFORE_EXECUTION`을 넘기면 `REGISTERED`도 같이 포함해서 조회한다(2-3절) |
| `keyword` | N | 캠페인명 부분 일치, 대소문자 무시, 최대 100자 |
| `sort` | N | `NAME`(기본값)\|`EXECUTION_RECENT`\|`EXECUTION_OLDEST` |

접근 권한: 팀이 없으면 `404`, 요청자가 이 팀에 `ACTIVE` 상태로 속해 있지 않으면 `403` —
역할 제한은 없다(`MEMBER`도 조회 가능, 기존 캠페인 목록 조회와 동일한 원칙).

### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `teamName` | string | 화면 상단 팀명 표시용 |
| `campaigns[].campaignId` | number | |
| `campaigns[].campaignName` | string | |
| `campaigns[].status` | string | DB 상태를 그대로 반환한다: `REGISTRATION_FAILED`\|`REGISTERED`\|`BEFORE_EXECUTION`\|`IN_EXECUTION`\|`AFTER_EXECUTION`. 프론트는 등록 직후의 짧은 `REGISTERED`를 "집행 전"과 같은 배지로 표시하면 된다 (`REGISTRATION_FAILED`는 현재 등록 흐름에서는 생성되지 않음) |
| `campaigns[].executionStartDate` | date | |
| `campaigns[].executionEndDate` | date | |
| `campaigns[].mediaLocationAddress` | string | 선택한 매체의 `locationAddress` |
| `campaigns[].todayPlayCount` | number | 2-1절 "금일" |
| `campaigns[].dailyTargetPlayCount` | number | 2-1절 "누적" |

### Response Example

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "teamName": "신한 KLLJS 딥비전스 옥외 광고 3팀",
    "campaigns": [
      {
        "campaignId": 31,
        "campaignName": "나이키 썸머 프로젝트 2026 07 ~ 08",
        "status": "IN_EXECUTION",
        "executionStartDate": "2026-07-11",
        "executionEndDate": "2026-08-15",
        "mediaLocationAddress": "서울특별시 강남구 테헤란로 1123",
        "todayPlayCount": 12,
        "dailyTargetPlayCount": 200
      }
    ]
  }
}
```

페이지네이션은 없다 — 팀당 캠페인 수가 많지 않을 것으로 가정한 기존 캠페인 목록 조회
(`DashboardCampaignQueryService`)와 동일한 전제다.

### 에러 케이스

| 상황 | HTTP / 코드 |
|---|---|
| 팀이 없음 | `404 / TEAM_404_001` |
| 요청자가 이 팀 소속이 아님 | `403 / TEAM_403_001` |

---

## 5. 캠페인 상세정보 조회 API

캠페인 등록 3단계 "최종 확인" 화면과 완전히 같은 내용을 그대로 보여준다. 기존
`GET /api/v1/dashboard/campaigns/{campaignId}`(`DashboardCampaignDetailResponse`)는 매체 조인
정보(매체명/주소/규격/해상도/형태)와 소재 미리보기(`creativeUrl`)가 없어서 재사용할 수
없다 — 그 API는 기간별 조회(홈 대시보드용)가 목적이라 응답 모양 자체가 다르다.

### Request

```http
GET /api/v1/teams/{teamId}/campaigns/{campaignId}
Authorization: Bearer {accessToken}
```

### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `campaignId` | number | |
| `campaignName` | string | |
| `brandName` | string | |
| `executionStartDate` | date | |
| `executionEndDate` | date | |
| `dailyTargetPlayCount` | number | |
| `description` | string/null | 메모 |
| `creativeType` | string | `IMAGE`\|`VIDEO` |
| `creativeUrl` | string | 공개 조회 URL (`campaign-registration-api-spec.md` 4절) |
| `mediaUnitId` | number | |
| `mediaName` | string | |
| `mediaPhotoUrl` | string | 매체 사진 URL |
| `mediaLocationAddress` | string | |
| `mediaWidthMm` / `mediaHeightMm` | number | "규격" |
| `mediaResolutionWidthPx` / `mediaResolutionHeightPx` | number | "해상도" |
| `mediaShapeTypes` | array | "형태" 태그 |

### Response Example

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 31,
    "campaignName": "0711 나이키 썸머 프로모션 홍보 영상",
    "brandName": "나이키 코리아",
    "executionStartDate": "2026-07-11",
    "executionEndDate": "2026-07-12",
    "dailyTargetPlayCount": 100,
    "description": "브랜드 인지도 확대를 주요 목표로 설정",
    "creativeType": "VIDEO",
    "creativeUrl": "https://cdn.example.com/campaign-creatives/42/550e8400-e29b-41d4-a716-446655440000",
    "mediaUnitId": 12,
    "mediaName": "파르나스 미디어타워 전광판",
    "mediaPhotoUrl": "https://cdn.example.com/media-units/12.jpg",
    "mediaLocationAddress": "서울 강남구 영동대로 513",
    "mediaWidthMm": 81000,
    "mediaHeightMm": 20000,
    "mediaResolutionWidthPx": 1215,
    "mediaResolutionHeightPx": 1792,
    "mediaShapeTypes": ["FLAT", "VERTICAL"]
  }
}
```

### 에러 케이스

| 상황 | HTTP / 코드 |
|---|---|
| 팀이 없음 | `404 / TEAM_404_001` |
| 요청자가 이 팀 소속이 아님 | `403 / TEAM_403_001` |
| 캠페인이 없음(삭제됐거나 애초에 없음) 또는 이 팀 소유가 아님 | `404 / CAMPAIGN_404_001` |

---

## 6. 캠페인 삭제 API

### Request

```http
DELETE /api/v1/teams/{teamId}/campaigns/{campaignId}
Authorization: Bearer {accessToken}
```

(요청 본문 없음)

### 접근 권한

- 팀 없음 `404`, 요청자 미소속 `403`
- 요청자가 `MEMBER`면 `403` (등록과 동일한 권한 기준)

### 처리 순서

1. 캠페인을 조회한다. 없거나 이 팀 소유가 아니면 `404`.
2. `campaigns` row를 삭제한다(하드 삭제). `vision_summary_5s`에 이 캠페인을 참조하던 행이
   있으면 DB의 `ON DELETE SET NULL` 제약에 따라 그 행들의 `campaign_id`가 자동으로
   `NULL`로 바뀐다 - 애플리케이션이 별도로 처리할 것은 없다.

### Response

성공 시 `200`과 함께 `ApiResponse<Void>` JSON 본문을 반환한다. `result`는 null이므로 응답에서 생략된다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다."
}
```

### 에러 케이스

| 상황 | HTTP / 코드 |
|---|---|
| 팀이 없음 | `404 / TEAM_404_001` |
| 요청자가 이 팀 소속이 아님 | `403 / TEAM_403_001` |
| 요청자가 `MEMBER`임 | `403 / TEAM_403_003` |
| 캠페인이 없음(이미 삭제됐거나 애초에 없음) 또는 이 팀 소유가 아님 | `404 / CAMPAIGN_404_001` |

---

## 7. 기존 기능에 미치는 영향 — 없음

하드 삭제라 `campaigns` row 자체가 사라지므로, 기존 코드 어디에도 변경이 필요 없다.

- **상태 보정 스케줄러**: 존재하지 않는 행은 갱신 대상 쿼리에도 안 걸린다.
- **홈 대시보드 목록** (`DashboardCampaignQueryService`): 존재하지 않는 행은
  `findByTeamIdIn` 같은 조회에도 안 걸린다.
- **SQS Vision 귀속 쿼리** (`CampaignRepository.findActiveCampaignsForMediaUnit`): 존재하지
  않는 행은 매체+날짜 조건에도 안 걸린다. 삭제 후 그 매체·기간에 새로 들어오는 Vision
  메시지는 그냥 "캠페인 없음"으로 처리된다(기존에도 있던 정상 경로).

소프트 삭제(상태값 추가)였다면 이 세 곳 모두 "그 상태는 제외"하는 조건을 추가해야
했는데, 하드 삭제는 그럴 필요가 없다는 게 이 방식을 선택한 핵심 이유다.

---

## 8. DB 마이그레이션

**필요 없다.** 새 컬럼도, 새 상태값도, 새 테이블도 없다. 삭제 API는 기존 `campaigns` 테이블에
대한 평범한 `DELETE` 한 번이다.

---

## 9. 확인/결정 필요 항목

1. **삭제 권한을 OWNER/ADMIN으로 가정했습니다.** 화면 목업만으로는 이 권한 기준이 안
   보여서, 등록과 같은 기준을 그대로 가져왔습니다 — 다르게 가야 하면 알려주세요.
2. **`REGISTRATION_FAILED` 상태 캠페인의 "전체" 탭 포함 여부.** 현재는 이 API에서 이 상태가
   전혀 만들어지지 않아 실질적 영향은 없지만, 나중에 외부 등록 연동이 생겨 이 상태가
   실제로 나타나기 시작하면 "전체"에 포함할지 정해야 합니다.

---

## 10. 구현 체크리스트

- 삭제 API는 `campaignRepository.delete(campaign)`(또는 `deleteById`) 한 번으로 끝난다 —
  별도 상태값, 별도 필터 조건, cascade 코드 모두 불필요.
- 목록 API의 `todayPlayCount`는 매 요청마다 서버 시간 기준으로 새로 계산한다(저장된 값이
  아니라 조회 시점 실시간 추정치).
- 상세 조회 API는 `campaign-registration-api-spec.md`에서 확정된 `Campaign.mediaUnit`
  (NOT NULL 예정)과 `creative*` 컬럼을 그대로 조인해서 사용한다 — 그 마이그레이션이 먼저
  들어가 있어야 한다.

---

## 11. 필수 테스트

### 목록 조회

- 상태 필터 4종(전체/집행전/집행중/집행완료) 각각 올바른 캠페인만 반환
- 정렬 3종(이름순/집행 최신순/집행 오래된순)
- 캠페인명 키워드 검색
- `todayPlayCount`가 상태별로 올바르게 계산됨(집행 전 0, 집행 중 실시간 추정치, 집행 완료
  `dailyTargetPlayCount`와 동일)
- `dailyTargetPlayCount`는 상태와 무관하게 항상 저장된 값
- 팀 미소속 403, 팀 없음 404
- 캠페인이 하나도 없으면 빈 배열

### 상세 조회

- 캠페인/매체 필드가 정확히 응답에 포함됨
- 삭제됐거나 존재하지 않는 캠페인 조회 시 404
- 다른 팀 소유 캠페인 조회 시 404
- 팀 미소속 403

### 삭제

- OWNER/ADMIN 성공, MEMBER 403
- 삭제 후 `campaigns` row가 실제로 사라짐(재조회 시 404)
- 삭제 전에 그 캠페인을 참조하던 `vision_summary_5s` 행은 그대로 남아있되 `campaign_id`가
  `NULL`로 바뀜
- 삭제 후 홈 대시보드 목록에도 더 이상 나타나지 않음(별도 필터 없이 자연히 사라짐)
- 삭제된 캠페인의 매체·날짜와 겹치는 새 Vision 메시지가 와도 캠페인 없음으로 처리됨
- 이미 삭제된 캠페인을 다시 삭제 요청하면 404

---

## 12. 이번 범위에서 제외

- **캠페인 수정** — `campaign-registration-api-spec.md`에서부터 이어지는 범위 밖 항목.
- **캠페인 재등록** — 위와 동일.
- **삭제 취소(복원)** — 하드 삭제를 선택했으므로 나중에 추가로 만들 수 있는 기능이
  아니다. 삭제된 캠페인의 데이터는 영구적으로 복구할 수 없다.
- **리포트 추출** — 요청하신 대로 MVP 범위 아님.
