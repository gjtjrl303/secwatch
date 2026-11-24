# 사이트 점검 검증 기록

실행일: 2026-11-24

취약하게 만든 연습용 쇼핑몰 OWASP Juice Shop을 이 컴퓨터에 띄우고, 우리 서비스의 사이트 점검을 화면과 API로 돌려 동작과 결과를 확인했습니다. POST 폼을 제출하지 않는지는 요청을 기록하는 시험용 페이지로 따로 확인했습니다.

**운영 중인 서버는 시험 대상에서 제외했습니다.** 모든 점검은 이 컴퓨터의 `127.0.0.1`에만 연 연습용 사이트와 시험용 페이지에 했습니다.

## 조건

| 항목 | 값 |
| --- | --- |
| 대상 | Juice Shop 20.4.0-SNAPSHOT (`bkimminich/juice-shop:latest`, 이미지 빌드 2026-11-19), `127.0.0.1:3000`에만 열어 둠 |
| 점검 도구 | ZAP 2.17.0 (`ghcr.io/zaproxy/zaproxy:stable`, 이미지 빌드 2026-11-03) |
| 우리 서비스 | 이 저장소의 `main`(`73c6274`), 허용 주소 기본값(`localhost`) |
| 점검 방식 | 둘러보기 1분, 응답 살펴보기, POST 폼 제출 안 함, 공격 요청 없음 |
| 컴퓨터 | Apple Silicon(arm64) Mac, Docker Desktop |

## 방법

1. Juice Shop을 이 컴퓨터에서만 접근되게 띄웁니다.
   ```bash
   docker run -d --name secwatch-juice -p 127.0.0.1:3000:3000 bkimminich/juice-shop:latest
   ```
2. 앱을 띄우고(`SERVER_PORT=8081 ./gradlew bootRun`) 도메인이 `localhost:3000`인 프로젝트를 등록합니다.
3. 상세 화면의 "사이트 점검 실행"을 누르거나 API로 요청합니다.
   ```bash
   curl -X POST http://localhost:8081/api/projects/<번호>/site-scans
   ```
4. 점검 기록이 성공이나 실패로 바뀔 때까지 기다리며, 그동안 ZAP 컨테이너(`secwatch-zap-*`)의 메모리를 `docker stats`로 2초마다 쟀습니다.
5. 발견 항목을 화면과 `GET /api/projects/<번호>/site-findings`로 확인합니다.

## 결과

| 항목 | 값 |
| --- | --- |
| 점검 요청부터 성공까지 | 52초 |
| ZAP 컨테이너 최대 메모리 | 약 1,008MB |
| 발견 항목 | 10종 (보통 2, 낮음 4, 정보 4) |
| 원본 결과 크기 | 약 35KB |
| 점검 뒤 남은 ZAP 컨테이너 | 0개 |
| 허용 목록에 없는 프로젝트에 점검 요청 | 403, "점검이 허용되지 않은 주소입니다." |
| 성공 직후 같은 프로젝트에 다시 요청 | 429, "방금 점검한 프로젝트입니다." |

전날(2026-11-23) 같은 대상에 같은 방식으로 돌렸을 때는 34초가 걸렸습니다. 한 번씩 잰 값이라 30초에서 1분 사이로 흔들린다고 보는 것이 맞습니다.

### 발견 항목 10종

| 위험도 | 이름 | 유형(화면 표시) | 걸린 주소 수 |
| --- | --- | --- | --- |
| 보통 | Content Security Policy (CSP) Header Not Set | 보안 설정 미흡 | 5 |
| 보통 | Cross-Domain Misconfiguration | 보안 설정 미흡 | 5 |
| 낮음 | Cross-Origin-Embedder-Policy Header Missing or Invalid | 보안 설정 미흡 | 5 |
| 낮음 | Cross-Origin-Opener-Policy Header Missing or Invalid | 보안 설정 미흡 | 5 |
| 낮음 | Dangerous JS Functions | 없음 | 1 |
| 낮음 | Deprecated Feature Policy Header Set | 보안 설정 미흡 | 5 |
| 정보 | Modern Web Application | 없음 | 5 |
| 정보 | Non-Storable Content | 정보 노출 위험 | 5 |
| 정보 | Storable and Cacheable Content | 정보 노출 위험 | 1 |
| 정보 | Storable but Non-Cacheable Content | 정보 노출 위험 | 5 |

걸린 주소 수는 둘러본 범위에 따라 달라집니다. 전날에는 "Non-Storable Content"가 3곳이었습니다.

## POST 폼을 제출하지 않는지

POST 폼(`/subscribe`)과 GET 폼(`/search`)이 하나씩 있는 시험용 페이지를 `127.0.0.1:3999`에 띄우고, 들어오는 요청을 모두 기록했습니다.

| 점검 방법 | 들어온 요청 | POST | GET 검색 |
| --- | --- | --- | --- |
| ZAP 기본 점검 스크립트(`zap-baseline.py`), 2026-11-23 | 7 | 1 (`POST /subscribe`) | 1 |
| 같은 스크립트에 `-z "-config spider.postform=false"`, 2026-11-23 | 7 | 1 | 1 |
| 우리 서비스, 2026-11-24 | 6 | 0 | 1 |

우리 서비스로 돌렸을 때 들어온 요청은 `GET /` 2번, `GET /about`, `GET /robots.txt`, `GET /sitemap.xml`, `GET /search?q=ZAP`입니다. 점검은 26초 만에 성공했고 ZAP 메모리는 최대 약 488MB였습니다.

ZAP의 기본 점검 스크립트는 페이지의 POST 폼을 빈 값으로 제출합니다. 설정값으로도 꺼지지 않아서, 우리 서비스는 스크립트 대신 자동화 계획 파일에 `postForm: false`를 적어 ZAP에 직접 넘깁니다(#49).

## 기본 점검과 전체 점검 (참고)

2026-11-23에 같은 Juice Shop을 ZAP 스크립트로 직접 돌려 비교했습니다. 우리 서비스를 거치지 않았고, 이 기록의 위 결과와는 실행 방법이 다릅니다.

| | 기본 점검(`zap-baseline.py`) | 전체 점검(`zap-full-scan.py`) |
| --- | --- | --- |
| 걸린 시간 | 51초 | 첫 번째 37분 넘게 돌다 강제 종료, 두 번째 10분 9초 |
| 최대 메모리 | 약 955MB | 약 7.6GB, 약 8.6GB |
| 찾은 항목 | 10종 | 15종(두 번째 실행) |

전체 점검에서만 나온 것은 백업 파일 노출(31곳), 403 우회(6곳), CORS 설정 오류, HTTP로만 서비스, 사용자 에이전트를 바꾼 요청에 대한 응답 차이입니다. 메모리가 7GB를 넘어서 작은 서버에서는 돌릴 수 없고, 상대 사이트에 공격 요청을 보내므로 우리 서비스에는 넣지 않았습니다.

## 해석

- 사이트 점검은 의도한 대로 동작합니다. 화면과 API로 요청하고, 1분 안에 끝나며, 결과가 저장되고, 허용 목록과 재요청 간격 규칙이 지켜지고, 컨테이너가 남지 않습니다.
- 찾는 것은 보안 헤더 누락과 캐시 설정 같은 설정 문제입니다. Juice Shop에는 SQL 주입, 스크립트 삽입, 로그인 우회 같은 문제를 다루는 도전 과제가 100개 넘게 있지만, 그런 문제는 이 점검으로 찾지 못합니다. 값을 넣어 봐야 드러나는 문제이기 때문입니다. 전체 점검으로도 이번에는 나오지 않았습니다.
- 메모리는 점검 한 번에 1GB 가까이 듭니다. 앱과 DB를 함께 띄우려면 서버 메모리는 4GB를 권장합니다. 사이트가 크면 더 들 수 있습니다.
- GET 폼은 제출합니다. 정상적인 사이트라면 조회만 하지만, GET으로 데이터를 바꾸는 사이트라면 영향이 있을 수 있습니다.
- ZAP은 점검할 때마다 `pscanrulesBeta` 규칙 묶음을 GitHub에서 내려받습니다. 규칙이 갱신되면 같은 사이트라도 결과가 달라질 수 있습니다.
- 모든 수치는 한 번씩 잰 값입니다.
