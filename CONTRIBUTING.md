# 작업 규칙

- 이슈를 만들고 `main`에서 `feat/#이슈번호` 브랜치를 만듭니다. (`fix` `refactor` `test` `docs` `chore`도 같은 형식)
- 커밋 제목은 `Type: 한글 설명` 한 줄입니다. Type은 `Feat` `Fix` `Refactor` `Test` `Docs` `Chore` `CI/CD`입니다.
- PR 본문에 `close #이슈번호`를 적고 전체 테스트(`./gradlew cleanTest test`)가 통과하면 머지합니다.
- 비밀값(웹훅 주소와 키)은 코드와 로그와 문서에 올리지 않습니다.
