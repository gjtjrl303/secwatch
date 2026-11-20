# Juice Shop 검증 기록

실행일: 2026-11-20

취약하게 만든 연습용 쇼핑몰 OWASP Juice Shop의 라이브러리 목록을 우리 서비스와 Grype(Anchore의 오픈소스 취약점 스캐너)로 각각 점검해 결과를 비교했습니다. 정답이 따로 없어서 이미 널리 쓰이는 도구인 Grype를 기준으로 삼았습니다.

## 조건
| 항목 | 값 |
| --- | --- |
| 대상 | Juice Shop `master`의 `package.json`(20.4.0-SNAPSHOT)에서 만든 `package-lock.json` |
| 비교 도구 | Grype v0.122.1(공식 GitHub 릴리스, 체크섬 확인), 취약점 데이터베이스 2026-11-20 06:21 UTC 빌드 |
| 우리 서비스 | 이 저장소의 `main`, 실제 OSV(`api.osv.dev`)를 호출 |
| 점검 시점 | 두 도구 모두 2026-11-20 |

## 방법
1. Juice Shop 저장소에는 `package-lock.json`이 없어서 `package.json`만 받아 `npm install --package-lock-only --ignore-scripts --no-audit --no-fund`로 잠금 파일만 만들었습니다. 라이브러리를 설치하거나 스크립트를 실행하지 않았습니다.
2. 같은 폴더를 Grype로 점검했습니다. `grype dir:<경로> -o json > grype.json`
3. 비교 도구를 실행했습니다. 우리 쪽은 프로젝트를 만들고 잠금 파일을 가져오면 자동으로 점검이 돌고, 끝난 뒤 저장된 발견 항목을 Grype 결과와 비교합니다.
   ```bash
   VERIFY_LOCKFILE=<경로>/package-lock.json VERIFY_GRYPE_JSON=<경로>/grype.json \
     ./gradlew cleanTest test --tests '*JuiceShopComparisonTest' -i
   ```
4. 결과는 `build/verification/comparison.txt`에 저장됩니다. 같은 취약점인지는 번호(GHSA, CVE 등)와 별칭이 하나라도 겹치고 패키지와 버전이 같으면 같은 것으로 봅니다.

## 결과
| 항목 | 값 |
| --- | --- |
| 잠금 파일 항목 수 | 1,249 |
| 우리가 읽은 서로 다른 패키지 수(건너뜀) | 1,108 (0) |
| 우리 서비스가 찾은 취약점 | 54 |
| Grype가 찾은 취약점 | 49 |
| 둘 다 찾음 | 49 |
| 우리만 찾음 | 5 |
| Grype만 찾음 | 0 |

- 1,249개 항목이 1,108개가 되는 것은 같은 이름과 버전이 여러 곳에 나뉘어 있는 것을 하나로 합치고, `string-width-cjs`처럼 다른 이름으로 들어온 npm 별칭 3개를 원래 이름으로 합쳤기 때문입니다. 건너뛴 항목은 없습니다.
- 가져오기부터 점검까지 끝나는 데 테스트 실행 전체(앱과 MySQL 컨테이너 시작 포함)가 약 38초 걸렸습니다.

## 우리만 찾은 5건
| 패키지 | 위험도 | 번호 | 내용 | 개발용 |
| --- | --- | --- | --- | --- |
| @cyclonedx/cyclonedx-npm@4.2.1 | HIGH | GHSA-q69g-4hcv-6jg4 | Windows에서 `--workspace` 인자를 거르지 않아 생기는 셸 주입 | 예 |
| @cyclonedx/cyclonedx-npm@4.2.1 | HIGH | GHSA-v75r-vx73-82pj | `--workspace` 인자를 거르지 않아 생기는 셸 주입 | 예 |
| glob@10.5.0 | MODERATE | GHSA-3g3m-2fjr-6pw4 | AbortSignal 취소를 우회해 시간 초과 뒤에도 파일 시스템을 돌 수 있음 | 예(선택 설치) |
| messageformat@2.3.0 | LOW | GHSA-xfqm-j7pc-xrfc | 프로토타입 오염 | 아니오 |
| minimatch@9.0.9 | MODERATE | GHSA-g3hv-75wg-m249 | globparts 증폭으로 인한 서비스 거부 가능성 | 예(선택 설치) |

개발용 여부는 `package-lock.json`의 `dev` 표시로 확인했습니다.

## 해석
- Grype가 찾은 49건은 모두 우리도 찾았습니다. 이 비교에서 우리가 놓친 것은 없습니다.
- 우리만 찾은 5건은 모두 GitHub 보안 권고(GHSA)입니다. Grype가 왜 빠뜨렸는지는 확인하지 못했습니다. 권고 원문을 하나씩 대조하지 않았고, 우리 서비스는 OSV의 조회 결과를 그대로 쓰므로 같은 출처로 다시 확인하는 것은 독립적인 검증이 아닙니다.
- 이 비교는 "Grype 대비"이지 정답 대비가 아닙니다. 둘 다 놓친 취약점은 알 수 없습니다.
- 두 도구의 데이터와 Juice Shop의 `master`는 계속 바뀝니다. 2026-11-06에 이전 구현으로 잰 기록(우리 67건, Grype 63건, 대상 20.3.0)과 숫자가 다른 것은 대상 버전과 데이터가 달라서이고, 이번 결과와 직접 비교할 수 없습니다.
- 개발용 의존성을 구분하지 않습니다. 우리만 찾은 5건 중 4건이 개발용이고, 이 잠금 파일은 1,249개 항목 중 678개가 개발용입니다. 알림이 많아질 때의 후속 개선 후보입니다.
- Grype 데이터베이스는 디스크에서 약 3.3GB입니다. 같은 비교를 다시 하려면 이만큼의 공간이 필요합니다.
