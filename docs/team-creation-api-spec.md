# 팀 생성 API 설계 문서

로그인 후 소속된 팀이 없는 사용자에게 노출되는 "팀 생성" 플로우와, 그 이후 팀원 페이지에서
초대 코드를 발급/재발급하는 API의 설계다. 백엔드 개발자 2명이 나눠서 작업하는 것에 맞춰
API 단위로 담당을 분리했다 — 상세 내용은 "2. 담당자 요약" 참고.

같은 화면에 있는 "팀 합류"(발급된 코드를 입력해서 실제로 합류하는 쪽)는 이번 범위가 아니다 —
`TeamInviteQueryService.validateAndGetId()`로 1차 검증 로직만 있고, 실제로 `team_members` row를
만드는 "초대 수락" 컨트롤러/트랜잭션은 아직 없다(별도 작업). 팀원 페이지의 나머지 기능(멤버
목록 조회, 역할 변경, 강퇴 등)도 이번 범위가 아니다 — "초대 코드 발급" 버튼 하나만 다룬다.

---

## 1. 전체 흐름

### 1-1. 팀 생성 (백엔드 B → 백엔드 A로 이어짐)

```
[로그인 후 소속 팀 없음 확인]
        |
        v
  [팀 생성 화면 진입]
        |
        v
1. 사용자가 사업자등록증 파일을 선택/드래그
        |
        v
2. 프론트 -> 백엔드(B): 사업자등록증 업로드 API 호출
        |
        v
   백엔드(B):
     a. 파일을 S3(private)에 저장
     b. Lambda(OCR)를 서버-to-서버로 invoke (S3 key만 전달, 파일 바이트 재전송 안 함)
     c. OCR 결과 수신
        |
        v
   백엔드(B) -> 프론트: OCR로 추출된 필드 + documentStorageKey 응답
        |
        v
3. 프론트 화면에 OCR 결과가 채워진 폼 표시, 사용자가 값 확인/수정 (백엔드 호출 없음)
        |
        v
4. 사용자가 "팀 생성하기" 클릭
        |
        v
5. 프론트 -> 백엔드(B): 팀 생성 API 호출 (수정된 필드 + documentStorageKey)
        |
        v
   백엔드(B): 외부 사업자등록정보 조회 API로 진위 확인
   (트랜잭션을 시작하기 전에 먼저 호출한다 - 외부 API 응답을 기다리는 동안
    DB 커넥션/트랜잭션을 붙들고 있지 않기 위해)
        |
        +--- 유효하지 않음 ---> 에러 응답, 아무 row도 만들지 않음
        |                        (프론트: 에러 메시지 표시, 같은 화면에 그대로 머무름)
        |
       유효함
        |
        v
   백엔드(B): 하나의 트랜잭션으로
     a. teams row 생성
     b. team_members row 생성 (요청자 = OWNER)
     c. team_business_registrations row 생성 (verificationStatus = APPROVED, verifiedAt = now -
        외부 API로 이미 확인됐으므로 PENDING 없이 바로 승인 상태로 만든다)
        |
        v
   백엔드(B) -> 프론트: 팀 정보(teamId 포함, 초대 코드는 없음) 응답
        |
        v
6. 프론트 -> 백엔드(A): 초대 코드 발급 API 호출 (5번에서 받은 teamId로 곧바로 이어서 호출)
        |
        v
   백엔드(A): team_invite_links row 생성 (7자리 랜덤 코드를 해시해서 저장 - 새로 만든
   팀이라 활성 코드가 아직 없으므로 폐기 절차 없이 바로 발급)
        |
        v
   백엔드(A) -> 프론트: 초대 코드(평문, 이 응답에서만 내려감) 응답
        |
        v
7. 프론트: "팀원 초대하기" 팝업에 초대 코드 표시
   (사용자가 코드를 복사해서 팀원에게 전달 - 카카오톡 등으로 전달하는 행위 자체는
    시스템 밖에서 일어나고, 백엔드는 관여하지 않는다)
        |
        v
8. 사용자가 팝업을 닫으면 홈 화면으로 리다이렉션
```

**5번과 6번은 서로 다른 백엔드가 만드는 별개의 API 호출이다.** 프론트가 5번 응답의
`teamId`를 받아서 곧바로 6번을 호출하는 구조 — 하나의 트랜잭션으로 묶여있지 않다. 이 트레이드
오프는 "아키텍처 결정 사항"에 이유를 적어뒀다.

### 1-2. 팀원 페이지에서 초대 코드 재발급 (백엔드 A 단독)

```
[팀원 페이지] -> "팀원 초대하기" 버튼 클릭
        |
        v
프론트 -> 백엔드(A): 초대 코드 발급 API 호출 (1-1의 6번과 동일한 API)
        |
        v
   백엔드(A): 하나의 트랜잭션으로
     a. 이 팀의 기존 활성 코드가 있으면 폐기(revoke)
     b. 새 코드 생성 (6절 "초대 코드 생성 규칙")
        |
        v
   백엔드(A) -> 프론트: 새 초대 코드(평문) 응답
        |
        v
프론트: 팀 생성 때와 같은 형태의 "팀원 초대하기" 팝업에 (새) 초대 코드 표시
```

1-1의 6번과 1-2는 **완전히 같은 API**다. 새로 만든 팀은 활성 코드가 없으니 "폐기할 게
없어서 그냥 새로 발급"되는 것이고, 이미 코드가 있는 팀은 "폐기 후 재발급"되는 것뿐이다 —
버튼을 누를 때마다 항상 새 코드가 나온다는 동작은 두 경우 모두 동일하다.

### 아키텍처 결정 사항

- **OCR Lambda는 프론트가 아니라 백엔드(B)가 호출한다.** 프론트가 직접 Lambda/API Gateway를
  부르면 인증(JWT 인가자 필요)과 CORS를 별도로 관리해야 하고, 인증 없이 열려 있으면 OCR
  비용 남용 리스크가 있다. 백엔드가 이미 검증한 JWT 세션 안에서 호출하면 이 문제가 전부
  없어진다.
- **사업자등록증 이미지는 private 스토리지에만 저장한다.** 대표자 성명·사업장 주소·사업자등록번호가
  같이 찍혀 있는 민감 문서라 공개 URL을 만들지 않는다 (`docs/mvp-database-erd.md` 5절에 이미
  명시된 정책과 동일). 이번 플로우 안에서는 프론트가 이미지 자체에 접근할 필요가 없으므로
  (OCR 결과 텍스트만 주고받음) presigned URL조차 필요 없다. 나중에 이미지 미리보기/검수 화면이
  생기면 그때 "권한 확인 후 짧은 만료시간의 서명 URL 발급" 엔드포인트를 추가한다.
- **Lambda는 API Gateway가 아니라 SDK로 직접 invoke한다.** API Gateway를 거치면 동기 호출
  기준 29초 타임아웃이 있는데, SDK로 직접 invoke하면 이 제한이 없다. EC2 인스턴스 역할에
  `lambda:InvokeFunction` 권한만 추가하면 된다.
- **사업자등록 검증은 팀 생성 요청 안에서 동기적으로 이루어진다.** 유효하지 않은 사업자등록증으로
  일단 팀을 만들고 나중에 반려하는 것보다, 애초에 유효한 경우에만 팀이 생기는 쪽이 사용자
  경험(그 자리에서 바로 에러를 알려줌)과 데이터 정합성(반려된 팀이 DB에 남지 않음) 모두에
  낫다고 판단했다.
- **팀 생성과 초대 코드 발급은 하나의 트랜잭션으로 묶지 않고, 별개의 API로 분리한다.**
  두 사람이 나눠 작업하는 구조라 — 하나로 묶으면 백엔드 B의 팀 생성 코드가 백엔드 A의
  `team_invite_links` 로직에 의존하게 되어 서로 독립적으로 개발/배포하기 어려워진다.
  대신 프론트가 "팀 생성 API 성공 -> 곧바로 초대 코드 발급 API 호출"의 2단계로 호출한다.
  트레이드오프: 팀 생성은 성공했는데 초대 코드 발급 호출이 실패하는 경우가 이론상 있을 수
  있다. 이 API는 호출마다 기존 코드를 폐기하고 새 코드를 발급하므로 멱등하지 않다. 프론트는
  자동 재시도하지 않고, 실패 시 사용자가 명시적으로 "다시 발급"을 눌러 재호출하게 한다.
  이 경우 팀 생성을 다시 할 필요는 없다.
- **초대 코드는 새 테이블을 만들지 않고 기존 `team_invite_links`를 그대로 쓴다.** 이미
  `V2__campaign_brand_name_invite_code_business_fields.sql`에서 "코드 입력 방식 초대"로
  개편이 끝나 있는 테이블이다 — 역할 선택 컬럼(`default_role`)은 제거되어 항상 MEMBER로
  합류하고, `max_uses`는 nullable(NULL = 무제한)이고, 팀당 폐기되지 않은 코드가 1개만
  존재하도록 `active_code_marker` 유니크 제약도 이미 걸려 있다. "코드 하나를 여러 팀원이
  같이 쓰고, 재발급하면 기존 코드는 무효화된다"는 요구사항과 정확히 맞아떨어지는 구조라
  그대로 재사용한다. 코드 해싱도 초대 링크 검증(`TeamInviteQueryService`)에서 이미 쓰고 있는
  `TokenHasher.sha256()`을 동일하게 쓴다.

---

## 2. 담당자 요약

| 구분 | 담당 | 섹션 |
|---|---|---|
| 사업자등록증 업로드 API (S3 저장 + OCR Lambda 연동) | 백엔드 B | 4절 |
| 외부 사업자등록 진위확인 API 연동 | 백엔드 B | 5절 |
| 팀 생성 API (teams/team_members/team_business_registrations) | 백엔드 B | 5절 |
| 초대 코드 발급 API (team_invite_links) | 백엔드 A | 6절 |
| 팀원 페이지(목록 조회/역할 변경/강퇴 등, 초대 코드 발급 버튼 제외) | 백엔드 A | 미설계 - 별도 논의 필요 |
| 팀 합류(코드 입력 → 실제 합류) | 미정 | 이번 범위 밖, "이번 범위에서 제외" 참고 |

백엔드 B는 Spring 코드와 Lambda를 전부 직접 다룬다(OCR Lambda 작성/배포 포함). 백엔드 A는
Spring 쪽 초대 코드 API만 다루고 Lambda와는 접점이 없다.

두 사람의 API는 `teamId`로만 연결된다 — 백엔드 B의 팀 생성 API 응답에 담긴 `teamId`를
프론트가 그대로 백엔드 A의 초대 코드 발급 API 경로에 넣어서 호출하는 구조라, 서로의 내부
구현을 몰라도 각자 독립적으로 개발/테스트할 수 있다.

---

## 3. API 목록

| 기능명 | 담당 | HTTP | 엔드포인트 | 설명 |
|---|---|---|---|---|
| 사업자등록증 업로드 (OCR 포함) | 백엔드 B | POST | `/api/v1/teams/business-registration` | 파일을 S3에 저장하고 OCR 결과를 응답 |
| 팀 생성 | 백엔드 B | POST | `/api/v1/teams` | 사업자등록 진위 확인 후 팀 + 팀원(OWNER) + 사업자등록 정보를 생성 |
| 초대 코드 발급 | 백엔드 A | POST | `/api/v1/teams/{teamId}/invite-code` | 팀원 초대용 코드를 새로 발급 (기존 활성 코드가 있으면 폐기) |

앞의 두 API(백엔드 B)는 "인증된 사용자인가"만 확인하면 된다 (아직 팀이 없는 시점). 초대
코드 발급 API(백엔드 A)는 다르다 — 이미 존재하는 팀에 대한 작업이라 "이 사용자가 이 팀
소속인가"(+역할 제한 여부, "확인 필요 항목" 참고)까지 확인해야 한다.

---

## 4. 사업자등록증 업로드 API — 담당: 백엔드 B

파일을 받아 S3에 저장하고, OCR로 추출한 필드를 즉시 응답한다. 이 시점엔 아직 팀이 생성되지
않으므로 DB에는 아무것도 저장하지 않는다 — S3 업로드만 일어난다.

### Request

```http
POST /api/v1/teams/business-registration
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data

file: (binary)
```

### 처리 순서

1. 파일 검증 — 확장자만으로 판단하지 않는다(확장자만 바꾼 파일로 우회 가능). 매직 바이트/MIME
   타입 검사 + 이미지는 디코딩 성공 여부까지 확인, PDF는 페이지 수/암호화 여부도 확인한다.
   구체적인 허용 목록·용량 상한 값은 "확인 필요 항목" 참고
2. S3에 저장. 키는 추측 불가능한 값으로 생성한다 (예: `team-registrations/{uuid}.{ext}`).
   업로드가 성공한 뒤에만 5절의 업로드 토큰을 발급한다.
3. Lambda를 동기 invoke해서 OCR 요청 — 페이로드로 원본 파일을 다시 보내지 않고 S3 버킷/키만
   전달한다 (Lambda가 자기 IAM 권한으로 S3에서 직접 읽음). 동기 invoke 페이로드 자체엔 6MB
   제한이 있어 파일을 직접 실어 보내면 큰 이미지에서 걸릴 수 있다.
4. OCR 결과를 파싱해서 응답 필드에 매핑. **OCR이 실패하거나 특정 필드를 못 읽어도 업로드
   자체는 성공으로 처리한다** — 실패한 필드는 `null`로 내려주고, 프론트에서 사용자가 직접
   입력하게 한다. OCR 성패가 업로드 성공 여부를 좌우하지 않는다.

화면에는 팀명/사업자명/대표자명/사업자등록번호/개업일자 5개만 보이고 전부 사용자가 직접
수정할 수 있다 — 그래서 OCR도 이 중 문서에 실제로 인쇄돼 있는 4개(사업자명/대표자명/
사업자등록번호/개업일자)만 추출하면 된다. 업태·사업장 소재지는 이번 화면에 아예 없으므로
OCR 대상이 아니다 (DB 컬럼 자체는 nullable로 남아있고, 이 플로우에서는 항상 값이 안 채워질 뿐).

### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `documentStorageKey` | string | 업로드된 파일을 가리키는 서명된 토큰(문자열). 프론트는 내용을 해석할 필요 없이 그대로 받아서 팀 생성 API에 그대로 실어 보낸다 — 자세한 내용은 5절 "documentStorageKey 검증" 참고 |
| `ocrResult.companyName` | string/null | OCR로 추출한 사업자명. 인식 실패 시 `null` |
| `ocrResult.representativeName` | string/null | OCR로 추출한 대표자명 |
| `ocrResult.businessNumber` | string/null | OCR로 추출한 사업자등록번호 (하이픈 제거) |
| `ocrResult.businessOpeningDate` | string/null | OCR로 추출한 개업일 (`yyyy-MM-dd`). 팀 생성 API에서는 필수 필드라, OCR이 못 읽으면 사용자가 반드시 직접 입력해야 한다 |

### Response Example

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "documentStorageKey": "eyJrIjoidGVhbS1yZWdpc3RyYXRpb25zLzNmMWE5YzJlLS4uLi5wbmciLCJ1IjoxLCJlIjoxNzUyMTMzNjAwfQ.9f2a...",
    "ocrResult": {
      "companyName": "신한 KLLJS",
      "representativeName": "이정헌",
      "businessNumber": "4959240582",
      "businessOpeningDate": null
    }
  }
}
```

### 에러 케이스

| 상황 | 코드 |
|---|---|
| 파일이 없음 | `400` |
| 지원하지 않는 파일 형식/용량 초과 | `400` |
| S3 업로드 자체가 실패 (인프라 문제) | `500` |

OCR 인식 실패는 에러가 아니다 — 위에서 설명한 대로 `ocrResult`의 해당 필드만 `null`이 된다.

---

## 5. 팀 생성 API — 담당: 백엔드 B

팀, 팀원(OWNER), 사업자등록 정보를 한 트랜잭션으로 생성한다. 초대 코드는 여기서 만들지
않는다 — 팀 생성 성공 직후 프론트가 이어서 6절(백엔드 A) API를 호출한다. 트랜잭션을
시작하기 전에 외부 API로 사업자등록 진위를 먼저 확인하고, 유효하지 않으면 트랜잭션 자체를
시작하지 않는다 — 즉 실패 시 어떤 row도 생기지 않는다.

### Request

```http
POST /api/v1/teams
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "teamName": "신한 KLLJS 딥비전스 옥외 광고 3팀",
  "companyName": "신한 KLLJS",
  "representativeName": "이정헌",
  "businessNumber": "4959240582",
  "businessOpeningDate": "2020-03-02",
  "documentStorageKey": "eyJrIjoidGVhbS1yZWdpc3RyYXRpb25zLzNmMWE5YzJlLS4uLi5wbmciLCJ1IjoxLCJlIjoxNzUyMTMzNjAwfQ.9f2a..."
}
```

### Request Fields

| 필드 | 필수 | 타입 | 설명 |
|---|---:|---|---|
| `teamName` | Y | string | 팀명 |
| `companyName` | Y | string | 사업자명. 화면상 필수 입력이라 API 레벨에서도 필수로 검증 (DB 컬럼 자체는 nullable, "확인 필요 항목" 참고) |
| `representativeName` | Y | string | 대표자명. 위와 동일하게 API 레벨 필수 |
| `businessNumber` | Y | string | 사업자등록번호. 위와 동일하게 API 레벨 필수 |
| `businessOpeningDate` | Y | string | 개업일 (`yyyy-MM-dd`). 외부 진위확인 API 호출에 필요해서 필수로 확정 |
| `documentStorageKey` | Y | string | 업로드 API에서 받은 서명 토큰 |

`businessType`/`businessAddress`는 화면에 입력란 자체가 없어서 이번 API에는 없다 (해당 DB
컬럼은 nullable이라 그대로 두면 되고, 이 플로우로 생성된 팀은 항상 `null`로 남는다).

### 요청값 정규화 및 시간 규칙

- `teamName`/`companyName`/`representativeName`은 앞뒤 공백을 제거한 뒤 빈 문자열을 거부한다.
  최대 길이는 각각 DB 컬럼 길이인 200/200/100자다.
- `businessNumber`는 하이픈과 공백을 제거한 뒤 숫자 10자리만 허용하고, 정규화된 값으로 외부
  진위확인 API 호출 및 DB 저장을 수행한다.
- `businessOpeningDate`는 `yyyy-MM-dd`의 실제 날짜여야 하며 미래 날짜는 거부한다.
- 서버 내부의 `now`, 토큰 만료 시각, `verifiedAt`은 UTC `Instant` 기준으로 처리한다. DB에는
  UTC로 저장하고, API가 시각을 응답해야 할 때만 KST ISO-8601 오프셋으로 변환한다.

### documentStorageKey 검증

4절 업로드 API가 내려주는 `documentStorageKey`는 원본 S3 키가 아니라, 서버 비밀키로 서명한
불투명 업로드 토큰이다. 토큰 payload는 아래 필드를 가지며, Base64URL 인코딩한 payload 뒤에
HMAC-SHA-256 서명을 붙인다.

```text
v1.{base64url(payload)}.{base64url(hmacSha256(payload, uploadTokenSecret))}
payload = { purpose: "business-registration-upload", s3Key, uploaderId, expiresAtEpochSecond }
```

`TokenHasher`는 단방향 SHA-256 해시 도구이므로 이 용도로 재사용하지 않는다. 별도의
`BusinessRegistrationUploadTokenSigner`를 두고, JWT 서명 키와 분리된
`BUSINESS_REGISTRATION_UPLOAD_TOKEN_SECRET`으로 서명/검증한다. DB 스키마나 새 테이블 없이,
팀 생성 시점에 다음을 검증한다:

1. 토큰 버전과 `purpose`가 기대한 값인가
2. HMAC 서명이 유효한가 (위변조 여부)
3. 만료되지 않았는가 (`expiresAtEpochSecond`, UTC)
4. 토큰에 담긴 `uploaderId`가 이 요청의 JWT `sub`(요청자)와 같은가
5. `s3Key`가 서버가 발급하는 `team-registrations/` prefix 형식인가

모든 조건을 통과하면 토큰 안의 실제 `s3Key`를 꺼내 `document_storage_key` 컬럼에 그대로
저장한다 (DB에는 원래대로 순수 S3 키만 남는다 - 서명 토큰은 API 레벨에만 존재하는 값이다).
S3에 그 객체가 실제로 존재하는지는 별도로 조회하지 않는다 — 서명 토큰은 업로드가 성공한
뒤에만 발급되고(4절 처리 순서 2단계 참고), 토큰 만료(1시간)가 orphan 정리 배치의 최소 삭제
기준(2일 이상, "확인 필요 항목" 참고)보다 훨씬 짧아서 토큰이 유효한 동안에는 그 객체가
정리 배치로 지워질 수 없다. 즉 서명이 유효하다는 것 자체로 "그 객체가 지금도 존재한다"는
보증이 성립하므로, 팀 생성 경로에서 S3를 다시 조회하는 API 호출과 그에 따른 에러 케이스를
늘릴 이유가 없다.

만료 시간은 **1시간**을 제안한다 (업로드 후 폼을 확인/수정하고 제출하기까지 충분한 여유 +
분실/유출돼도 노출 기간이 짧음). 다른 사용자가 이 토큰 문자열을 어떻게든 손에 넣어도
`uploaderId`가 자신과 다르면 4번에서 막히므로, 다른 사람이 업로드한 사업자등록증으로 팀을
만드는 건 불가능하다. (같은 사용자가 같은 토큰으로 여러 팀을 만드는 것까지는 막지 않는다 —
MVP 범위에서는 신경 쓰지 않기로 함)

### 처리 순서

0. **(트랜잭션 시작 전)**
   a. 요청 필드를 정규화/형식 검증한다 (위 "요청값 정규화 및 시간 규칙" 참고).
   b. `documentStorageKey`를 검증한다 (위 "documentStorageKey 검증" 참고). 실패하면 즉시
      에러 응답한다.
   c. 외부 사업자등록정보 조회 API를 정규화된 `businessNumber`(+ API가 요구하는 다른 파라미터,
      "확인 필요 항목" 참고)로 호출해서 진위를 확인한다. 유효하지 않으면 즉시 에러 응답한다.
   0-a~0-c의 세 단계가 모두 통과해야 아래 단계로 진행한다.
1. `teams` row 생성 (`teamName`, `status = ACTIVE`)
2. `team_members` row 생성 (`team` = 1에서 만든 팀, `user` = 요청자, `role = OWNER`,
   `status = ACTIVE`, `joinedAt = now`, `joinedViaInvite = null`)
3. `team_business_registrations` row 생성 (`team` = 1에서 만든 팀, `uploadedBy` = 요청자,
   `companyName`/`representativeName`/`businessNumber`/`businessOpeningDate` = 요청값 그대로,
   `businessType`/`businessAddress` = `null`, `documentStorageKey` = 0-b에서 꺼낸 실제 S3 키,
   `verificationStatus = APPROVED`, `verifiedAt = now`)

1~3단계 중 하나라도 실패하면 전체 롤백한다 (0단계는 트랜잭션 시작 전이라 롤백 대상이 아니다
— 실패하면 애초에 DB에 아무것도 쓰지 않은 상태다).

`verificationStatus`는 이 흐름에서 항상 `APPROVED`로 바로 만들어진다 — `PENDING`/`REJECTED`는
이 API에서는 도달하지 않는 상태다 (유효하지 않으면 0단계에서 걸러져서 row 자체가 안 생긴다).
엔티티에 이미 있는 `resubmit()`/`reject()` 메서드는 추후 다른 플로우(예: 사후 재검증, 관리자
수동 반려)를 위해 남겨둔 것으로 보고 이번 범위에서는 건드리지 않는다.

### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `teamId` | number | 생성된 팀 ID. 프론트가 곧바로 6절 초대 코드 발급 API 호출에 사용한다 |
| `teamName` | string | 팀명 |
| `verificationStatus` | string | 항상 `APPROVED` (외부 API로 이미 진위 확인을 통과한 뒤에만 팀이 생성되므로) |

### Response Example

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "teamId": 12,
    "teamName": "신한 KLLJS 딥비전스 옥외 광고 3팀",
    "verificationStatus": "APPROVED"
  }
}
```

### 에러 케이스

| 상황 | 코드 |
|---|---|
| 필수 필드 누락 (`teamName`/`companyName`/`representativeName`/`businessNumber`/`businessOpeningDate`/`documentStorageKey`) | `400` |
| `documentStorageKey` 서명이 유효하지 않거나 위변조됨, 또는 만료됨 | `400` |
| `documentStorageKey`에 담긴 업로더가 요청자와 다름 | `400` |
| 사업자등록증 진위 확인 실패 (외부 API가 "유효하지 않음"으로 응답) | `400` |
| 외부 사업자등록 조회 API 호출 실패/타임아웃 (인프라 문제) | `500` |

---

## 6. 초대 코드 발급 API — 담당: 백엔드 A

팀 생성 직후(5절 성공 이후 프론트가 곧바로 호출) **또는** 팀원 페이지의 "팀원 초대하기"
버튼에서 호출한다 — 두 경우 모두 완전히 같은 API를 쓴다. 누를 때마다 항상 새 코드를
발급한다 — 기존 활성 코드가 있어도 그대로 보여주지 않고, 폐기 후 새로 하나 더 만든다
(처음 호출이라 폐기할 게 없으면 그 단계는 그냥 아무 일도 안 하고 넘어간다).

### Request

```http
POST /api/v1/teams/{teamId}/invite-code
Authorization: Bearer {accessToken}
```

(요청 본문 없음)

### 접근 권한

- 팀이 존재하지 않으면 `404`
- 요청자가 이 팀에 `ACTIVE` 상태로 속해 있지 않으면 `403` (기존 캠페인 API들의
  `getAccessibleCampaign()`과 동일한 패턴 — 팀 없으면 404, 있어도 권한 없으면 403)
- 팀 생성 직후 호출되는 경우, 요청자는 5절에서 이미 OWNER로 등록된 상태이므로 이 체크를
  그대로 통과한다 — 별도 예외 처리가 필요 없다.
- **역할은 `OWNER`/`ADMIN`만 허용, `MEMBER`는 `403`.** `mvp-database-erd.md` 4절 "권한 기준"
  표에 이미 "초대" 권한이 `OWNER`/`ADMIN` 행에만 있고 `MEMBER` 행에는 없다고 명시돼 있다 —
  이 문서에서 새로 정하는 게 아니라 기존 정책을 그대로 따르는 것이다.

### 처리 순서 (트랜잭션)

0. **`teams` 행을 `SELECT ... FOR UPDATE`로 잠근다.** 같은 팀에 대한 동시 요청을 직렬화하기
   위해서다 — 두 요청이 동시에 "활성 코드 없음/현재 코드"를 읽고 각자 새 코드를 insert하면
   `active_code_marker` 유니크 제약(팀당 활성 코드 1개)에 걸려 하나는 예외로 실패한다.
   `team_invite_links`에는 최초 발급 시점엔 잠글 행 자체가 없으므로, 대신 항상 존재하는
   `teams` 행을 잠근다 — `TeamRepository`에 잠금 조회 메서드를 추가해야 한다(현재는
   `JpaRepository`만 상속한 빈 인터페이스라 커스텀 쿼리가 없음).
1. 이 팀의 현재 활성 코드(`revoked_at IS NULL`)가 있으면 조회해서 폐기(`revoke(now)`) —
   없으면(팀 생성 직후 최초 호출) 이 단계는 건너뛴다
2. 새 코드 생성 — 아래 "초대 코드 생성 규칙"
3. 새 `team_invite_links` row insert (`team`, `createdBy` = 요청자, `tokenHash`, `maxUses = null`,
   `expiresAt`, `revokedAt = null`)

0단계의 잠금 덕분에 같은 팀에 대한 동시 요청은 하나씩 순서대로만 처리된다 — 뒤에 처리되는
요청은 앞선 요청이 커밋한 새 코드를 "현재 활성 코드"로 보고 다시 폐기 후 재발급하게 되므로,
경합 상황에서도 최종적으로 팀에는 활성 코드가 정확히 1개만 남는다. 1~3단계 중 하나라도
실패하면 전체 롤백된다(같은 트랜잭션) — 기존 코드는 폐기했는데 새 코드 발급에 실패해서
팀에 활성 코드가 하나도 없는 상태가 되는 걸 방지한다.

### 초대 코드 생성 규칙

- **문자셋/길이**: 영어 대문자(A-Z) + 숫자(0-9) 36자 중에서 7자리를 무작위로 뽑는다
  (예: `7F3K9QX`). 조합 수는 36^7 ≈ 780억 가지로, 한 팀이 코드 하나를 쓰는 수준에서
  중복 걱정은 거의 없다.
- **저장 방식**: 초대 링크 검증(`TeamInviteQueryService`)과 동일하게 평문은 저장하지 않고
  `TokenHasher.sha256()`으로 해시한 값만 `token_hash`에 저장한다. **평문 코드는 이 API
  응답에서 딱 한 번만 내려간다** — 이후에는 해시만 남아 있어 백엔드도 평문을 다시 복원해서
  보여줄 수 없다. (분실 시 이 API를 다시 호출해서 새 코드를 받으면 된다 — 기존 코드는
  무효화됨)
- **충돌 처리**: `token_hash`에 이미 UNIQUE 제약이 걸려 있으므로, 극히 낮은 확률로 같은
  평문 코드가 이미 존재해서 insert가 실패하면 새 코드를 다시 뽑아 재시도한다 (예: 최대 3회).
- **사용 횟수 제한 없음(`maxUses = null`)**: 한 코드를 여러 팀원이 각자 입력해서 합류하는
  흐름이라, 특정 인원수로 막지 않는다.
- **만료 기간**: **발급일로부터 1년**으로 확정 (DB 스키마상 `expires_at`은 `NOT NULL`이라
  값을 반드시 넣어야 한다). 사용자가 능동적으로 재발급 버튼을 누르지 않는 한 자동 갱신되진
  않으므로, 방치된 팀이 초대 자체를 못 하게 되는 걸 막기 위해 길게 잡았다.

### Response Fields

| 필드 | 타입 | 설명 |
|---|---|---|
| `inviteCode` | string | 새로 발급된 초대 코드 (평문, 7자리) |
| `inviteCodeExpiresAt` | string(ISO-8601) | 새 초대 코드의 만료 시각 |

### Response Example

```json
{
  "isSuccess": true,
  "code": "COMMON_200_001",
  "message": "성공적으로 요청을 처리했습니다.",
  "result": {
    "inviteCode": "K2M8XZ1",
    "inviteCodeExpiresAt": "2027-07-17T10:12:00+09:00"
  }
}
```

### 에러 케이스

| 상황 | 코드 |
|---|---|
| `teamId`가 존재하지 않음 | `404` |
| 요청자가 이 팀 소속이 아님(또는 역할 권한 부족) | `403` |
| 초대 코드 재시도 횟수를 모두 소진 (극히 드문 케이스) | `500` |

---

## 7. 확인/결정 필요 항목

이 문서를 구현으로 옮기기 전에 정해야 하는 것들이다. 담당자별로 묶어뒀다.

### 백엔드 B 관련

1. **외부 사업자등록 조회 API의 정확한 스펙.** `businessOpeningDate`를 필수로 확정했으니
   필드 구성 자체는 더 이상 막히지 않지만, 실제로 어떤 API를 호출할지(엔드포인트, 인증 방식,
   "유효/무효" 판정을 어떤 응답 필드로 구분하는지)는 아직 정해지지 않았다 — Backend B가
   실제 연동을 시작하기 전에 확인 필요.
2. **외부 API 장애/타임아웃 시 처리 방침.** 지금 문서는 "실패하면 팀 생성 자체를 500으로
   실패시킨다"로 가정했다. 대안으로 이럴 때만 `verificationStatus = PENDING`으로 일단
   만들어두고 나중에 재검증하는 방식도 가능한데, 그러면 "PENDING인 동안 팀을 쓸 수 있는가"
   질문이 이 예외 케이스에 한해 다시 살아난다 — 어느 쪽으로 할지 확인 필요.
3. **업로드 파일 검증 기준.** 확장자 검사만으로는 우회가 쉬우므로(확장자만 바꾼 악성 파일),
   실제 파일 시그니처(매직 바이트)/MIME 타입 검사, 이미지 파일은 디코딩까지 성공하는지 확인,
   PDF는 페이지 수 상한과 암호화된 PDF 허용 여부까지 포함해서 검증해야 한다 — 다만 허용
   확장자 목록, 최대 용량, 이미지 해상도 하한 등 구체적인 값 자체는 아직 확인 필요.
4. **DB 컬럼은 nullable인데 API는 필수로 막는 것이 맞는가.** `mvp-database-erd.md` 5절 기준
   `company_name`/`representative_name`/`business_number`는 컬럼 자체는 nullable이다 (재제출
   플로우 등을 고려한 설계로 추정). 화면은 전부 필수 입력(`*`)이라, 이 문서에서는 API 레벨
   유효성 검사로 필수 처리했다 — 이 판단이 맞는지 확인 필요.
5. **업로드 후 팀 생성을 안 하고 이탈하면 S3에 파일이 orphan으로 남는다.** 새 업로드 테이블을
   쓰지 않으므로, 정리 배치는 `team-registrations/*` 중 N일이 지난 객체를 대상으로
   `team_business_registrations.document_storage_key`에 참조가 없는 경우만 삭제한다. 토큰 만료가
   1시간이므로 N은 최소 2일 이상으로 둔다. 대량 데이터에서는 S3 목록을 페이지 단위로 읽고,
   DB 참조 키를 batch 조회해 삭제 대상을 결정한다.

### 백엔드 A 관련

6. **혼동되는 문자(0/O, 1/I) 제외 여부.** 요청하신 스펙은 대문자+숫자 36자 전체를 그대로
   쓰는 것이라 이 문서도 그렇게 반영했다. 다만 사람이 코드를 직접 읽고 옮겨 적는 상황이 잦다면
   (예: 전화로 불러주기) 0/O, 1/I처럼 헷갈리는 문자를 빼고 32자 정도로 줄이는 것도 흔한 관행이다
   - 필요하면 반영하겠다.

---

## 8. 백엔드 B 구현/배포 체크리스트

### 구현 계약

- 업로드 토큰은 `BusinessRegistrationUploadTokenSigner`만 발급/검증한다. HMAC 키는
  `BUSINESS_REGISTRATION_UPLOAD_TOKEN_SECRET` 환경변수로 주입하고, 최소 256비트의 무작위 값을
  SSM `SecureString`에 저장한다. 키를 교체하면 최대 1시간 동안 기존 업로드 토큰이 무효화될 수
  있으므로, 교체는 팀 생성 화면을 사용하지 않는 시간대에 수행한다.
- S3 업로드는 서버가 발급한 `team-registrations/` prefix로만 수행한다. 애플리케이션 역할에는
  해당 prefix의 `PutObject` 권한과 orphan 정리용 `DeleteObject` 권한만 부여한다 — 팀 생성
  시점에 S3를 다시 조회하지 않으므로(5절 "documentStorageKey 검증" 참고) `GetObject`/
  `HeadObject`는 필요 없다. OCR Lambda 역할에는 `GetObject` 권한만 부여한다. 버킷은 private
  상태를 유지한다.
- OCR Lambda 요청 계약은 `{ bucket, key }`, 응답 계약은
  `{ companyName, representativeName, businessNumber, businessOpeningDate }`로 고정한다. 각 OCR
  필드는 인식 실패 시 `null`을 허용한다. Lambda 호출 또는 응답 파싱 자체가 실패해도 업로드는
  성공으로 처리하고 모든 OCR 필드를 `null`로 내려주되, 서버에는 원인 로그를 남긴다.
- 외부 사업자 진위확인 API의 선택은 백엔드 B가 담당한다. 선택한 API는
  `BusinessRegistrationVerifier` 같은 내부 인터페이스 뒤에 감추고, API 키는 SSM `SecureString`으로
  관리한다. 외부 API가 바뀌어도 이 문서의 프론트 요청/응답 계약은 바꾸지 않는다.

### 필수 테스트

- 업로드 토큰: 정상, 서명 위변조, 만료, 다른 사용자 토큰, 잘못된 prefix
- 요청값: 공백/하이픈 정규화, 사업자번호 자릿수 오류, 미래 개업일, 문자열 길이 초과
- 외부 검증: 유효, 무효, 타임아웃/5xx
- 트랜잭션: 팀/OWNER/사업자등록 중 어느 저장 단계가 실패해도 전체 롤백
- OCR: 일부 필드 또는 Lambda 자체 실패는 업로드 성공, OCR 필드는 모두 `null`

---

## 9. 백엔드 A 구현/배포 체크리스트

### 구현 계약

- 동시성 제어: `TeamRepository`에 팀 행을 잠그는 조회 메서드를 추가한다
  (`@Lock(LockModeType.PESSIMISTIC_WRITE)`, 예: `findByIdForUpdate(Long teamId)`). 초대 코드
  발급 트랜잭션은 이 메서드로 `teams` 행을 잠근 뒤에만 활성 코드 조회/폐기/재발급을
  진행한다(6절 "처리 순서" 0단계 참고). 현재 `TeamRepository`는 `JpaRepository`만 상속한
  빈 인터페이스라 이 메서드가 없다.
- 역할 검사: `TeamMemberRepository.existsByUserIdAndTeamIdAndStatus()`는 `boolean`만
  반환해서 역할 판단에는 못 쓴다. 요청자의 `TeamMemberRole`까지 반환하는 조회를 추가하고,
  `OWNER`/`ADMIN`이 아니면 `403`으로 응답한다.
- 코드 생성/해싱은 6절 "초대 코드 생성 규칙"을 그대로 따른다 — 문자셋(대문자+숫자 7자리),
  `TokenHasher.sha256()`, 충돌 시 재시도 최대 3회, 만료 1년.

### 필수 테스트

- 인가: 팀 미존재 `404`, 요청자가 팀 소속 아님 `403`, `MEMBER` `403`, `OWNER`/`ADMIN` 성공
- 최초 발급: 활성 코드가 없는(방금 생성된) 팀에서 폐기 단계 없이 정상 발급되는지
- 동시성: 같은 팀에 재발급 요청 2건을 동시에 보냈을 때 하나가 예외로 실패하지 않고 순서대로
  처리되며, 두 요청이 끝난 뒤 활성 코드가 정확히 1개만 남는지
- 원자성: 기존 코드 폐기 이후 새 코드 insert가 실패하는 상황을 재현해서, 폐기까지 함께
  롤백되어 활성 코드가 0개인 상태가 남지 않는지
- 코드 저장: 응답에는 평문 코드가 오지만 `team_invite_links.token_hash`에는 해시만 저장되고,
  평문 코드로 DB를 조회해도 일치하는 행이 없는지

---

## 10. 이번 범위에서 제외

- **팀 합류(코드 입력 → 실제 합류)** — 초대 코드를 "발급"하는 부분(최초 발급 + 재발급)은
  이번 범위에 들어왔지만, 발급된 코드를 다른 사용자가 입력했을 때 실제로 `team_members`
  row를 만드는 "초대 수락" 트랜잭션과 컨트롤러는 아직 없다.
  `TeamInviteLink`/`TeamInviteQueryService.validateAndGetId()`로 1차 검증(코드 유효성 확인)
  까지만 있는 상태다. 담당자 미정, 별도 작업으로 진행한다.
- **단독 폐기 API(재발급 없이 코드만 없애는 기능)** — 6절의 발급 API는 "폐기 + 새 코드
  발급"을 한 번에 하는 동작이라, 코드를 없앤 채로 유지하고 싶은 경우는 다루지 않는다.
  필요해지면 별도 작업으로 추가한다.
- **사업자등록증 미리보기/재검수 화면용 서명 URL 발급 API** — 지금 플로우엔 필요 없고,
  나중에 이미지 열람이 필요한 화면이 생기면 추가한다(백엔드 B 영역으로 예상).
- **팀원 페이지의 나머지 기능** — 멤버 목록 조회, 역할 변경, 강퇴 등. 백엔드 A가 화면
  전체를 담당하게 됐지만, 이번 문서는 "초대 코드 발급" 버튼 하나만 다룬다 — 나머지는
  구체적인 화면/요구사항이 정해지면 별도로 설계한다.
