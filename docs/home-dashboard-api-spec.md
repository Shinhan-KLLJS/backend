# 홈 대시보드 API 명세

홈 화면 구현 전에 프론트엔드와 백엔드가 맞춰야 할 대시보드 조회 API 명세다. 현재 범위는 캠페인 선택, 기간 선택, 상단 송출정보, 5초 단위 실시간 그래프, 깔대기 그래프, 평균 시청시간, 성별·연령 시청 비율, 시간·연령별 노출도 대시보드까지로 한다.

---

## 0. 공통 규칙

### 기본 정보

| 항목 | 값 |
|---|---|
| Base URL | `{API_BASE_URL}` |
| 인증 | `Authorization: Bearer {accessToken}` |
| 응답 래퍼 | 기존 `ApiResponse<T>` 형식 사용 |
| 날짜 기준 | KST |
| 날짜 형식 | `yyyy-MM-dd` |
| 시간 형식 | ISO-8601 문자열 |
| 금액/비율 | 비율은 `%` 단위 숫자로 응답한다. 예: `15.0` = 15.0% |

### 응답 래퍼

모든 성공 응답은 아래 형식을 따른다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {}
}
```

### 요청 파라미터 네이밍

대시보드 조회 API의 query parameter는 프론트 초안과 맞춰 아래 snake_case를 사용한다.

| 파라미터 | 의미 |
|---|---|
| `campaign_id` | 캠페인 ID |
| `selected_start_date` | 사용자가 선택한 시작일 |
| `selected_end_date` | 사용자가 선택한 종료일 |

응답 body의 필드명은 기존 백엔드 응답 관례에 맞춰 camelCase를 사용한다.

### Vision 데이터 기준

홈 대시보드의 Vision 원천 데이터 기준은 `docs/v2-vision-summary-schema.json`이다. `docs/vision-summary-sqs-guide.md`는 구버전 가이드라 대시보드 구현의 기준으로 삼지 않는다.

v2 Vision Summary 기준 의미는 아래와 같이 고정한다.

| Vision 필드 | 대시보드 의미 | DB 저장 컬럼 |
|---|---|---|
| `ots_count` | 노출인구 | `vision_summary_5s.ots_count` |
| `lts_count` | 주목인구 | `vision_summary_5s.lts_count` |
| `ots_demographics` | 노출인구 성별·연령 분포 | `vision_summary_5s.ots_*` |
| `lts_demographics` | 주목인구 성별·연령 분포 | `vision_summary_5s.lts_*` |
| `attention.avg_dwell_sec` | 평균 시청시간 | `vision_summary_5s.avg_dwell_sec` |
| `attention.dwell_distribution` | 시청시간 구간 분포 | `vision_summary_5s.dwell_*` |

별도 `exposure_count` 필드는 사용하지 않는다. 노출인구는 v2 스키마의 `ots_count`다.

### 캠페인 상태값

홈 대시보드 API의 `campaign.status`는 아래 값을 기준으로 응답한다.

| 상태값 | 화면 문구 | 의미 |
|---|---|---|
| `REGISTRATION_FAILED` | 등록 실패 | 캠페인 등록 또는 외부 연동 등록에 실패한 상태 |
| `REGISTERED` | 등록 완료 | 캠페인 등록은 완료됐지만 아직 집행 상태로 전환되지 않은 상태 |
| `BEFORE_EXECUTION` | 집행 전 | 집행 시작일 이전 상태 |
| `IN_EXECUTION` | 집행 중 | 현재 KST 기준 시각이 집행 기간 안에 있는 상태 |
| `AFTER_EXECUTION` | 집행 후 | 집행 종료일 이후 상태 |

`DRAFT`는 사용하지 않는다 (임시저장·다단계 등록 같은 기획이 없어 `CampaignStatus` enum에서 제거했다). 캠페인은 항상 위 5개 상태 중 하나다.

### 접근 권한

`campaign_id`를 받는 모든 API는 공통으로 요청한 사용자가 해당 캠페인의 `team_id`에 **`ACTIVE` 상태로** 속해 있는지 확인한다. 캠페인이 존재하지 않으면 404, 존재하지만 현재 사용자의 팀 소속이 아니거나(LEFT/REMOVED 포함) 팀 자체가 다르면 403이다 (8절 에러 케이스 참고).

### 시간대 처리 규칙

- 프론트엔드는 날짜/기간을 KST 기준으로 요청한다.
- Vision AI가 보내는 `timestamp`는 UTC instant다. 예: `2026-07-07T07:43:25Z`
- 서버는 `timestamp`를 UTC instant 기준으로 해석하고 DB `vision_summary_5s.event_time`에도 UTC 기준 시각으로 저장한다.
- DB 컬럼이 `DATETIME(3)`/`LocalDateTime`처럼 timezone 정보를 직접 담지 않더라도, 애플리케이션 규칙상 `event_time`은 UTC로 간주한다.
- 조회 시 서버는 프론트의 KST 날짜 범위를 UTC instant 범위로 변환해서 DB를 조회한다.
- 응답의 `serverTime`, `eventTime`, `lastEventTime`, `nextPollAfter`, `aggregationCutoffTime`은 KST ISO-8601 문자열로 내려준다. 예: `2026-07-07T16:43:25+09:00`

예를 들어 프론트가 `selected_start_date=2026-07-07`, `selected_end_date=2026-07-07`로 요청하면 서버 조회 범위는 아래처럼 변환한다.

```text
KST: 2026-07-07T00:00:00+09:00 <= t < 2026-07-08T00:00:00+09:00
UTC: 2026-07-06T15:00:00Z      <= t < 2026-07-07T15:00:00Z
```
### 기간 처리 규칙

- `selected_start_date <= selected_end_date`여야 한다.
- 선택 기간은 캠페인 집행 기간과 겹치지 않아도 된다.
- 기간 범위를 받는 API는 응답에 `selectedPeriod`, `effectivePeriod`, `periodStatus`를 함께 내려준다.
- 캠페인 상세정보처럼 집행 기간 표시가 필요한 API는 `executionPeriod`도 함께 내려준다.
- `selectedPeriod`: 프론트가 요청한 기간
- `executionPeriod`: 캠페인 집행 기간
- `effectivePeriod`: 실제 집계에 사용한 기간. 집행 전 조회이면 `null`
- `periodStatus`: 선택 기간과 캠페인 집행 기간의 관계

| `periodStatus` | 판정 기준 | `effectivePeriod` | 응답 값 |
|---|---|---|---|
| `BEFORE_EXECUTION` | `selected_end_date < execution_start_date` | `null` | 집계형 숫자/비율 값은 `null`, 그래프 포인트는 빈 배열 |
| `IN_EXECUTION` | 선택 기간이 집행 기간과 하루 이상 겹침 | 선택 기간과 집행 기간의 교집합 | 해당 기간에 해당하는 값 |
| `AFTER_EXECUTION` | `selected_start_date > execution_end_date` | 전체 집행 기간 | 집행 기간 중 모든 데이터 |

선택 기간이 집행 전/후를 일부 포함하더라도 집행 기간과 겹치는 날짜가 있으면 `IN_EXECUTION`으로 본다. 예를 들어 캠페인 기간이 `2026-07-01` ~ `2026-07-31`이고 프론트가 `2026-06-30` ~ `2026-07-02`를 요청하면, `periodStatus`는 `IN_EXECUTION`, `effectivePeriod`는 `2026-07-01` ~ `2026-07-02`가 된다.
### 대시보드 조회/갱신 플로우

사용자가 캠페인 목록에서 특정 캠페인을 선택하면 프론트엔드는 선택된 `campaign_id`와 조회 기간을 기준으로 홈 화면의 각 대시보드 API를 호출한다.

기본 조회 기간은 오늘(KST)이다.

```text
selected_start_date = 오늘
selected_end_date = 오늘
```

프론트는 기본적으로 날짜 범위만 보내고, 현재 시각 기준으로 어디까지 집계할지는 서버가 API 성격에 맞춰 계산한다. 서버는 응답에 `aggregationCutoffTime` 또는 `lastEventTime`을 내려줘서 프론트가 화면에 표시된 데이터의 기준 시각을 알 수 있게 한다.

예를 들어 서버 시각이 `2026-07-07 16:43:25`라면:

| 대시보드 영역 | 갱신 주기 | 기준 시각 정책 |
|---|---:|---|
| 캠페인 송출정보 | 15초 | 현재 시각 기준. 15초 단위 증가 로직 또는 실제 송출 로그 기준 |
| 노출/주목 흐름 그래프 (오늘) | 5초 | 최신 SQS 저장 포인트 기준. `after_event_time` 커서 사용 (5-1절) |
| 노출/주목 흐름 그래프 (과거) | 재조회 불필요 | 1시간 단위 누적, 과거 데이터라 값이 안 바뀜 (5-2절) |
| 깔대기 그래프 | 1분 | 직전 완료 분까지. 예: `16:43:00`까지 |
| 평균 시청시간 | 1분 | 직전 완료 분까지. 예: `16:43:00`까지 |
| 성별·연령 시청 비율 | 1시간 | 직전 완료 시간까지. 예: `16:00:00`까지 |
| 시간·연령별 노출도 | 1시간 | 직전 완료 시간까지. 예: `16:00:00`까지 |

프론트 권장 호출 흐름:

1. `GET /api/v1/dashboard/campaigns`로 캠페인 목록 조회
2. 사용자가 캠페인 선택
3. `campaign_id`, `selected_start_date`, `selected_end_date`를 전역 대시보드 상태로 저장
4. 홈 화면 진입 또는 캠페인/기간 변경 시 모든 대시보드 API를 한 번 호출한다. 단, 노출/주목 흐름 그래프 카드는 5절의 조건에 따라 5-1/5-2 중 하나만 호출한다 (둘 다 호출하지 않는다)
5. 이후 각 API의 `refreshIntervalSec` 또는 `pollIntervalSec`에 맞춰 개별 갱신

기간을 사용자가 직접 바꾸면 모든 대시보드 API는 새 기간으로 다시 조회한다. 단, 실시간 그래프의 `after_event_time` 커서는 캠페인 또는 날짜가 바뀌면 초기화한다.

### 집계 기준 시각

| 필드 | 의미 |
|---|---|
| `serverTime` | 백엔드가 응답을 만든 현재 시각(KST) |
| `aggregationCutoffTime` | 집계에 포함된 데이터의 배타적 상한 시각. `event_time < aggregationCutoffTime`인 데이터까지 포함됐다는 뜻이며, 이 시각 자체의 데이터는 아직 미포함이다 |
| `lastEventTime` | 실시간 그래프 응답 포인트 중 가장 최신 이벤트 시각 |
| `refreshIntervalSec` | 프론트가 해당 API를 다시 호출하기를 권장하는 주기 |
| `pollIntervalSec` | 실시간 그래프처럼 polling 성격이 강한 API의 권장 호출 주기 |

오늘 데이터를 조회할 때도 API별 집계 단위가 다르므로, 모든 API가 같은 기준 시각을 쓰지 않는다. 깔대기 그래프와 평균 시청시간은 `16:43`에 호출하면 직전 완료 분(`16:42`~`16:43`)의 끝 시각인 `16:43:00`을 `aggregationCutoffTime`으로 응답하고, 1시간 단위 대시보드는 직전 완료 시간(`15:00`~`16:00`)의 끝 시각인 `16:00:00`을 `aggregationCutoffTime`으로 응답한다.
---

## 1. API 목록

| 기능명 | HTTP | 엔드포인트 | 설명 |
|---|---|---|---|
| 캠페인 목록 조회 | GET | `/api/v1/dashboard/campaigns` | 캠페인명 클릭 시 노출할 캠페인 목록 |
| 캠페인 상세정보 조회 | GET | `/api/v1/dashboard/campaigns/{campaign_id}` | 캠페인 클릭 또는 기간 선택 시 기본 상세정보 조회 |
| 캠페인 송출정보 조회 | GET | `/api/v1/dashboard/campaigns/{campaign_id}/delivery` | 홈 화면 상단 대시보드 카드 조회 |
| 5초 단위 실시간 그래프 API | GET | `/api/v1/dashboard/campaigns/{campaign_id}/realtime-graph` | 오늘 실시간 노출인구(OTS)/주목인구(LTS) 그래프 조회 (5-1절) |
| 시간별 누적 그래프 API | GET | `/api/v1/dashboard/campaigns/{campaign_id}/realtime-graph/hourly` | 과거 기간의 시간대별 누적 OTS/LTS 그래프 조회 (5-2절) |
| 깔대기 그래프 대시보드 조회 | GET | `/api/v1/dashboard/campaigns/{campaign_id}/funnel` | 유동인구, 노출인구, 주목인구, 주목 전환률 조회 |
| 평균 시청시간 API | GET | `/api/v1/dashboard/campaigns/{campaign_id}/average-watch-time` | 평균 시청시간과 시청시간 분포 조회 |
| 성별·연령 시청 비율 API | GET | `/api/v1/dashboard/campaigns/{campaign_id}/demographic-view-ratio` | 성별·연령대별 시청 비율 조회 |
| 시간·연령별 노출도 API | GET | `/api/v1/dashboard/campaigns/{campaign_id}/hourly-age-exposure` | 시간대·연령대별 노출도 히트맵 조회 |

---

## 2. 캠페인 목록 조회

캠페인명 드롭다운을 클릭했을 때 노출할 캠페인 목록을 조회한다.

### Request

```http
GET /api/v1/dashboard/campaigns
Authorization: Bearer {accessToken}
```

### Query Parameters

| 이름 | 필수 | 타입 | 설명 |
|---|---:|---|---|
| `keyword` | N | string | 캠페인명 검색어. 미입력 시 전체 조회 |
| `status` | N | string | 캠페인 상태 필터. 예: `REGISTRATION_FAILED`, `REGISTERED`, `BEFORE_EXECUTION`, `IN_EXECUTION`, `AFTER_EXECUTION` |

### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `campaigns` | array | 캠페인 목록 |
| `campaigns[].campaignId` | number | 캠페인 ID |
| `campaigns[].campaignName` | string | 캠페인명 |
| `campaigns[].brandName` | string | 브랜드명 |
| `campaigns[].executionStartDate` | string | 집행 시작일 |
| `campaigns[].executionEndDate` | string | 집행 종료일 |
| `campaigns[].status` | string | 캠페인 상태 |
| `campaigns[].isDefaultSelected` | boolean | 홈 진입 시 기본 선택 캠페인 여부 |

### Response Example

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaigns": [
      {
        "campaignId": 1,
        "campaignName": "HD현대오일뱅크 2026년 하반기 채용 공고 홍보 영상",
        "brandName": "HD현대오일뱅크",
        "executionStartDate": "2026-07-01",
        "executionEndDate": "2026-07-31",
        "status": "IN_EXECUTION",
        "isDefaultSelected": true
      },
      {
        "campaignId": 2,
        "campaignName": "삼성전자 2026 하반기 채용 공고 홍보 영상",
        "brandName": "삼성전자",
        "executionStartDate": "2026-07-05",
        "executionEndDate": "2026-07-25",
        "status": "IN_EXECUTION",
        "isDefaultSelected": false
      }
    ]
  }
}
```

### 기본 선택 규칙

홈 진입 시 기본 선택 캠페인은 서버에서 하나를 지정해 내려준다.

권장 우선순위:

1. 현재 KST 기준 집행 중인 `IN_EXECUTION` 캠페인
2. 오늘 이후 가장 먼저 시작하는 `BEFORE_EXECUTION` 캠페인
3. 최근 등록 완료된 `REGISTERED` 캠페인
4. 최근 종료된 `AFTER_EXECUTION` 캠페인
5. 없으면 가장 최근 생성된 캠페인

---

## 3. 캠페인 상세정보 조회

캠페인을 클릭하거나 기간을 변경했을 때, 현재 선택된 캠페인의 기본 정보와 선택 기간 정보를 조회한다.

### Request

```http
GET /api/v1/dashboard/campaigns/{campaign_id}?selected_start_date=2026-07-07&selected_end_date=2026-07-07
Authorization: Bearer {accessToken}
```

### Path Variables

| 이름 | 타입 | 설명 |
|---|---|---|
| `campaign_id` | number | 캠페인 ID |

### Query Parameters

| 이름 | 필수 | 타입 | 설명 |
|---|---:|---|---|
| `selected_start_date` | Y | string | 사용자가 선택한 시작일 |
| `selected_end_date` | Y | string | 사용자가 선택한 종료일 |

### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `campaignId` | number | 캠페인 ID |
| `campaignName` | string | 캠페인명 |
| `brandName` | string | 브랜드명 |
| `description` | string/null | 캠페인 설명 |
| `imageUrl` | string/null | 캠페인 이미지 URL |
| `status` | string | 캠페인 상태 |
| `mediaUnitId` | number | 연결된 매체 ID. 캠페인 생성 화면에서 매체 선택이 필수라 항상 값이 있다 |
| `dailyTargetPlayCount` | number | 하루 목표 광고 실행 횟수 |
| `executionPeriod` | object | 캠페인 집행 기간 |
| `selectedPeriod` | object | 프론트가 요청한 기간 |
| `effectivePeriod` | object/null | 실제 집계 기간. 집행 전 조회이면 `null` |
| `periodStatus` | string | `BEFORE_EXECUTION`, `IN_EXECUTION`, `AFTER_EXECUTION` |

### Response Example

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "campaignName": "HD현대오일뱅크 2026년 하반기 채용 공고 홍보 영상",
    "brandName": "HD현대오일뱅크",
    "description": "하반기 채용 공고 홍보 캠페인",
    "imageUrl": "https://example.com/campaigns/1.png",
    "status": "IN_EXECUTION",
    "mediaUnitId": 10,
    "dailyTargetPlayCount": 500,
    "executionPeriod": {
      "startDate": "2026-07-01",
      "endDate": "2026-07-31"
    },
    "periodStatus": "IN_EXECUTION",
    "selectedPeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "effectivePeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    }
  }
}
```

---

## 4. 캠페인 송출정보 조회

홈 화면 상단 대시보드 카드에 필요한 송출 관련 정보를 조회한다.

화면 카드 기준:

- 현재 송출 횟수
- 진행률
- 총 플레이타임
- 다운타임

다운타임은 현재 백엔드 응답 대상에서 제외하고, 프론트엔드에서 하드코딩한다.

### Request

```http
GET /api/v1/dashboard/campaigns/{campaign_id}/delivery?selected_start_date=2026-07-07&selected_end_date=2026-07-07
Authorization: Bearer {accessToken}
```

### Path Variables

| 이름 | 타입 | 설명 |
|---|---|---|
| `campaign_id` | number | 캠페인 ID |

### Query Parameters

| 이름 | 필수 | 타입 | 설명 |
|---|---:|---|---|
| `selected_start_date` | Y | string | 사용자가 선택한 시작일 |
| `selected_end_date` | Y | string | 사용자가 선택한 종료일 |

### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `campaignId` | number | 캠페인 ID |
| `selectedPeriod` | object | 프론트가 요청한 기간 |
| `effectivePeriod` | object/null | 실제 집계 기간. 집행 전 조회이면 `null` |
| `periodStatus` | string | `BEFORE_EXECUTION`, `IN_EXECUTION`, `AFTER_EXECUTION` |
| `today` | string | 서버 기준 오늘 날짜(KST) |
| `serverTime` | string | 서버 기준 현재 시각(KST) |
| `playStartTime` | string | 송출 카운트 시작 시각. 현재 정책은 `06:00:00` |
| `playIntervalSec` | number | 광고 1회 송출 간격. 현재 정책은 `15` |
| `refreshIntervalSec` | number | 프론트 권장 재조회 주기. 현재 `15` |
| `currentPlayCount` | number/null | 현재 광고 실행 횟수. 집행 전이면 `null` |
| `dailyTargetPlayCount` | number | 하루 목표 광고 실행 횟수 |
| `periodTargetPlayCount` | number | 선택 기간 목표 광고 실행 횟수. `dailyTargetPlayCount × effectivePeriod 일수`. 오늘 하루만 조회하면 `dailyTargetPlayCount`와 같다 |
| `progressRate` | number/null | 진행률(%). `currentPlayCount / periodTargetPlayCount * 100`. 집행 전이면 `null` |
| `totalPlayTimeMin` | number/null | 총 플레이타임(분). 집행 전이면 `null` |
| `nextIncrementAt` | string/null | 다음 카운트 증가 예상 시각. 목표 도달 또는 집행 전이면 `null` |
| `isEstimated` | boolean | 실제 송출 로그가 아닌 시간 기반 추정값인지 여부 |

### 계산식

#### 현재 송출 횟수

현재 실제 송출 로그가 없다면 서버에서 시간 기반으로 계산한다.

```text
elapsedSec = max(0, serverTime(KST) - selectedDate 06:00:00)
estimatedPlayCount = floor(elapsedSec / 15)
currentPlayCount = min(estimatedPlayCount, dailyTargetPlayCount)
```

기간 상태별 권장 정책:

- `BEFORE_EXECUTION`이면 `currentPlayCount`, `periodTargetPlayCount`, `progressRate`, `totalPlayTimeMin`는 `null`로 응답한다.
- `IN_EXECUTION`이고 선택 기간이 오늘 하루이면 위 시간 기반 현재값을 사용한다.
- `IN_EXECUTION`이고 과거 날짜 또는 여러 날짜 조회이면 `effectivePeriod`에 해당하는 실제 집계치 또는 일별 목표치 합계를 사용한다.
- `AFTER_EXECUTION`이면 선택 기간과 관계없이 캠페인 전체 집행 기간의 최종 송출 데이터를 응답한다.

실제 송출 로그 테이블이 생기면 `currentPlayCount`는 로그 집계값으로 교체한다. 그 전까지는 `isEstimated: true`로 내려준다.

이 추정치는 06:00부터 다운타임 없이 15초 간격으로 연속 재생됐다고 가정한다. 실제로 다운타임이 발생하면 `currentPlayCount`/`totalPlayTimeMin`은 실제보다 크게 나온다 — 실제 송출 로그로 교체되기 전까지는 알려진 한계다.

#### 진행률

```text
periodTargetPlayCount = dailyTargetPlayCount × effectivePeriod 일수
progressRate = currentPlayCount / periodTargetPlayCount * 100
```

기간이 오늘 하루뿐이면 `periodTargetPlayCount == dailyTargetPlayCount`라 기존과 동일하게 동작한다. 과거 여러 날짜나 `AFTER_EXECUTION`처럼 `currentPlayCount`가 기간 합계로 바뀌는 경우에도, 분모가 같이 늘어나므로 진행률이 100%를 임의로 넘지 않는다.

`BEFORE_EXECUTION`이면 `null`로 응답한다. `periodTargetPlayCount`가 0이면 `0`으로 응답한다.

#### 총 플레이타임

```text
totalPlayTimeSec = 15 * currentPlayCount
totalPlayTimeMin  = floor(totalPlayTimeSec / 60)
```

프론트에는 초 단위 없이 분 단위(`totalPlayTimeMin`)만 내려준다. 화면 표시 문구("1시간 15분" 등) 조립은 프론트가 담당한다. `BEFORE_EXECUTION`이면 `totalPlayTimeMin`은 `null`이다.

### 카운트 갱신 권장안

프론트가 15초마다 API를 계속 호출하는 방식보다, 서버가 기준값과 다음 증가 시각을 내려주는 방식을 권장한다.

1. 프론트는 API 호출로 `currentPlayCount`, `serverTime`, `nextIncrementAt`, `dailyTargetPlayCount`를 받는다.
2. 프론트는 `nextIncrementAt`에 맞춰 로컬 표시값만 1 증가시킨다.
3. 최대값(`dailyTargetPlayCount`)에 도달하면 더 이상 증가시키지 않는다.
4. 탭 복귀, 날짜 변경, 캠페인 변경, 1~5분 주기 중 하나의 시점에 API를 다시 호출해 서버값과 동기화한다.

이렇게 하면 서버 부하를 줄이면서도 사용자는 실시간처럼 증가하는 카운트를 볼 수 있다.

### Response Example

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedPeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "periodStatus": "IN_EXECUTION",
    "effectivePeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "today": "2026-07-07",
    "serverTime": "2026-07-07T08:15:00+09:00",
    "playStartTime": "06:00:00",
    "playIntervalSec": 15,
    "refreshIntervalSec": 15,
    "currentPlayCount": 500,
    "dailyTargetPlayCount": 500,
    "periodTargetPlayCount": 500,
    "progressRate": 100.0,
    "totalPlayTimeMin": 125,
    "nextIncrementAt": null,
    "isEstimated": true
  }
}
```


최대값 도달 전 예시는 아래와 같다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedPeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "effectivePeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "periodStatus": "IN_EXECUTION",
    "today": "2026-07-07",
    "serverTime": "2026-07-07T07:11:20+09:00",
    "playStartTime": "06:00:00",
    "playIntervalSec": 15,
    "refreshIntervalSec": 15,
    "currentPlayCount": 285,
    "dailyTargetPlayCount": 500,
    "periodTargetPlayCount": 500,
    "progressRate": 57.0,
    "totalPlayTimeMin": 71,
    "nextIncrementAt": "2026-07-07T07:11:30+09:00",
    "isEstimated": true
  }
}
```

---

## 5. 노출/주목 흐름 그래프

홈 화면의 실시간 노출/주목 흐름 라인 그래프는 선택 기간에 따라 **서로 다른 두 API**를 호출한다. 아래 두 조건 중 **하나라도 해당하면** 누적 데이터를 보여주기로 합의했다.

- 과거 기간을 조회한다 (`selected_end_date`가 오늘보다 이전)
- 하루를 초과하는 기간을 조회한다 (`selected_start_date != selected_end_date`) — 오늘이 포함되어 있어도 마찬가지다

| 조건 | 호출 API | 집계 단위 |
|---|---|---|
| 위 두 조건 중 하나라도 해당 | 5-2. 시간별 누적 그래프 API | 1시간 |
| 둘 다 해당 안 함 (`selected_start_date == selected_end_date == 오늘`) | 5-1. 실시간 그래프 API | 5초 |

같은 화면 카드가 이 둘 중 하나를 선택해서 쓰는 구조이며, 두 API의 응답 필드는 서로 다르다. 프론트는 캠페인/기간이 바뀔 때마다 이 조건을 다시 판단해서 호출 API를 바꾼다.

### 5-1. 실시간 그래프 API (오늘, 5초 단위)

SQS로 들어오는 5초 단위 Vision Summary 데이터를 홈 화면의 실시간 그래프에 표시하기 위한 API다. **오늘 하루 조회에만 사용한다.**

화면 카드 기준:

- 실시간 노출/주목 흐름 라인 그래프
- 5초 단위 노출인구(OTS, `ots_count`)
- 5초 단위 주목인구(LTS, `lts_count`)

### 권장 구현 로직

프론트엔드 요청이 들어올 때마다 SQS를 직접 pull하지 않는다. SQS는 메시지 큐이고, 메시지를 읽은 뒤 삭제해야 하므로 여러 브라우저 요청이 SQS를 직접 건드리면 데이터 유실, 중복 소비, 응답 지연, 권한 관리 문제가 생긴다.

권장 구조는 아래와 같다.

```text
Vision AI
  → 5초마다 SQS SendMessage
  → 백엔드 SQS Consumer가 long polling으로 수신
  → vision_summary_5s 테이블에 멱등 저장
  → 대시보드 API가 DB 또는 캐시에서 조회
  → 프론트엔드는 5초마다 대시보드 API polling
```

백엔드 SQS Consumer는 애플리케이션 내부 스케줄러 또는 별도 worker로 상시 실행한다.

1. `ReceiveMessage`는 long polling을 사용한다. 예: `WaitTimeSeconds=20`, `MaxNumberOfMessages=10`.
2. 메시지 body를 Vision Summary 스키마로 검증한다.
3. `board_id` 또는 `media_unit_id`로 매체를 찾고, `event_time` 기준으로 현재 송출 중인 캠페인을 연결한다.
4. MVP에서는 한 매체의 동일 시간대에 하나의 캠페인만 존재하도록 캠페인 확정 트랜잭션에서 검증한다. 같은 매체의 집행 기간이 겹치면 새 캠페인 확정을 거부한다.
5. `vision_summary_5s`에 저장한다. 이미 같은 메시지가 저장되어 있으면 성공으로 보고 넘어간다.
6. DB 저장이 성공한 뒤에만 SQS 메시지를 삭제한다.
7. 그래프 응답 속도가 중요해지면 Redis 같은 캐시에 최신 포인트를 함께 적재한다. DB는 source of truth로 유지한다.

SQS Standard Queue는 순서와 정확히 한 번 전달을 보장하지 않는다. 따라서 API는 `eventTime` 오름차순으로 정렬해서 응답하고, 프론트엔드는 `eventTime`을 key로 upsert해야 한다.

### SQS 중복 처리 권장안

SQS는 at-least-once 방식이므로 같은 메시지가 두 번 이상 도착할 수 있다. 백엔드는 메시지 처리 전체를 idempotent하게 만든다.

권장 처리 방식:

1. SQS consumer는 메시지를 읽고 v2 Vision Summary 스키마를 검증한다.
2. `timestamp`를 UTC instant로 파싱해 `event_time`에 저장한다.
3. `board_id`/`device_id`로 `media_unit_id`를 찾는다.
4. 한 매체에서 5초 window 하나는 한 행만 저장한다는 전제로 `(media_unit_id, event_time)`을 중복 방지 키로 사용한다. `vision_summary_5s`의 UNIQUE 제약도 `(media_unit_id, event_time)` 2컬럼이다 (`seq`는 디바이스 재시작 시 리셋되는 보조 카운터라 unique key에서 제외했다 — V4 마이그레이션 적용 완료).
5. insert 중 duplicate key가 발생하면 이미 처리한 메시지로 보고 성공 처리한다. 이 경우 SQS 메시지는 삭제하고 API에는 기존 DB row를 사용한다.
6. 같은 `(media_unit_id, event_time)`인데 payload 값이 다르면 기본 정책은 기존 row 유지, 경고 로그/모니터링 기록이다. 추후 Vision AI가 정정 메시지를 보낸다는 계약이 생기면 upsert 정책으로 바꾼다.
7. 파싱 실패나 매체 매핑 실패처럼 재시도해도 해결되지 않는 오류는 DLQ로 보내고, 일시적 DB 오류는 SQS 재시도를 사용한다.

`seq`는 정렬/디버깅 보조 값으로만 저장한다 (unique key에는 포함되지 않는다).

### 프론트엔드 polling 전략

이 API는 날짜 파라미터를 받지 않는다 — 항상 서버 기준 오늘(KST)만 조회한다. 과거 날짜 조회는 아예 요청할 방법이 없도록 설계해서, 프론트가 실수로 과거 날짜를 넣어 호출하는 상황 자체를 막는다 (과거 조회가 필요하면 5-2절 API를 쓴다). 대신 커서(`after_event_time`)를 사용한다. 의미는 다음과 같다.

- `after_event_time`: 프론트가 마지막으로 받은 포인트의 시각이다. 서버는 이 시각 이후에 새로 저장된 포인트만 내려준다.
- 첫 조회에는 `after_event_time`을 보내지 않고 최근 N개 포인트를 받는다.
- 이후 5초마다 마지막 `lastEventTime`을 `after_event_time`으로 보내면, 이미 받은 전체 데이터를 다시 받지 않고 새 포인트만 추가로 받을 수 있다.
- 캠페인이 바뀌거나 자정이 지나 서버 기준 오늘이 바뀌면 그래프의 기준 데이터가 달라지므로 `after_event_time`을 초기화한다.

첫 진입 시에는 최근 N개 포인트를 조회한다.

```http
GET /api/v1/dashboard/campaigns/{campaign_id}/realtime-graph?limit=60
```

이후에는 마지막으로 받은 `lastEventTime`을 `after_event_time`으로 보내 5초마다 새 포인트만 조회한다.

```http
GET /api/v1/dashboard/campaigns/{campaign_id}/realtime-graph?after_event_time=2026-07-07T08:14:55+09:00&limit=20
```

다만 SQS 메시지는 늦게 도착하거나 순서가 바뀔 수 있으므로, 서버는 내부적으로 `after_event_time`보다 10~30초 정도 이전 데이터까지 겹쳐서 조회해도 된다. 프론트는 같은 `eventTime`의 포인트가 다시 오면 새 값으로 덮어쓴다.

### Request

```http
GET /api/v1/dashboard/campaigns/{campaign_id}/realtime-graph?after_event_time=2026-07-07T08:14:55+09:00&limit=20
Authorization: Bearer {accessToken}
```

### Path Variables

| 이름 | 타입 | 설명 |
|---|---|---|
| `campaign_id` | number | 캠페인 ID |

### Query Parameters

| 이름 | 필수 | 타입 | 설명 |
|---|---:|---|---|
| `after_event_time` | N | string | 이 시각 이후의 포인트 조회. 첫 조회 시 미입력 |
| `limit` | N | number | 최대 포인트 개수. 기본 60, 최대 720 |

날짜 파라미터는 없다 — 항상 서버 기준 오늘(KST)만 조회한다.

`limit=60`이면 5초 단위 기준 최근 5분 데이터다. `limit=720`이면 최근 1시간 데이터다.

`periodStatus`가 `BEFORE_EXECUTION`(집행 전)이거나 `AFTER_EXECUTION`(오늘이 이미 집행 종료일 이후)이면 `points`는 빈 배열이고 `effectivePeriod`는 `null`이다 — 이 API는 라이브 데이터 전용이라 캠페인이 지금 진행 중이 아니면 보여줄 게 없다. 과거 실적을 보려면 5-2절 API를 사용한다.

### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `campaignId` | number | 캠페인 ID |
| `selectedDate` | string | 조회 날짜. 요청 파라미터가 아니라 서버가 결정한 오늘(KST) 날짜다 |
| `effectivePeriod` | object/null | 실제 그래프 조회 기간. 집행 전 조회이면 `null` |
| `periodStatus` | string | `BEFORE_EXECUTION`, `IN_EXECUTION`, `AFTER_EXECUTION` |
| `serverTime` | string | 서버 기준 현재 시각(KST) |
| `pollIntervalSec` | number | 프론트 권장 polling 주기. 현재 `5` |
| `overlapSec` | number | 서버가 중복 허용을 위해 겹쳐 조회한 시간 |
| `lastEventTime` | string/null | 응답 포인트 중 가장 최신 eventTime |
| `nextPollAfter` | string | 다음 polling 권장 시각 |
| `dataDelaySec` | number | 서버 시각과 최신 데이터 시각의 차이(초) |
| `hasMore` | boolean | 더 조회할 과거 데이터가 있는지 여부 |
| `points` | array | 5초 단위 그래프 포인트 |
| `points[].eventTime` | string | 해당 5초 window의 기준 시각 |
| `points[].intervalSec` | number | 집계 간격. 일반적으로 `5` |
| `points[].exposedPopulationCount` | number | 해당 5초 동안의 노출인구 수. 원천은 `ots_count` |
| `points[].attentionPopulationCount` | number | 해당 5초 동안의 주목인구 수. 원천은 `lts_count` |
| `points[].source` | string | 데이터 출처. 예: `SQS`, `DB`, `CACHE` |

### 지표 매핑

| 원천 데이터 | 응답 필드 | 설명 |
|---|---|---|
| `ots_count` | `exposedPopulationCount` | 5초 window의 노출인구 |
| `lts_count` | `attentionPopulationCount` | 5초 window의 주목인구 |

v2 Vision Summary에서 `ots_count`가 노출인구이고 `lts_count`가 주목인구다. 별도 `exposure_count`는 사용하지 않는다.

### Response Example: 첫 조회

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedDate": "2026-07-07",
    "effectivePeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "periodStatus": "IN_EXECUTION",
    "serverTime": "2026-07-07T08:15:02+09:00",
    "pollIntervalSec": 5,
    "overlapSec": 0,
    "lastEventTime": "2026-07-07T08:15:00+09:00",
    "nextPollAfter": "2026-07-07T08:15:07+09:00",
    "dataDelaySec": 2,
    "hasMore": true,
    "points": [
      {
        "eventTime": "2026-07-07T08:14:50+09:00",
        "intervalSec": 5,
        "exposedPopulationCount": 148,
        "attentionPopulationCount": 44,
        "source": "DB"
      },
      {
        "eventTime": "2026-07-07T08:14:55+09:00",
        "intervalSec": 5,
        "exposedPopulationCount": 153,
        "attentionPopulationCount": 45,
        "source": "DB"
      },
      {
        "eventTime": "2026-07-07T08:15:00+09:00",
        "intervalSec": 5,
        "exposedPopulationCount": 151,
        "attentionPopulationCount": 45,
        "source": "DB"
      }
    ]
  }
}
```

### Response Example: 이후 polling

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedDate": "2026-07-07",
    "effectivePeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "periodStatus": "IN_EXECUTION",
    "serverTime": "2026-07-07T08:15:07+09:00",
    "pollIntervalSec": 5,
    "overlapSec": 15,
    "lastEventTime": "2026-07-07T08:15:05+09:00",
    "nextPollAfter": "2026-07-07T08:15:12+09:00",
    "dataDelaySec": 2,
    "hasMore": false,
    "points": [
      {
        "eventTime": "2026-07-07T08:14:55+09:00",
        "intervalSec": 5,
        "exposedPopulationCount": 153,
        "attentionPopulationCount": 45,
        "source": "DB"
      },
      {
        "eventTime": "2026-07-07T08:15:00+09:00",
        "intervalSec": 5,
        "exposedPopulationCount": 151,
        "attentionPopulationCount": 45,
        "source": "DB"
      },
      {
        "eventTime": "2026-07-07T08:15:05+09:00",
        "intervalSec": 5,
        "exposedPopulationCount": 156,
        "attentionPopulationCount": 46,
        "source": "DB"
      }
    ]
  }
}
```

### 데이터가 아직 없는 경우

집행 전이거나, SQS 데이터가 아직 도착하지 않았거나, 캠페인에 연결된 매체 데이터가 없는 경우 `points`는 빈 배열로 응답한다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedDate": "2026-06-30",
    "effectivePeriod": null,
    "periodStatus": "BEFORE_EXECUTION",
    "serverTime": "2026-07-07T08:15:07+09:00",
    "pollIntervalSec": 5,
    "overlapSec": 0,
    "lastEventTime": null,
    "nextPollAfter": "2026-07-07T08:15:12+09:00",
    "dataDelaySec": null,
    "hasMore": false,
    "points": []
  }
}
```

### SSE/WebSocket 전환 기준

초기 구현은 5초 polling으로 충분하다. 아래 조건이 생기면 SSE 또는 WebSocket을 별도 API로 검토한다.

- 동시에 보는 사용자가 많아져 polling 부하가 커지는 경우
- 1~2초 이하의 지연 시간이 필요한 경우
- 서버에서 여러 그래프/알림을 한 번에 push해야 하는 경우

그 전까지는 `GET /realtime-graph` polling 방식이 구현, 테스트, 장애 대응이 가장 단순하다.

### 5-2. 시간별 누적 그래프 API (과거 기간, 1시간 단위)

과거 기간을 조회하거나 하루를 초과하는 기간을 조회할 때(오늘 포함 여부와 무관) 사용한다. 5-1과 같은 화면 카드(노출/주목 흐름 그래프)에 쓰이지만, 5초 단위 실시간 데이터가 아니라 **1시간 단위로 누적**된 값을 응답한다. 커서(`after_event_time`)는 사용하지 않고 매번 선택 기간 전체를 다시 조회한다 — 완전히 과거 기간이면 값이 더 이상 바뀌지 않아 재조회가 사실상 불필요하지만, 선택 기간에 오늘이 포함되면 오늘에 해당하는 마지막 시간대는 아직 확정 전이라 `refreshIntervalSec`(3600)에 맞춰 재조회해야 한다.

#### Request

```http
GET /api/v1/dashboard/campaigns/{campaign_id}/realtime-graph/hourly?selected_start_date=2026-07-01&selected_end_date=2026-07-05
Authorization: Bearer {accessToken}
```

#### Path Variables

| 이름 | 타입 | 설명 |
|---|---|---|
| `campaign_id` | number | 캠페인 ID |

#### Query Parameters

| 이름 | 필수 | 타입 | 설명 |
|---|---:|---|---|
| `selected_start_date` | Y | string | 사용자가 선택한 시작일 |
| `selected_end_date` | Y | string | 사용자가 선택한 종료일 |

기간 처리 규칙은 0절과 동일하다 (`BEFORE_EXECUTION`/`IN_EXECUTION`/`AFTER_EXECUTION`, `effectivePeriod` 계산 방식 포함).

#### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `campaignId` | number | 캠페인 ID |
| `selectedPeriod` | object | 프론트가 요청한 기간 |
| `effectivePeriod` | object/null | 실제 집계 기간. 집행 전이면 `null` |
| `periodStatus` | string | `BEFORE_EXECUTION`, `IN_EXECUTION`, `AFTER_EXECUTION` |
| `serverTime` | string | 서버 기준 현재 시각(KST) |
| `aggregationUnit` | string | `HOUR` |
| `aggregationCutoffTime` | string/null | 집계에 포함된 데이터의 배타적 상한 시각(그 시각 자체는 미포함). 집행 전이면 `null` |
| `refreshIntervalSec` | number | 프론트 권장 재조회 주기. `3600`. 선택 기간에 오늘이 포함되지 않으면(완전히 과거) 값이 더 안 바뀌므로 재조회하지 않아도 된다 |
| `points` | array | 1시간 단위 그래프 포인트 |
| `points[].eventTime` | string | 해당 1시간 구간의 시작 시각 |
| `points[].exposedPopulationCount` | number | 해당 시간 동안의 노출인구 합계. 원천은 `ots_count` 합 |
| `points[].attentionPopulationCount` | number | 해당 시간 동안의 주목인구 합계. 원천은 `lts_count` 합 |

#### 계산식

```text
exposedPopulationCount(해당 시간대)   = sum(vision_summary_5s.ots_count)  [해당 캠페인, 해당 1시간 구간]
attentionPopulationCount(해당 시간대) = sum(vision_summary_5s.lts_count)  [해당 캠페인, 해당 1시간 구간]
```

`effectivePeriod`에 해당하는 시간대만 `points`에 포함한다. `BEFORE_EXECUTION`이면 `points`는 빈 배열이다.

#### Response Example

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedPeriod": {
      "startDate": "2026-07-01",
      "endDate": "2026-07-05"
    },
    "effectivePeriod": {
      "startDate": "2026-07-01",
      "endDate": "2026-07-05"
    },
    "periodStatus": "IN_EXECUTION",
    "serverTime": "2026-07-07T16:43:25+09:00",
    "aggregationUnit": "HOUR",
    "aggregationCutoffTime": "2026-07-06T00:00:00+09:00",
    "refreshIntervalSec": 3600,
    "points": [
      {
        "eventTime": "2026-07-01T06:00:00+09:00",
        "exposedPopulationCount": 5820,
        "attentionPopulationCount": 412
      },
      {
        "eventTime": "2026-07-01T07:00:00+09:00",
        "exposedPopulationCount": 6210,
        "attentionPopulationCount": 455
      }
    ]
  }
}
```

## 6. 깔대기 그래프 대시보드 조회

특정 캠페인과 기간에 대한 깔대기 그래프 지표를 조회한다.

화면 카드 기준:

- 전체 유동인구
- 노출인구
- 주목인구
- 주목 전환률
- 오늘 조회 시에만 어제 대비 증가율

### Request

```http
GET /api/v1/dashboard/campaigns/{campaign_id}/funnel?selected_start_date=2026-07-07&selected_end_date=2026-07-07
Authorization: Bearer {accessToken}
```

### Path Variables

| 이름 | 타입 | 설명 |
|---|---|---|
| `campaign_id` | number | 캠페인 ID |

### Query Parameters

| 이름 | 필수 | 타입 | 설명 |
|---|---:|---|---|
| `selected_start_date` | Y | string | 사용자가 선택한 시작일 |
| `selected_end_date` | Y | string | 사용자가 선택한 종료일 |

### 지표 정의

| 지표 | 응답 필드 | 정의 |
|---|---|---|
| 전체 유동인구 | `totalTrafficCount` | 매체 좌표가 속한 250m 구역의 서울시 공공데이터 일일 유동인구 합계 |
| 노출인구 | `exposedPopulationCount` | 선택 기간의 `vision_summary_5s.ots_count` 합계 |
| 주목인구 | `attentionPopulationCount` | 선택 기간의 LTS 기준 주목 인구 합계 |
| 주목 전환률 | `attentionConversionRate` | `attentionPopulationCount / exposedPopulationCount * 100` |

전체 유동인구는 카메라가 직접 관측한 OTS가 아니라, 서울시 공공데이터를 매체 좌표와 매핑해서 저장한 일일 유동인구 데이터를 사용한다. 백엔드는 매체 또는 광고판 좌표가 속한 250m 구역을 계산해 `traffic_area_id` 같은 내부 구역 식별자와 연결해둔다.

현재 저장 데이터 기준으로는 아래 매핑을 우선 사용한다.

| DB/원천 필드 | API 지표 |
|---|---|
| 서울시 공공데이터 250m 구역 일일 유동인구 합계 | `totalTrafficCount` |
| `vision_summary_5s.ots_count` 합계 | `exposedPopulationCount` |
| `vision_summary_5s.lts_count` 합계 | `attentionPopulationCount` |
| `lts_count 합계 / exposedPopulationCount * 100` | `attentionConversionRate` |

`exposedPopulationCount`는 광고 매체/카메라 기준의 실제 노출 인구이며 원천은 v2 Vision Summary의 `ots_count`다. 전체 유동인구(`totalTrafficCount`)는 서울시 공공데이터 기반의 주변 250m 구역 일일 유동인구이므로, 두 값의 출처와 의미가 다르다.

깔대기 API는 1분마다 재조회하지만, `totalTrafficCount` 자체는 일일 공공데이터가 새로 적재될 때 갱신된다. 따라서 같은 날짜 안에서 1분마다 변하는 값은 주로 `exposedPopulationCount`, `attentionPopulationCount`, `attentionConversionRate`이고, `totalTrafficCount`는 `trafficArea.dataDateRange` 기준의 일 단위 값이다. 요청 기간에 해당하는 공공데이터가 아직 적재되지 않았으면 `totalTrafficCount.value`와 해당 `yesterdayComparison`은 `null`로 응답한다.

MVP에서는 전체 유동인구용 DB 스키마를 보류하므로 `totalTrafficCount`와 `trafficArea`는 mock provider에서 응답한다. 이때 `trafficArea.dataSource = MOCK_SEOUL_OPEN_DATA`, `trafficArea.isMock = true`로 내려 프론트와 백엔드가 실제 공공데이터 연동 전 상태임을 구분한다.

### 어제 대비 증가율

`selected_start_date == selected_end_date == 오늘(KST)`일 때만 `yesterdayComparison`을 응답한다.

오늘 조회가 아닌 경우:

```json
"yesterdayComparison": null
```

오늘 조회인 경우:

```text
increaseRate = (todayValue - yesterdayValue) / yesterdayValue * 100
```

비교 기준은 "어제 같은 시간대"를 권장한다.

예를 들어 오늘 `2026-07-07 08:15`에 조회하면:

- 오늘 집계 범위: `2026-07-07 00:00:00` ~ `2026-07-07 08:15:00`
- 어제 비교 범위: `2026-07-06 00:00:00` ~ `2026-07-06 08:15:00`

`yesterdayValue`가 0이면 증가율은 계산하지 않고 `increaseRate: null`로 응답한다.

### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `campaignId` | number | 캠페인 ID |
| `selectedPeriod` | object | 프론트가 요청한 기간 |
| `effectivePeriod` | object/null | 실제 집계 기간. 집행 전 조회이면 `null` |
| `periodStatus` | string | `BEFORE_EXECUTION`, `IN_EXECUTION`, `AFTER_EXECUTION` |
| `serverTime` | string | 서버 기준 현재 시각(KST) |
| `aggregationUnit` | string | 집계 단위. 깔대기 그래프는 `MINUTE` |
| `aggregationCutoffTime` | string/null | 집계에 포함된 데이터의 배타적 상한 시각(그 시각 자체는 미포함). 집행 전이면 `null` |
| `refreshIntervalSec` | number | 프론트 권장 재조회 주기. 깔대기 그래프는 `60` |
| `trafficArea.areaId` | string/null | 매체 좌표가 속한 서울시 공공데이터 250m 구역 식별자 |
| `trafficArea.gridSizeMeter` | number | 유동인구 매핑 구역 크기. 현재 `250` |
| `trafficArea.dataSource` | string | 전체 유동인구 출처. `SEOUL_OPEN_DATA` 또는 `MOCK_SEOUL_OPEN_DATA` |
| `trafficArea.isMock` | boolean | MVP mock 데이터 여부 |
| `trafficArea.dataAggregationUnit` | string | 전체 유동인구 원천 집계 단위. 현재 `DAY` |
| `trafficArea.dataDateRange` | object/null | 전체 유동인구 계산에 사용한 공공데이터 날짜 범위 |
| `metrics.totalTrafficCount.value` | number/null | 전체 유동인구. 집행 전이면 `null` |
| `metrics.totalTrafficCount.yesterdayComparison` | object/null | 어제 대비 증가율 정보 |
| `metrics.exposedPopulationCount.value` | number/null | 노출인구. 집행 전이면 `null` |
| `metrics.exposedPopulationCount.yesterdayComparison` | object/null | 어제 대비 증가율 정보 |
| `metrics.attentionPopulationCount.value` | number/null | 주목인구. 집행 전이면 `null` |
| `metrics.attentionPopulationCount.yesterdayComparison` | object/null | 어제 대비 증가율 정보 |
| `metrics.attentionConversionRate.value` | number/null | 주목 전환률(%). 집행 전이면 `null` |
| `metrics.attentionConversionRate.yesterdayComparison` | object/null | 어제 대비 증가율 정보 |

### Response Example: 오늘 조회

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedPeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "effectivePeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "periodStatus": "IN_EXECUTION",
    "serverTime": "2026-07-07T16:43:25+09:00",
    "aggregationUnit": "MINUTE",
    "aggregationCutoffTime": "2026-07-07T16:43:00+09:00",
    "refreshIntervalSec": 60,
    "trafficArea": {
      "areaId": "SEOUL_250M_11680_00123",
      "gridSizeMeter": 250,
      "dataSource": "MOCK_SEOUL_OPEN_DATA",
      "isMock": true,
      "dataAggregationUnit": "DAY",
      "dataDateRange": {
        "startDate": "2026-07-07",
        "endDate": "2026-07-07"
      }
    },
    "metrics": {
      "totalTrafficCount": {
        "value": 58123,
        "unit": "people",
        "yesterdayComparison": {
          "baseDate": "2026-07-06",
          "baseValue": 50542,
          "increaseRate": 15.0
        }
      },
      "exposedPopulationCount": {
        "value": 8123,
        "unit": "people",
        "yesterdayComparison": {
          "baseDate": "2026-07-06",
          "baseValue": 8164,
          "increaseRate": -0.5
        }
      },
      "attentionPopulationCount": {
        "value": 123,
        "unit": "people",
        "yesterdayComparison": {
          "baseDate": "2026-07-06",
          "baseValue": 115,
          "increaseRate": 7.0
        }
      },
      "attentionConversionRate": {
        "value": 1.51,
        "unit": "percent",
        "yesterdayComparison": {
          "baseDate": "2026-07-06",
          "baseValue": 1.41,
          "increaseRate": 7.1
        }
      }
    }
  }
}
```

### Response Example: 집행 후 기간 조회

오늘 하루 조회가 아닌 경우 어제 대비 증가율은 내려주지 않는다.

`AFTER_EXECUTION`이면 선택 기간이 집행 종료일 이후여도 `effectivePeriod`는 전체 집행 기간으로 내려가고, 집행 기간 중 모든 데이터를 응답한다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedPeriod": {
      "startDate": "2026-08-01",
      "endDate": "2026-08-07"
    },
    "effectivePeriod": {
      "startDate": "2026-07-01",
      "endDate": "2026-07-31"
    },
    "periodStatus": "AFTER_EXECUTION",
    "serverTime": "2026-08-07T16:43:25+09:00",
    "aggregationUnit": "MINUTE",
    "aggregationCutoffTime": "2026-08-01T00:00:00+09:00",
    "refreshIntervalSec": 60,
    "trafficArea": {
      "areaId": "SEOUL_250M_11680_00123",
      "gridSizeMeter": 250,
      "dataSource": "MOCK_SEOUL_OPEN_DATA",
      "isMock": true,
      "dataAggregationUnit": "DAY",
      "dataDateRange": {
        "startDate": "2026-07-01",
        "endDate": "2026-07-31"
      }
    },
    "metrics": {
      "totalTrafficCount": {
        "value": 351024,
        "unit": "people",
        "yesterdayComparison": null
      },
      "exposedPopulationCount": {
        "value": 48940,
        "unit": "people",
        "yesterdayComparison": null
      },
      "attentionPopulationCount": {
        "value": 742,
        "unit": "people",
        "yesterdayComparison": null
      },
      "attentionConversionRate": {
        "value": 1.52,
        "unit": "percent",
        "yesterdayComparison": null
      }
    }
  }
}
```

### Response Example: 집행 전 조회

집행 시작일 이전 기간을 조회하면 `effectivePeriod`는 `null`이고 집계형 값은 모두 `null`이다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedPeriod": {
      "startDate": "2026-06-01",
      "endDate": "2026-06-30"
    },
    "effectivePeriod": null,
    "periodStatus": "BEFORE_EXECUTION",
    "serverTime": "2026-07-07T16:43:25+09:00",
    "aggregationUnit": "MINUTE",
    "aggregationCutoffTime": null,
    "refreshIntervalSec": 60,
    "trafficArea": {
      "areaId": "SEOUL_250M_11680_00123",
      "gridSizeMeter": 250,
      "dataSource": "MOCK_SEOUL_OPEN_DATA",
      "isMock": true,
      "dataAggregationUnit": "DAY",
      "dataDateRange": null
    },
    "metrics": {
      "totalTrafficCount": {
        "value": null,
        "unit": "people",
        "yesterdayComparison": null
      },
      "exposedPopulationCount": {
        "value": null,
        "unit": "people",
        "yesterdayComparison": null
      },
      "attentionPopulationCount": {
        "value": null,
        "unit": "people",
        "yesterdayComparison": null
      },
      "attentionConversionRate": {
        "value": null,
        "unit": "percent",
        "yesterdayComparison": null
      }
    }
  }
}
```
---

## 7. 평균 시청시간 및 1시간 단위 대시보드 API

평균 시청시간은 1분 단위로 갱신하고, 성별·연령 시청 비율과 시간·연령별 노출도는 1시간 단위로 갱신한다.

오늘 데이터를 조회할 때 서버 시각이 `16:43`이면 평균 시청시간의 `aggregationCutoffTime`은 직전 완료 분의 끝 시각(=지금 진행 중인 분의 시작 시각)인 `16:43:00`이 된다. 성별·연령 시청 비율과 시간·연령별 노출도의 `aggregationCutoffTime`은 직전 완료 시간의 끝 시각(=지금 진행 중인 시간대의 시작 시각)인 `16:00:00`이 된다.

세 API는 모두 같은 기간 처리 규칙을 사용한다.

- `BEFORE_EXECUTION`: `effectivePeriod = null`, 집계값은 `null` 또는 빈 배열
- `IN_EXECUTION`: `effectivePeriod`에 해당하는 데이터 응답
- `AFTER_EXECUTION`: 전체 집행 기간 데이터 응답

### 공통 Request

```http
GET /api/v1/dashboard/campaigns/{campaign_id}/{metric_endpoint}?selected_start_date=2026-07-07&selected_end_date=2026-07-07
Authorization: Bearer {accessToken}
```

### 공통 Query Parameters

| 이름 | 필수 | 타입 | 설명 |
|---|---:|---|---|
| `selected_start_date` | Y | string | 사용자가 선택한 시작일 |
| `selected_end_date` | Y | string | 사용자가 선택한 종료일 |

### 공통 Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `campaignId` | number | 캠페인 ID |
| `selectedPeriod` | object | 프론트가 요청한 기간 |
| `effectivePeriod` | object/null | 실제 집계 기간. 집행 전이면 `null` |
| `periodStatus` | string | `BEFORE_EXECUTION`, `IN_EXECUTION`, `AFTER_EXECUTION` |
| `serverTime` | string | 서버 기준 현재 시각(KST) |
| `aggregationUnit` | string | 평균 시청시간은 `MINUTE`, 성별·연령 시청 비율과 시간·연령별 노출도는 `HOUR` |
| `aggregationCutoffTime` | string/null | 집계에 포함된 데이터의 배타적 상한 시각(그 시각 자체는 미포함). 집행 전이면 `null` |
| `refreshIntervalSec` | number | 프론트 권장 재조회 주기. 평균 시청시간은 `60`, 1시간 단위 API는 `3600` |

### 평균 시청시간 API

```http
GET /api/v1/dashboard/campaigns/{campaign_id}/average-watch-time?selected_start_date=2026-07-07&selected_end_date=2026-07-07
```

`vision_summary_5s.avg_dwell_sec`, `dwell_sum_sec`, 시청시간 분포 필드를 기반으로 평균 시청시간과 도넛 차트 분포를 응답한다.

평균 시청시간은 1분 단위로 확정된 데이터만 포함한다. 오늘 `16:43`에 조회하면 직전 완료 분(`16:42`~`16:43`)까지의 5초 row를 모아 계산한다. DB 조회 범위는 `event_time < 16:43:00`이고, 응답의 `aggregationCutoffTime`은 이 조회 상한과 같은 `16:43:00+09:00`(=지금 진행 중인 분의 시작 시각)으로 내려준다.

선택 기간이 완전히 과거(오늘 미포함)이면 1분 단위 확정 제약과 무관하게 해당 날짜 `00:00:00`~`24:00:00`(KST) 전체 데이터를 사용한다 — 이미 다 지난 날이라 그 안의 모든 5초 row가 확정 데이터이기 때문이다. 예를 들어 오늘이 `2026-07-07`이고 `2026-07-05`를 조회하면, DB 조회 범위는 `2026-07-05T00:00:00+09:00 <= event_time < 2026-07-06T00:00:00+09:00`이고, 응답의 `aggregationCutoffTime`은 이 조회 상한과 같은 `2026-07-06T00:00:00+09:00`이 된다. 이 경우 값이 더 바뀌지 않으므로 `refreshIntervalSec`에 맞춰 재조회할 필요는 없다.

계산식은 5초 row의 `avg_dwell_sec`를 단순 평균하지 않고, 아래 가중 평균을 사용한다.

```text
averageWatchTimeSec = sum(vision_summary_5s.dwell_sum_sec) / sum(vision_summary_5s.lts_count)
```

`sum(lts_count) == 0`이면 평균을 계산할 수 없으므로 `averageWatchTimeSec = null`로 응답한다. 시청시간 구간 분포는 같은 집계 범위의 `dwell_1_2s_count`, `dwell_2_3s_count`, `dwell_3_4s_count`, `dwell_over_4s_count` 합계를 사용한다.

이 식은 5초 window 하나에서 `dwell_sum_sec`을 구성한 인원과 `lts_count`가 항상 같은 모집단이라는 전제로 성립한다(그래야 `dwell_sum_sec / lts_count`가 그 window의 실제 평균 시청시간이 되고, 여러 window를 합산한 가중평균도 의미가 있다). Vision AI팀에 이 전제가 맞는지 확인이 필요하다 (9절 참고).

#### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `averageWatchTimeSec` | number/null | 평균 시청시간(초). 집행 전이면 `null` |
| `watchTimeBuckets` | array | 시청시간 구간별 분포 |
| `watchTimeBuckets[].bucket` | string | `1_TO_2S`, `2_TO_3S`, `3_TO_4S`, `OVER_4S` |
| `watchTimeBuckets[].label` | string | 화면 표시 라벨 |
| `watchTimeBuckets[].count` | number | 해당 구간 시청 수 |
| `watchTimeBuckets[].ratio` | number | 전체 대비 비율(%) |

#### Response Example: 오늘 조회

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedPeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "effectivePeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "periodStatus": "IN_EXECUTION",
    "serverTime": "2026-07-07T16:43:25+09:00",
    "aggregationUnit": "MINUTE",
    "aggregationCutoffTime": "2026-07-07T16:43:00+09:00",
    "refreshIntervalSec": 60,
    "averageWatchTimeSec": 1.6,
    "watchTimeBuckets": [
      { "bucket": "1_TO_2S", "label": "1-2초", "count": 320, "ratio": 34.0 },
      { "bucket": "2_TO_3S", "label": "2-3초", "count": 280, "ratio": 29.8 },
      { "bucket": "3_TO_4S", "label": "3-4초", "count": 210, "ratio": 22.3 },
      { "bucket": "OVER_4S", "label": "4초 이상", "count": 131, "ratio": 13.9 }
    ]
  }
}
```

#### Response Example: 과거 날짜 조회

오늘이 `2026-07-07`일 때 지난 날짜 `2026-07-05`를 조회한 예시다. 해당 날짜가 이미 완전히 지났으므로 `aggregationCutoffTime`은 조회 상한인 다음 날 `00:00:00`(`2026-07-06T00:00:00`)이고, 하루 전체 데이터가 반영된 값이라 더 이상 바뀌지 않는다.

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedPeriod": {
      "startDate": "2026-07-05",
      "endDate": "2026-07-05"
    },
    "effectivePeriod": {
      "startDate": "2026-07-05",
      "endDate": "2026-07-05"
    },
    "periodStatus": "IN_EXECUTION",
    "serverTime": "2026-07-07T16:43:25+09:00",
    "aggregationUnit": "MINUTE",
    "aggregationCutoffTime": "2026-07-06T00:00:00+09:00",
    "refreshIntervalSec": 60,
    "averageWatchTimeSec": 2.1,
    "watchTimeBuckets": [
      { "bucket": "1_TO_2S", "label": "1-2초", "count": 4820, "ratio": 28.4 },
      { "bucket": "2_TO_3S", "label": "2-3초", "count": 4510, "ratio": 26.6 },
      { "bucket": "3_TO_4S", "label": "3-4초", "count": 4102, "ratio": 24.2 },
      { "bucket": "OVER_4S", "label": "4초 이상", "count": 3520, "ratio": 20.8 }
    ]
  }
}
```

### 성별·연령 시청 비율 API

```http
GET /api/v1/dashboard/campaigns/{campaign_id}/demographic-view-ratio?selected_start_date=2026-07-07&selected_end_date=2026-07-07
```

LTS 성별·연령 집계를 기반으로 화면 우측의 성별·연령 시청 비율 막대 그래프를 응답한다.

화면에 **전체/남성/여성 토글 버튼**이 있다. 토글은 순수 화면 전환이라 버튼 클릭마다 API를 다시 호출하지 않는다 — 한 번의 응답에 세 가지 뷰에 필요한 값을 모두 내려주고, 프론트는 토글 상태에 따라 `ageGroups[]`의 필드 중 어떤 걸 막대 길이로 쓸지만 바꾼다.

- **전체 탭**: `totalRatio`를 막대 길이로 쓰고, 막대 안을 `maleRatio`/`femaleRatio` 비율로 두 가지 색으로 채운다.
- **남성 탭**: `maleShareRatio`를 막대 길이로 쓴다 (단색). 7개 연령대의 `maleShareRatio` 합은 100%다.
- **여성 탭**: `femaleShareRatio`를 막대 길이로 쓴다 (단색). 7개 연령대의 `femaleShareRatio` 합은 100%다.

`maleRatio`/`femaleRatio`/`totalRatio`는 **전체 시청자(남녀 합산) 대비** 비율이라 7개 연령대의 `totalRatio`를 다 더하면 100%가 된다. 반면 `maleShareRatio`는 **남성 시청자만 놓고 봤을 때** 이 연령대가 차지하는 비율이라 분모 자체가 다르다 — 그래서 `maleRatio`를 그대로 남성 탭에 쓸 수 없고 별도 필드로 분리했다.

```text
maleAgeTotal   = lts_male_under10 + lts_male_10s + ... + lts_male_60plus (7개 연령 컬럼의 합)
femaleAgeTotal = lts_female_under10 + lts_female_10s + ... + lts_female_60plus

maleShareRatio(연령대)   = 이 연령대 lts_male_* / maleAgeTotal * 100
femaleShareRatio(연령대) = 이 연령대 lts_female_* / femaleAgeTotal * 100
```

분모는 `lts_male_count`/`lts_female_count` 컬럼이 아니라 **7개 연령 컬럼의 합**을 쓴다. 얼굴 인식은 됐는데 나이 추정만 실패해서 `lts_male_count`가 연령 컬럼 합보다 클 가능성을 완전히 배제할 수 없기 때문이다(v2 스키마에 `count == sum(age)` 제약이 명시되어 있지 않음) — 연령 컬럼 합을 분모로 쓰면 이 문제와 무관하게 7개 연령대 비율의 합이 항상 정확히 100%가 된다.

`maleAgeTotal`(또는 `femaleAgeTotal`)이 0이면 그 성별의 `*ShareRatio`는 전부 `0`으로 응답한다(분모가 0이라 계산 불가).

#### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `genderSummary.maleRatio` | number/null | 남성 시청 비율(%). 토글과 무관하게 항상 하나의 값(우측 상단 범례용) |
| `genderSummary.femaleRatio` | number/null | 여성 시청 비율(%) |
| `ageGroups` | array | 연령대별 시청 비율. 토글 3개 뷰에 필요한 값을 전부 포함 |
| `ageGroups[].ageGroup` | string | `UNDER_10`, `10S`, `20S`, `30S`, `40S`, `50S`, `60_PLUS` |
| `ageGroups[].maleRatio` | number | 이 연령대 남성이 **전체 시청자** 중 차지하는 비율(%). 전체 탭 막대 안 남성 구간 |
| `ageGroups[].femaleRatio` | number | 이 연령대 여성이 **전체 시청자** 중 차지하는 비율(%). 전체 탭 막대 안 여성 구간 |
| `ageGroups[].totalRatio` | number | `maleRatio + femaleRatio`. 전체 탭 막대 길이 |
| `ageGroups[].maleShareRatio` | number | 이 연령대가 **전체 남성** 중 차지하는 비율(%). 남성 탭 막대 길이 |
| `ageGroups[].femaleShareRatio` | number | 이 연령대가 **전체 여성** 중 차지하는 비율(%). 여성 탭 막대 길이 |

#### Response Example

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedPeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "effectivePeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "periodStatus": "IN_EXECUTION",
    "serverTime": "2026-07-07T16:43:25+09:00",
    "aggregationUnit": "HOUR",
    "aggregationCutoffTime": "2026-07-07T16:00:00+09:00",
    "refreshIntervalSec": 3600,
    "genderSummary": {
      "maleRatio": 63.4,
      "femaleRatio": 36.6
    },
    "ageGroups": [
      { "ageGroup": "UNDER_10", "label": "0-9세", "maleRatio": 0.7, "femaleRatio": 0.5, "totalRatio": 1.2, "maleShareRatio": 1.1, "femaleShareRatio": 1.4 },
      { "ageGroup": "10S", "label": "10-19세", "maleRatio": 1.3, "femaleRatio": 0.8, "totalRatio": 2.1, "maleShareRatio": 2.0, "femaleShareRatio": 2.2 },
      { "ageGroup": "20S", "label": "20-29세", "maleRatio": 18.4, "femaleRatio": 11.8, "totalRatio": 30.2, "maleShareRatio": 29.0, "femaleShareRatio": 32.2 },
      { "ageGroup": "30S", "label": "30-39세", "maleRatio": 14.5, "femaleRatio": 7.9, "totalRatio": 22.4, "maleShareRatio": 22.9, "femaleShareRatio": 21.6 }
    ]
  }
}
```

### 시간·연령별 노출도 API

```http
GET /api/v1/dashboard/campaigns/{campaign_id}/hourly-age-exposure?selected_start_date=2026-07-07&selected_end_date=2026-07-07
```

OTS 연령 집계를 기반으로 시간대·연령대별 노출도 히트맵을 응답한다.

화면에 **전체/남성/여성 토글 버튼**이 있다. demographic-view-ratio API와 같은 이유로, 토글마다 API를 다시 호출하지 않고 한 응답에 세 뷰의 값을 모두 담아 내려준다. 프론트는 토글 상태에 따라 `cells[]`에서 어떤 `exposureCount`/`intensityLevel` 쌍을 색칠에 쓸지만 바꾼다 (전체 탭 = `exposureCount`/`intensityLevel`, 남성 탭 = `maleExposureCount`/`maleIntensityLevel`, 여성 탭 = `femaleExposureCount`/`femaleIntensityLevel`).

#### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `hours` | array | 화면에 표시할 시간 축. 예: `06`, `07`, ... (토글과 무관하게 공통) |
| `ageGroups` | array | 화면에 표시할 연령 축 (토글과 무관하게 공통) |
| `cells` | array | 히트맵 셀 목록. 전체/남성/여성 세 뷰의 값을 모두 포함 |
| `cells[].hour` | string | `HH` 형식 시간 |
| `cells[].ageGroup` | string | 연령대 코드 |
| `cells[].exposureCount` | number | 해당 시간·연령대의 전체(남녀 합산) 노출 수. 전체 탭용 |
| `cells[].intensityLevel` | number | 전체 탭 색상 강도 레벨. `1`(Less)~`4`(More), 데이터 없으면 `0` |
| `cells[].maleExposureCount` | number | 해당 시간·연령대의 남성 노출 수. 남성 탭용 |
| `cells[].maleIntensityLevel` | number | 남성 탭 색상 강도 레벨 |
| `cells[].femaleExposureCount` | number | 해당 시간·연령대의 여성 노출 수. 여성 탭용 |
| `cells[].femaleIntensityLevel` | number | 여성 탭 색상 강도 레벨 |

#### intensityLevel 계산

`intensityLevel`/`maleIntensityLevel`/`femaleIntensityLevel`은 **각자 독립적으로** 4단계로 정규화한다. 즉 `maleIntensityLevel`은 이 응답의 `maleExposureCount` 중 최댓값을 기준으로 계산하고, 전체 탭의 최댓값(`exposureCount` 기준)과는 무관하다. 남성/여성 인원 수는 보통 전체 인원 수보다 작으므로, 만약 전체 탭과 같은 기준(같은 최댓값)으로 정규화하면 남성/여성 탭 히트맵이 항상 옅게만 보이게 된다 — 세 탭 모두 "이 성별 안에서 상대적으로 어디가 진한지"를 보여주는 게 목적이라 각자 따로 정규화한다.

```text
maxExposureCount = 이 응답의 cells 중 exposureCount 최댓값
intensityLevel = exposureCount == 0 ? 0 : min(4, ceil(exposureCount / maxExposureCount * 4))

maxMaleExposureCount = 이 응답의 cells 중 maleExposureCount 최댓값
maleIntensityLevel = maleExposureCount == 0 ? 0 : min(4, ceil(maleExposureCount / maxMaleExposureCount * 4))

maxFemaleExposureCount = 이 응답의 cells 중 femaleExposureCount 최댓값
femaleIntensityLevel = femaleExposureCount == 0 ? 0 : min(4, ceil(femaleExposureCount / maxFemaleExposureCount * 4))
```

각 뷰에서 가장 노출이 많은 셀은 항상 레벨 4가 된다. 서버가 세 레벨을 전부 계산해서 내려주므로 프론트는 활성화된 탭에 맞는 필드 값(0~4)에 색상만 매핑하면 된다.

#### Response Example

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "campaignId": 1,
    "selectedPeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "effectivePeriod": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-07"
    },
    "periodStatus": "IN_EXECUTION",
    "serverTime": "2026-07-07T16:43:25+09:00",
    "aggregationUnit": "HOUR",
    "aggregationCutoffTime": "2026-07-07T16:00:00+09:00",
    "refreshIntervalSec": 3600,
    "hours": ["06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16"],
    "ageGroups": [
      { "ageGroup": "UNDER_10", "label": "0-9세" },
      { "ageGroup": "10S", "label": "10-19세" },
      { "ageGroup": "20S", "label": "20-29세" }
    ],
    "cells": [
      { "hour": "06", "ageGroup": "20S", "exposureCount": 182, "intensityLevel": 2, "maleExposureCount": 108, "maleIntensityLevel": 2, "femaleExposureCount": 74, "femaleIntensityLevel": 2 },
      { "hour": "07", "ageGroup": "20S", "exposureCount": 255, "intensityLevel": 3, "maleExposureCount": 150, "maleIntensityLevel": 3, "femaleExposureCount": 105, "femaleIntensityLevel": 3 },
      { "hour": "08", "ageGroup": "20S", "exposureCount": 410, "intensityLevel": 4, "maleExposureCount": 240, "maleIntensityLevel": 4, "femaleExposureCount": 170, "femaleIntensityLevel": 4 }
    ]
  }
}
```
## 8. 에러 케이스

### 캠페인이 없는 경우

```json
{
  "isSuccess": false,
  "code": "CAMPAIGN_404_001",
  "message": "캠페인을 찾을 수 없습니다.",
  "errorDetail": ["campaign_id에 해당하는 캠페인이 없습니다."]
}
```

### 접근 권한이 없는 경우

```json
{
  "isSuccess": false,
  "code": "CAMPAIGN_403_001",
  "message": "캠페인에 접근할 권한이 없습니다.",
  "errorDetail": ["현재 사용자의 팀에 속한 캠페인이 아닙니다."]
}
```

### 기간이 잘못된 경우

선택 기간이 캠페인 집행 기간과 겹치지 않는 것은 에러가 아니다. 이 에러는 시작일이 종료일보다 늦은 경우처럼 요청 기간 자체가 잘못됐을 때만 사용한다.

```json
{
  "isSuccess": false,
  "code": "DASHBOARD_400_001",
  "message": "조회 기간이 올바르지 않습니다.",
  "errorDetail": ["selected_start_date는 selected_end_date보다 늦을 수 없습니다."]
}
```

---

## 9. MVP 보류 항목

| 항목 | 현재 MVP 방식 | 비고 |
|---|---|---|
| 전체 유동인구 DB 스키마 | mock provider 사용 | 추후 서울시 공공데이터 적재 테이블명, 구역 ID 체계, 좌표→구역 매핑 방식 확정 |
| 실제 송출 횟수 | 06:00부터 15초마다 1 증가하는 시간 기반 추정 | 향후 송출 로그 테이블이 생기면 실제 집계로 대체 |
| 다운타임 | 프론트 하드코딩 | 백엔드가 제공할 필요가 생기면 별도 필드 추가 |
| 시간대별 집계 테이블 | 5초 원천 데이터를 API 호출 시 집계 | 트래픽이 커지면 1분/1시간 집계 테이블 또는 캐시 도입 검토 |
| **평균 시청시간 가중평균 전제** | `sum(dwell_sum_sec)/sum(lts_count)` 사용 | ⚠️ 다른 항목과 달리 이건 실제로 아직 미확정 — `dwell_sum_sec`와 `lts_count`가 5초 window마다 동일 모집단 기준인지 Vision AI팀 확인 필요 (7절 참고) |

