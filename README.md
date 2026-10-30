# ChronoCircle — 24시간 원형 대시보드 기반 스마트 일정 관리 프로그램 (223235 정우진)

## 빌드 및 실행 방법 (JDK 17+)

Windows에서 `run-chronocircle.bat`을 실행하면 앱을 컴파일한 뒤 GUI를 시작합니다. 컴파일된 클래스는 저장소의 `out` 폴더에 생성됩니다.

직접 실행하려면 저장소 루트에서 다음 명령을 사용하세요.

```bash
javac -encoding UTF-8 -d out ChronoCircleApp.java
java -cp out ChronoCircleApp
```

## AI 자연어 일정 등록 (Gemini)

자연어 문장 분석에 Google Gemini API를 사용합니다. 실행 전에 API 키를 환경 변수로 지정하세요. `run-chronocircle.bat`도 같은 창에서 설정한 환경 변수를 그대로 사용합니다.

```bash
# Windows (cmd)
set GEMINI_API_KEY=발급받은_키
# macOS / Linux
export GEMINI_API_KEY=발급받은_키
```

- 키 대신 `java -Dgemini.api.key=발급받은_키 -cp out ChronoCircleApp`처럼 시스템 속성으로도 지정할 수 있습니다.
- 기본 모델은 `gemini-3.8-flash`이며, `GEMINI_MODEL` 환경 변수로 바꿀 수 있습니다.
- 키가 없거나 네트워크 오류가 나면 내장 한국어 정규식 파서로 대신 분석합니다.

## 문서

- API 매뉴얼은 `javadoc -encoding UTF-8 -charset UTF-8 -d api_manual ChronoCircleApp.java`로 생성할 수 있습니다.
