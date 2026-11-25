# secwatch

웹 서비스의 보안 취약점을 찾아 알려 주는 점검 서비스입니다.

## 실행

```bash
export SECWATCH_ENCRYPTION_KEY="$(openssl rand -base64 32)"   # 처음 한 번만 만들고 따로 보관
docker compose up -d --wait   # MySQL
./gradlew bootRun             # http://localhost:8080
```

`SECWATCH_ENCRYPTION_KEY`는 디스코드 웹훅 주소와 사이트 점검 비밀번호를 DB에 암호화해 저장하는 키입니다. 없거나 32바이트가 아니면 앱이 시작하지 않습니다. 키를 바꾸거나 잃어버리면 저장해 둔 웹훅 주소와 비밀번호를 풀 수 없으니, 처음 만든 키를 계속 같은 값으로 넣어주세요. 키는 저장소에 올리지 않습니다.

## 테스트

```bash
./gradlew test
```

Docker가 켜져 있어야 합니다.
