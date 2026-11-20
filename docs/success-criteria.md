# 1차 성공 기준과 검증 결과

확인일: 2026-11-20, 확인한 코드: `main`(PR #38까지 머지)

1차의 범위는 기획 문서의 1차 항목입니다. npm 의존성의 취약점 조회와 하루 한 번 재조회, 디스코드 알림, 웹 화면, 연습용 대상(OWASP Juice Shop)으로 검증하는 것까지입니다. 아래 표는 기준마다 어떻게 확인했고 결과가 어땠는지를 적었습니다. 충족하지 못했거나 일부만 충족한 것도 그대로 적었습니다.

자동 테스트는 전체 191개 중 2개를 건너뛰고 나머지가 통과합니다(실패 0). 건너뛴 2개는 환경변수로 켜야 도는 실제 OSV 호출 테스트(`OsvLiveTest`)와 Juice Shop 비교(`JuiceShopComparisonTest`)입니다. Juice Shop 비교는 직접 실행했고 결과는 [Juice Shop 검증 기록](verification-juice-shop.md)에 있습니다.

## 기준과 결과

| No | 기준 | 확인한 방법 | 결과 |
| --- | --- | --- | --- |
| 1 | 프로젝트 이름과 도메인을 등록할 수 있고 같은 도메인은 중복 등록할 수 없다 | `ProjectRegistrationTest`, `ProjectControllerTest`(이름이 비었거나 너무 긴 경우), `PageControllerTest`(중복 도메인과 잘못된 도메인 안내) | 충족 |
| 2 | `package-lock.json`을 붙여넣으면 라이브러리 이름과 버전을 읽어 저장하고 소스 코드는 받지 않는다 | `NpmLockParserTest`(중첩과 `@scope` 이름과 별칭 설치 패키지, 링크와 버전 없는 항목은 건너뛰고 개수를 셈), `DependencyControllerTest`, Juice Shop 잠금 파일 1,249개 항목에서 1,108개 읽음(건너뜀 0) | 충족 |
| 3 | 지원하지 않는 형식이나 빈 입력은 안내 문구를 돌려준다 | `NpmLockParserTest`, `DependencyControllerTest`, `PageControllerTest` | 충족 |
| 4 | 저장된 라이브러리와 버전으로 OSV에서 취약점을 조회해 번호와 별칭, 위험도, 요약, 고쳐진 버전을 저장하고 위험도가 없으면 미분류로 둔다 | `OsvClientTest`, `CheckServiceTest`, `FindingReconcilerTest`, 실제 OSV로 Juice Shop 점검(우리 54건) | 충족 |
| 5 | OSV가 느리거나 실패해도 기존 항목은 남기고 실패를 기록한다 | `CheckServiceFailureTest`, `OsvClientTest`(서버 오류와 시간 초과와 이상한 응답) | 충족 |
| 6 | 같은 취약점이 여러 번호(GHSA, CVE)로 와도 한 건으로 센다 | `FindingReconcilerTest`(같은 의존성에서 별칭이 겹치는 경우) | **일부**: 처음부터 번호가 겹치면 한 건이다. 나중에 별칭으로 같은 것으로 밝혀지는 기존 항목끼리는 합치지 않는다(이슈 #31) |
| 7 | 가져온 직후 점검이 자동으로 돌고 가져오기 응답은 점검을 기다리지 않는다 | `CheckTriggerTest`(커밋 뒤 실행, 롤백된 가져오기에는 반응하지 않음, 대기열이 가득 차면 버리고 경고), 로컬에서 앱을 띄워 화면에서 등록한 뒤 발견 항목이 나오는 것을 확인 | **일부**: 평소에는 충족한다. 다만 점검 대기열(스레드 2개, 대기 20건)이 가득 차면 그 요청은 버려지고 바로 다시 시도하지 않아서, 다음 정기 점검(8번)이 따라잡을 때까지 점검되지 않는다 |
| 8 | 매일 정해진 시각에 모든 프로젝트를 다시 점검하고 한 프로젝트의 실패가 다른 프로젝트에 번지지 않는다 | `DailyRecheckTest`, `DailyRecheckSchedulingTest`(스케줄러를 매초로 켜서 저절로 실행되는 것을 확인) | **일부**: 기본 시각(오전 9시, 서울 기준)까지 실제로 기다려 확인하지는 않았다 |
| 9 | 새로 발견한 항목만 디스코드로 알리고 같은 항목을 두 번 보내지 않는다 | `NewFindingNotifierTest`(한 통으로 묶음, 이미 보낸 항목 제외, 동시에 두 번 호출해도 한 번만 보냄, 2000자 제한과 `…외 N건`) | 충족(가짜 서버로 확인) |
| 10 | 알림 전송이 실패하면 기록하고 다음에 다시 보낸다 | `NewFindingNotifierTest`, `DiscordNotifierTest`(저장 확인을 기다리는 `wait=true`, 본문 없는 응답은 실패), `CheckRunnerTest` | 충족(가짜 서버로 확인) |
| 11 | 웹훅 주소는 비밀값이라 화면과 로그와 예외 메시지에 나오지 않는다 | `DiscordNotifierTest`, `PageControllerTest`, `ProjectDetailPageTest`(등록과 상세와 실패한 입력 모두) | 충족 |
| 12 | 외부 글(OSV 요약)에 들어 있는 `@everyone` 같은 멘션이 채널을 울리지 않는다 | `DiscordNotifierTest`(`allowed_mentions` 비움) | 충족(가짜 서버로 확인) |
| 13 | 발견 항목의 상태(새로 발견, 확인함, 해결함, 해당 없음)를 바꿀 수 있고 변경 이력이 남는다 | `FindingControllerTest`, `FindingStatusConcurrencyTest`, `FindingStaleUpdateTest`, `ProjectDetailPageTest` | 충족 |
| 14 | 웹 화면에서 목록, 등록, 상세(패키지별 묶기, 위험도와 상태 필터, 상태 변경)를 쓸 수 있다 | `PageControllerTest`, `ProjectDetailPageTest`, `FindingGroupingTest`, 로컬에서 앱을 띄워 브라우저로 확인(필터, 상태 변경, 실패한 입력, 모바일 폭) | 충족 |
| 15 | 프로젝트 이름과 외부 글의 HTML이 화면에서 글자로만 나온다 | `PageControllerTest` | 충족 |
| 16 | 다른 프로젝트의 항목 상태를 바꿀 수 없다 | `ProjectDetailPageTest` | 충족 |
| 17 | 연습용 대상(Juice Shop)으로 검증하고 기록한다 | Grype와 비교 | 충족: 우리 54건, Grype 49건, Grype가 찾은 49건을 모두 찾았고 우리만 찾은 것이 5건([기록](verification-juice-shop.md)) |

## 확인하지 못한 것과 한계

- **실제 디스코드로 보내 보지 않았습니다.** 알림은 가짜 서버(`MockRestServiceServer`)로만 확인했습니다. 실제 디스코드가 응답하는 방식에 따른 차이는 아직 모릅니다.
- **오전 9시 재점검을 실제 시각에 확인하지 않았습니다.** 스케줄러가 저절로 실행되는지는 매초 실행으로 확인했습니다.
- **개발용 의존성을 구분하지 않습니다.** Juice Shop 잠금 파일은 1,249개 항목 중 678개가 개발용이고 알림이 많아질 수 있습니다.
- **로그인이 없고 CSRF 보호도 없습니다.** 개인 서버나 내부망에서 쓰는 전제입니다(기획 문서의 후순위 항목).
- **운영 서버에 배포하지 않았습니다.** 개발 중에는 내 컴퓨터의 Docker에서 실행합니다(기획 문서와 같음).
- **서버를 여러 대 띄우면 정기 점검이 각각 돕니다.** 같은 프로젝트는 행 잠금으로 직렬화되어 데이터가 깨지지는 않지만 OSV 호출과 알림 시도가 중복됩니다.
- **가져오기를 다시 하면 버전이 바뀌거나 빠진 라이브러리의 이전 항목과 상태 이력이 지워집니다.**
- **Juice Shop 비교는 Grype 대비입니다.** 정답 대비가 아니고, 측정한 날짜의 데이터에 의존합니다.

## 2차 이후 (이번 기준에 넣지 않음)

도메인 소유 확인과 ZAP 사이트 점검, CycloneDX SBOM 업로드와 Gradle 지원, 모꼬지 개발 서버 적용은 2차입니다. 오픈소스 라이선스 점검은 3차입니다.
