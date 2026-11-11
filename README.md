# secwatch (가칭)

도메인과 의존성 목록(`package-lock.json`)을 등록하면, 쓰는 오픈소스의 새 취약점을 찾아 디스코드로 알려주는 서비스입니다. 소스 코드는 받지 않습니다.

## 로컬에서 실행하기
1. MySQL을 도커로 띄웁니다. (호스트 3307 포트, 데이터베이스와 계정은 모두 `secwatch`)
   ```bash
   docker compose up -d --wait
   ```
2. 앱을 실행합니다. (기본 접속 주소가 위 MySQL이라 따로 설정할 것이 없습니다.)
   - IntelliJ: `SecwatchApplication`을 실행합니다.
   - 터미널:
     ```bash
     ./gradlew bootRun
     ```
3. 브라우저에서 http://localhost:8080 을 엽니다. (포트가 겹치면 `--server.port=원하는번호`를 붙입니다.)
4. 끝나면 MySQL을 내립니다. 데이터까지 지우려면 `-v`를 붙입니다.
   ```bash
   docker compose down
   ```

## 테스트
Docker가 켜져 있으면 테스트가 MySQL을 알아서 띄웁니다.
```bash
./gradlew test
```
