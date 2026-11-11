#!/bin/bash
# 저장소를 만든 뒤 한 번 실행한다. 라벨과 마일스톤을 등록한다. (gh 로그인과 저장소 폴더가 필요)
#   ./scripts/github-setup.sh
set -e
cd "$(dirname "$0")/.."

label() { gh label create "$1" --color "$2" --description "$3" --force >/dev/null && echo "라벨: $1"; }

# 작업 종류
label "기능"   "2da44e" "새 기능"
label "버그"   "d73a4a" "잘못 동작하는 것"
label "개선"   "0075ca" "구조나 품질 개선, 기술부채"
label "문서"   "6e7781" "문서"
label "테스트" "e3b341" "테스트 추가와 수정"
label "보안"   "e16f24" "보안 관련"

# 영역
label "parser"   "8250df" "의존성 목록 읽기"
label "osv"      "a475f9" "OSV 조회"
label "notify"   "bf8af7" "디스코드 알림"
label "web"      "7057ff" "화면"
label "application" "9a6dd7" "서비스 등록과 자동 점검과 재점검"
label "infra"    "5a3fbf" "실행 환경, 빌드, CI"

# 마일스톤 (기획서의 릴리즈 범위)
milestone() {
  local number
  number=$(gh api "repos/{owner}/{repo}/milestones?state=all" --jq ".[] | select(.title==\"$1\") | .number")
  if [ -z "$number" ]; then
    gh api "repos/{owner}/{repo}/milestones" -f title="$1" -f state="$2" -f description="$3" >/dev/null && echo "마일스톤: $1"
  else
    gh api -X PATCH "repos/{owner}/{repo}/milestones/$number" -f state="$2" -f description="$3" >/dev/null && echo "마일스톤: $1 (갱신)"
  fi
}
milestone "1차" "open"   "라이브러리 취약점 찾기와 알림과 화면"
milestone "2차" "open"   "도메인 소유 확인, ZAP 기본 점검, Gradle과 pnpm(SBOM) 지원, 모꼬지 등록"
milestone "3차" "open"   "오픈소스 라이선스 점검"
