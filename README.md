# secwatch

웹 서비스의 보안 취약점을 찾아 알려 주는 점검 서비스입니다.

## 실행

```bash
docker compose up -d --wait   # MySQL
./gradlew bootRun             # http://localhost:8080
```

## 테스트

```bash
./gradlew test
```

Docker가 켜져 있어야 합니다.
