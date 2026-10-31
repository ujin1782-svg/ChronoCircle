# ChronoCircle — 24시간 원형 대시보드 기반 스마트 일정 관리 프로그램 (223235 정우진)

## 빌드 및 실행 방법 (JDK 17+)

Windows에서 `run-chronocircle.bat`을 실행하면 앱을 컴파일한 뒤 GUI를 시작합니다. 컴파일된 클래스는 저장소의 `out` 폴더에 생성됩니다.

직접 실행하려면 저장소 루트에서 다음 명령을 사용하세요.

```bash
javac -encoding UTF-8 -d out ChronoCircleApp.java
java -cp out ChronoCircleApp
```

## AI 자연어 일정 등록 (Gemini)

자연어 문장 분석에 Google Gemini API를 사용합니다. 앱 화면에는 키 입력 칸이 없으며, 실행할 때 환경 변수 `GEMINI_API_KEY`에서 키를 읽습니다.

### 키 등록 (한 번만)

평소에는 Windows 사용자 환경 변수에 한 번 등록해 두는 방법이 가장 편합니다. 등록한 뒤로는 jar 더블클릭, `java -jar`, `run-chronocircle.bat` 어느 방법으로 실행해도 키를 다시 입력할 필요가 없습니다.

- **설정 화면:** 시작 메뉴에서 "시스템 환경 변수 편집" → [환경 변수] → 사용자 변수 [새로 만들기] → 이름 `GEMINI_API_KEY`, 값에 발급받은 키 입력
- **명령어:**

  ```bat
  setx GEMINI_API_KEY "발급받은_키"
  ```

등록한 뒤 **새로 연 창부터** 적용됩니다. 이미 열려 있는 터미널이나 실행 중인 앱은 다시 실행해야 합니다.

### 이번 실행에만 쓰기

키를 저장하지 않으려면 실행하는 창에서 직접 지정합니다. 키를 지정한 같은 창에서 앱을 실행해야 적용됩니다.

```bash
# Windows (cmd)
set GEMINI_API_KEY=발급받은_키
# Windows (PowerShell)
$env:GEMINI_API_KEY = "발급받은_키"
# macOS / Linux
export GEMINI_API_KEY=발급받은_키
```

환경 변수 대신 `java -Dgemini.api.key=발급받은_키 -cp out ChronoCircleApp`처럼 시스템 속성으로 지정할 수도 있습니다.

### 동작 확인

일정 등록 창에서 문장을 입력하고 "AI 자동 분석"을 누르면, 아래 상태 문구로 어떤 방식으로 분석했는지 알 수 있습니다.

| 상태 문구 | 의미 |
|---|---|
| `Gemini(gemini-3.8-flash) 분석 완료…` | Gemini로 분석함 |
| `Gemini 분석 실패(HTTP 400)…` | 키가 잘못됨 (내장 파서로 대신 분석) |
| `Gemini 분석 실패(HTTP 404)…` | 지정한 모델을 이 키로 쓸 수 없음 |
| `내장 파서 분석 완료…` | 키가 앱에 전달되지 않음 |

### 참고

- 기본 모델은 `gemini-3.8-flash`이며, `GEMINI_MODEL` 환경 변수로 바꿀 수 있습니다.
- 키가 없거나 네트워크 오류가 나면 내장 한국어 정규식 파서로 대신 분석하므로, 키 없이도 앱은 정상 동작합니다.
- 키는 PC마다 따로 등록해야 합니다. 저장소나 소스 코드에 키를 넣지 마세요.

## 일정 알림

알림 시각이 되면 화면 상단에 알림 배너가 펼쳐지고, 운영체제가 시스템 트레이를 지원하면 트레이(Windows 작업 표시줄 오른쪽 아래)에도 알림이 표시됩니다.

- 트레이 알림이나 트레이 아이콘을 더블 클릭하면 앱 창이 앞으로 나오고 해당 일정의 상세 창이 열립니다.
- 트레이 아이콘을 오른쪽 클릭하면 [ChronoCircle 열기], [종료] 메뉴를 쓸 수 있습니다.
- Windows 알림이 꺼져 있거나 집중 모드(방해 금지)가 켜져 있으면 트레이 알림이 보이지 않을 수 있습니다. 이때도 상단 배너 알림은 표시됩니다.

## 문서

- API 매뉴얼은 `javadoc -encoding UTF-8 -charset UTF-8 -d api_manual ChronoCircleApp.java`로 생성할 수 있습니다.
