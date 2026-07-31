# 배포 전 원복 체크리스트 — issue #31

이슈: https://github.com/Shinhan-KLLJS/backend/issues/31 ([Fix] 프론트 개발자가 local에서 서버 호출할 수 있도록 수정)

## 무엇을 바꿨나

프론트 개발이 끝나기 전까지, 로컬(Vite 기본 포트 `5173`)에서 배포된 서버(dev로 배포되는 인스턴스,
실질적으로 운영과 같은 인프라)를 호출해 CORS 없이 API 테스트와 카카오 로그인까지 해볼 수 있도록,
`infra/terraform.tfvars`의 `app_frontend_url`을 임시로 바꿨다.

```diff
- app_frontend_url = "https://www.loovi.my"
+ app_frontend_url = "http://localhost:5173"
```

이 값 하나가 다음 세 가지에 전부 쓰인다 (그래서 값 하나만 바꿔도 CORS·로그인 리다이렉트가
동시에 로컬을 가리키게 된다):

1. **CORS 허용 origin** (`CorsConfig.java`) — `Access-Control-Allow-Origin`이 `http://localhost:5173`이 된다.
2. **TrustedOriginValidator** — refresh/logout 같은 쿠키 기반 요청의 Origin 검증도 로컬을 신뢰한다.
3. **카카오 로그인 성공/실패 후 리다이렉트** (`AppAuthProperties.java`) — 로그인 끝나면
   `https://www.loovi.my/login/success`가 아니라 `http://localhost:5173/login/success`로 이동한다.

`terraform apply` 시 실제로 바뀌는 리소스는 2개뿐이다 (`terraform plan`으로 확인함):

- `module.ssm_params.aws_ssm_parameter.app_frontend_url` (`/vision/prod/app-frontend-url` 값 변경)
- `module.s3.aws_s3_bucket_cors_configuration.campaign_creatives` (캠페인 소재 업로드 CORS origin이
  `https://www.loovi.my` → `http://localhost:5173`로 교체, `https://api.loovi.my`는 그대로 유지)

카카오 디벨로퍼스에 등록된 `kakao_redirect_uri`(`https://api.loovi.my/login/oauth2/code/kakao`,
백엔드 자체 콜백 주소)는 건드리지 않았다 — 바뀌는 건 로그인 "이후" 프론트로 돌아가는 주소뿐이다.

## 실제 배포 전에 반드시 할 일

**이 상태로는 실제 사용자가 카카오 로그인을 하면 `localhost:5173`으로 튕겨서 로그인이 끝나지 않고,
운영 프론트(`www.loovi.my`)에서의 API 호출도 CORS로 막힌다.** 프론트 개발이 끝나고 실제 배포하기
전에 반드시 원복해야 한다.

1. `infra/terraform.tfvars`에서 `app_frontend_url`을 원래 값으로 되돌린다.
   ```
   app_frontend_url = "https://www.loovi.my"
   ```
2. `infra/`에서 `terraform plan`으로 위 두 리소스(SSM 파라미터, S3 CORS)가 원래 값으로 돌아가는지
   확인 후 `terraform apply`.
3. 다음 배포(CD) 때 컨테이너가 SSM에서 값을 다시 읽어가므로, apply 후 재배포가 한 번 있어야
   실제로 반영된다 — apply만 하고 재배포를 안 하면 이미 떠 있는 컨테이너는 이전 값을 계속 쓴다.
4. 이 문서와 issue #31은 원복 후 닫는다.

## 참고

`terraform.tfvars`는 gitignore 대상이라 이 변경 자체는 git에 흔적이 남지 않는다 — 그래서 이 문서가
유일한 기록이다. 원복을 깜빡하면 실제 배포 시점에 로그인이 조용히 깨지므로, 배포 전 체크리스트에
이 문서 확인을 포함시키는 걸 권장한다.
