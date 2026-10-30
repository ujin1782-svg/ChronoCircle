import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import java.util.stream.Collectors;

/** 24시간 원형 대시보드 기반 개인 일정 관리 프로그램 ChronoCircle. */
public class ChronoCircleApp {

    public static final Color BG_MAIN = new Color(0xF8, 0xFA, 0xFC);
    public static final Color BG_SURFACE = Color.WHITE;
    public static final Color BG_SUBTLE = new Color(0xF1, 0xF5, 0xF9);
    public static final Color PRIMARY_BLUE = new Color(0x43, 0x61, 0xEE);
    public static final Color PRIMARY_BLUE_HOVER = new Color(0x3A, 0x53, 0xD0);
    public static final Color PRIMARY_LIGHT_BG = new Color(0xEE, 0xF2, 0xFF);
    public static final Color PRIMARY_BORDER = new Color(0xC7, 0xD2, 0xFE);
    public static final Color INDIGO_PURPLE = new Color(0x5B, 0x4B, 0xC4);
    public static final Color TEXT_PRIMARY = new Color(0x1E, 0x29, 0x3B);
    public static final Color TEXT_SECONDARY = new Color(0x64, 0x74, 0x8B);
    public static final Color TEXT_MUTED = new Color(0x94, 0xA3, 0xB8);
    public static final Color BORDER_COLOR = new Color(0xE2, 0xE8, 0xF0);
    public static final Color DANGER_RED = new Color(0xEF, 0x44, 0x44);
    public static final Color DANGER_LIGHT = new Color(0xFE, 0xE2, 0xE2);
    public static final Color SUCCESS_GREEN = new Color(0x10, 0xB9, 0x81);
    public static final Color SUCCESS_LIGHT = new Color(0xD1, 0xFA, 0xE5);
    public static final Color WARNING_AMBER = new Color(0xF5, 0x9E, 0x0B);
    public static final Color WARNING_LIGHT = new Color(0xFE, 0xF3, 0xC7);
    public static final Color DARK_NAVY_BTN = new Color(0x2B, 0x30, 0x3A);
    public static final Color SUNDAY_RED = new Color(0xB9, 0x38, 0x29);
    public static final Color SATURDAY_BLUE = new Color(0x1D, 0x5B, 0x9B);

    public static final Color[] SCHEDULE_PALETTE = {
        new Color(0x43, 0x61, 0xEE),
        new Color(0x7C, 0x3A, 0xED),
        new Color(0x0D, 0x94, 0x88),
        new Color(0xEA, 0x58, 0x0C),
        new Color(0xDB, 0x27, 0x77),
        new Color(0x25, 0x63, 0xEB)
    };

    private static String fontFamily = "Dialog";

    /** Look and Feel을 설정하고, 시스템에 설치된 한글 글꼴을 찾아 기본 UI 글꼴로 지정한다. */
    public static void initFontAndLook() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] available = ge.getAvailableFontFamilyNames();
        String[] preferred = {
            "Malgun Gothic", "맑은 고딕", "Apple SD Gothic Neo",
            "Noto Sans CJK KR", "Noto Sans KR", "NanumGothic", "나눔고딕", "SansSerif"
        };
        Set<String> set = new HashSet<>(Arrays.asList(available));
        for (String p : preferred) {
            if (set.contains(p)) {
                fontFamily = p;
                break;
            }
        }
        Font base = uiFont(Font.PLAIN, 13);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(key, new javax.swing.plaf.FontUIResource(base));
            }
        }
    }

    public static Font uiFont(int style, int size) {
        return new Font(fontFamily, style, size);
    }

    /** 일정 한 건의 정보(제목, 시작/종료 일시, 장소, 설명, 알림 설정)를 담는 객체. */
    public static class ScheduleDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        public String id;
        public String title;
        public LocalDateTime startDateTime;
        public LocalDateTime endDateTime;
        public String location;
        public String description;
        public boolean notificationEnabled;
        public int notificationLeadMinutes; // 0, 5, 10, 15, 30, 60, 1440
        public int repeatIntervalMinutes;   // 0(반복없음), 5, 10, 30, 60, 1440
        public LocalDateTime nextNotificationTime;
        public int colorIndex;

        public ScheduleDTO() {
            this.id = UUID.randomUUID().toString();
            this.title = "";
            this.location = "";
            this.description = "";
            this.notificationEnabled = true;
            this.notificationLeadMinutes = 30;
            this.repeatIntervalMinutes = 0;
            this.colorIndex = 0;
        }

        public ScheduleDTO(String id, String title, LocalDateTime start, LocalDateTime end,
                           String location, String description, boolean notiEnabled,
                           int notiLeadMin, int repeatMin, int colorIndex) {
            this.id = (id == null || id.isEmpty()) ? UUID.randomUUID().toString() : id;
            this.title = title != null ? title : "";
            this.startDateTime = start;
            this.endDateTime = end;
            this.location = location != null ? location : "";
            this.description = description != null ? description : "";
            this.notificationEnabled = notiEnabled;
            this.notificationLeadMinutes = notiLeadMin;
            this.repeatIntervalMinutes = repeatMin;
            this.colorIndex = colorIndex;
            recalculateNextNotificationTime();
        }

        public ScheduleDTO copy() {
            ScheduleDTO c = new ScheduleDTO(id, title, startDateTime, endDateTime,
                    location, description, notificationEnabled,
                    notificationLeadMinutes, repeatIntervalMinutes, colorIndex);
            c.nextNotificationTime = this.nextNotificationTime;
            return c;
        }

        /** 시작 시각과 사전 알림 시간으로 다음 알림 시각을 다시 계산한다. */
        public void recalculateNextNotificationTime() {
            if (notificationEnabled && startDateTime != null) {
                this.nextNotificationTime = startDateTime.minusMinutes(notificationLeadMinutes);
            } else {
                this.nextNotificationTime = null;
            }
        }

        /**
         * @param date 확인할 날짜
         * @return 일정 기간이 해당 날짜를 포함하면 true
         */
        public boolean occursOn(LocalDate date) {
            if (startDateTime == null || endDateTime == null) return false;
            LocalDate s = startDateTime.toLocalDate();
            LocalDate e = endDateTime.toLocalDate();
            return !date.isBefore(s) && !date.isAfter(e);
        }

        /**
         * @param dt 기준 일시
         * @return 시작 &lt;= dt &lt; 종료이면 true
         */
        public boolean isOngoingAt(LocalDateTime dt) {
            if (startDateTime == null || endDateTime == null) return false;
            return !dt.isBefore(startDateTime) && dt.isBefore(endDateTime);
        }

        public String getTimeRangeText() {
            DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");
            return startDateTime.format(tf) + " ~ " + endDateTime.format(tf);
        }

        public String getShortTimeRangeText() {
            DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");
            return startDateTime.format(tf) + "~" + endDateTime.format(tf);
        }

        public String getNotificationSummary() {
            if (!notificationEnabled) return "알림 사용 안 함";
            String lead = formatMinutes(notificationLeadMinutes) + (notificationLeadMinutes == 0 ? " (정각)" : " 전");
            String rep = repeatIntervalMinutes > 0 ? " / " + formatMinutes(repeatIntervalMinutes) + "마다 반복" : " / 반복 없음";
            return lead + rep;
        }

        public static String formatMinutes(int min) {
            if (min == 0) return "시작 시점";
            if (min < 60) return min + "분";
            if (min < 1440) return (min / 60) + "시간";
            return (min / 1440) + "일";
        }
    }

    /** 등록 폼을 채우는 데 쓰는 자연어 문장 분석 결과. */
    public static class ParsedScheduleDTO {
        public String title;
        public LocalDateTime startDateTime;
        public LocalDateTime endDateTime;
        public String location;
        public String description;
        public String rawPrompt;
        public String parseConfidenceNote;

        public ParsedScheduleDTO(String title, LocalDateTime startDateTime, LocalDateTime endDateTime,
                                 String location, String description, String rawPrompt, String note) {
            this.title = title;
            this.startDateTime = startDateTime;
            this.endDateTime = endDateTime;
            this.location = location;
            this.description = description;
            this.rawPrompt = rawPrompt;
            this.parseConfidenceNote = note;
        }
    }

    /** 원형 대시보드에 그릴 일정 호(Arc) 하나의 각도와 표시 상태. */
    public static class CircularScheduleSegment {
        public final ScheduleDTO schedule;
        public final double rawStartAngleDeg;
        public final double sweepAngleDeg;
        public final double rotationOffsetDeg;
        public final boolean isOngoing;
        public final boolean isPreviewTarget;
        public final Color segmentColor;
        public Shape renderedArcShape;
        public Rectangle2D calloutCardBounds;

        public CircularScheduleSegment(ScheduleDTO schedule, double rawStartAngleDeg, double sweepAngleDeg,
                                       double rotationOffsetDeg, boolean isOngoing, boolean isPreviewTarget, Color color) {
            this.schedule = schedule;
            this.rawStartAngleDeg = rawStartAngleDeg;
            this.sweepAngleDeg = sweepAngleDeg;
            this.rotationOffsetDeg = rotationOffsetDeg;
            this.isOngoing = isOngoing;
            this.isPreviewTarget = isPreviewTarget;
            this.segmentColor = color;
        }
    }

    /** 키워드 검색 결과 목록에 표시할 일정 요약. */
    public static class ScheduleSummary {
        public final String scheduleId;
        public final String title;
        public final LocalDateTime startDateTime;
        public final LocalDateTime endDateTime;
        public final String location;
        public final String matchedSnippet;
        public final Color badgeColor;

        public ScheduleSummary(ScheduleDTO dto, String keyword) {
            this.scheduleId = dto.id;
            this.title = dto.title;
            this.startDateTime = dto.startDateTime;
            this.endDateTime = dto.endDateTime;
            this.location = dto.location;
            this.badgeColor = SCHEDULE_PALETTE[Math.abs(dto.colorIndex) % SCHEDULE_PALETTE.length];
            if (dto.description != null && !dto.description.isEmpty()) {
                this.matchedSnippet = dto.description;
            } else if (dto.location != null && !dto.location.isEmpty()) {
                this.matchedSnippet = "장소: " + dto.location;
            } else {
                this.matchedSnippet = dto.getTimeRangeText();
            }
        }
    }

    /** 작성 중인 일정 폼 내용을 메모리에 임시 보관한다(프로그램이 종료되면 사라짐). */
    public static class DraftSessionManager {
        private final Map<String, ScheduleDTO> sessionCache = new HashMap<>();
        private static final String ACTIVE_DRAFT_KEY = "CURRENT_FORM_DRAFT";
        private LocalDateTime lastSavedAt;

        /**
         * 제목, 장소, 설명 중 하나라도 입력돼 있으면 사본을 보관하고, 모두 비어 있으면 보관본을 지운다.
         *
         * @param draft 현재 폼 내용
         */
        public void saveDraft(ScheduleDTO draft) {
            if (draft == null) return;
            boolean hasContent = (draft.title != null && !draft.title.trim().isEmpty())
                    || (draft.location != null && !draft.location.trim().isEmpty())
                    || (draft.description != null && !draft.description.trim().isEmpty());
            if (hasContent) {
                sessionCache.put(ACTIVE_DRAFT_KEY, draft.copy());
                lastSavedAt = LocalDateTime.now();
            } else {
                sessionCache.remove(ACTIVE_DRAFT_KEY);
                lastSavedAt = null;
            }
        }

        public ScheduleDTO getDraft() {
            ScheduleDTO d = sessionCache.get(ACTIVE_DRAFT_KEY);
            return d != null ? d.copy() : null;
        }

        public boolean hasDraft() {
            return sessionCache.containsKey(ACTIVE_DRAFT_KEY);
        }

        public void clearDraft() {
            sessionCache.remove(ACTIVE_DRAFT_KEY);
            lastSavedAt = null;
        }

        public LocalDateTime getLastSavedAt() {
            return lastSavedAt;
        }
    }

    /** 일정 목록을 관리하고 사용자 홈 폴더의 <code>.chronocircle_schedules_v2.ser</code> 파일에 직렬화해 저장한다. */
    public static class ScheduleRepository {
        private final List<ScheduleDTO> schedules = new ArrayList<>();
        private final Path storagePath;

        public ScheduleRepository() {
            this.storagePath = Paths.get(System.getProperty("user.home"), ".chronocircle_schedules_v2.ser");
            if (!loadFromDisk()) {
                seedInitialData();
                saveToDisk();
            }
        }

        private void seedInitialData() {
            schedules.clear();
            LocalDate wireframeDate = LocalDate.of(2026, 10, 15);
            LocalDate today = LocalDate.now();

            seedDaySchedules(wireframeDate);
            if (!today.equals(wireframeDate)) {
                seedDaySchedules(today);
            }

            LocalDate baseMonth = LocalDate.of(today.getYear(), 10, 1);
            addSample(baseMonth.withDayOfMonth(5), 10, 0, 11, 30,
                    "소프트웨어설계 중간 점검", "공대 7호관 402호",
                    "Usecase Diagram 및 Sequence Diagram 발표 자료 최종 검토", true, 30, 0, 1);
            addSample(baseMonth.withDayOfMonth(8), 14, 0, 16, 0,
                    "UI/UX 프로토타입 피드백 미팅", "창업보육센터 세미나실",
                    "원형 대시보드 회전 인터랙션 및 탭 전환 애니메이션 사용성 평가", true, 15, 0, 2);
            addSample(baseMonth.withDayOfMonth(10), 15, 0, 16, 0,
                    "캡스톤 프로젝트 최종 회의", "소프트웨어공학 연구실",
                    "AI 자연어 파싱 모듈 및 임시 저장 매커니즘 통합 테스트", true, 30, 0, 0);
            addSample(baseMonth.withDayOfMonth(20), 13, 30, 15, 30,
                    "데이터베이스 인덱스 튜닝 실습", "전산실습실 301호",
                    "키워드 기반 고속 일정 검색 쿼리 최적화 실습", true, 30, 0, 3);
            addSample(baseMonth.withDayOfMonth(24), 18, 0, 20, 0,
                    "전남대 SW 해커톤 팀 빌딩", "정보마루 컨퍼런스홀",
                    "ChronoCircle 데스크톱 앱 데모 시연 및 팀원 네트워킹", true, 60, 0, 4);
            addSample(baseMonth.withDayOfMonth(28), 11, 0, 12, 30,
                    "지도교수님 정기 면담", "공대 7호관 교수연구실",
                    "진도 보고서 제출 및 향후 개발 일정 논의", true, 30, 0, 5);
        }

        private void seedDaySchedules(LocalDate date) {
            addSample(date, 8, 30, 9, 30,
                    "모닝 러닝 & 스트레칭", "대운동장 트랙",
                    "상쾌한 하루 시작을 위한 5km 조깅 및 코어 스트레칭", false, 10, 0, 3);

            addSample(date, 11, 0, 13, 0,
                    "소프트웨어공학 특강", "공대 7호관",
                    "객체지향 설계 원칙(SOLID) 및 UML 다이어그램 실무 적용 특강", true, 30, 0, 0);

            addSample(date, 13, 30, 15, 0,
                    "데이터베이스 시스템 실습", "전산실습실 301호",
                    "트랜잭션 격리 수준 실험 및 실행 계획(Execution Plan) 분석 실습", true, 15, 0, 2);

            addSample(date, 15, 30, 17, 0,
                    "캡스톤 프로젝트 중간 회의", "소프트웨어공학 연구실",
                    "Java Swing 원형 타임라인 렌더링 및 자연어 프롬프트 파서 코드 리뷰", true, 15, 0, 1);

            addSample(date, 19, 0, 21, 0,
                    "알고리즘 스터디", "중앙도서관 그룹스터디룸 B",
                    "그래프 탐색(BFS/DFS) 및 다이나믹 프로그래밍 실전 문제 풀이", true, 30, 0, 4);
        }

        private void addSample(LocalDate date, int sh, int sm, int eh, int em,
                               String title, String loc, String desc,
                               boolean noti, int leadMin, int repMin, int colorIdx) {
            LocalDateTime s = LocalDateTime.of(date, LocalTime.of(sh, sm));
            LocalDateTime e = LocalDateTime.of(date, LocalTime.of(eh, em));
            for (ScheduleDTO existing : schedules) {
                if (existing.title.equals(title) && existing.startDateTime.equals(s)) {
                    return;
                }
            }
            schedules.add(new ScheduleDTO(UUID.randomUUID().toString(), title, s, e, loc, desc, noti, leadMin, repMin, colorIdx));
        }

        public synchronized List<ScheduleDTO> findAll() {
            return schedules.stream()
                    .map(ScheduleDTO::copy)
                    .sorted(Comparator.comparing(a -> a.startDateTime))
                    .collect(Collectors.toList());
        }

        /**
         * @param date 조회할 날짜
         * @return 해당 날짜에 걸친 일정 목록 (시작 시각 순)
         */
        public synchronized List<ScheduleDTO> findByDate(LocalDate date) {
            return schedules.stream()
                    .filter(s -> s.occursOn(date))
                    .map(ScheduleDTO::copy)
                    .sorted(Comparator.comparing(a -> a.startDateTime))
                    .collect(Collectors.toList());
        }

        public synchronized Optional<ScheduleDTO> findById(String id) {
            return schedules.stream()
                    .filter(s -> Objects.equals(s.id, id))
                    .findFirst()
                    .map(ScheduleDTO::copy);
        }

        /**
         * 같은 ID의 일정이 있으면 교체하고, 없으면 추가한 뒤 파일에 저장한다.
         *
         * @param dto 저장할 일정
         */
        public synchronized void upsert(ScheduleDTO dto) {
            ScheduleDTO copy = dto.copy();
            copy.recalculateNextNotificationTime();
            for (int i = 0; i < schedules.size(); i++) {
                if (Objects.equals(schedules.get(i).id, copy.id)) {
                    schedules.set(i, copy);
                    saveToDisk();
                    return;
                }
            }
            if (copy.id == null || copy.id.isEmpty()) {
                copy.id = UUID.randomUUID().toString();
            }
            schedules.add(copy);
            saveToDisk();
        }

        public synchronized boolean deleteById(String id) {
            boolean removed = schedules.removeIf(s -> Objects.equals(s.id, id));
            if (removed) saveToDisk();
            return removed;
        }

        /**
         * 주어진 시간대와 겹치는 일정을 찾는다.
         *
         * @param start 시작 일시
         * @param end 종료 일시
         * @param excludeId 검사에서 제외할 일정 ID (수정 중인 자기 자신)
         * @return 시간이 겹치는 일정 목록
         */
        public synchronized List<ScheduleDTO> findOverlapping(LocalDateTime start, LocalDateTime end, String excludeId) {
            return schedules.stream()
                    .filter(s -> !Objects.equals(s.id, excludeId))
                    .filter(s -> s.startDateTime.isBefore(end) && start.isBefore(s.endDateTime))
                    .map(ScheduleDTO::copy)
                    .collect(Collectors.toList());
        }

        @SuppressWarnings("unchecked")
        private boolean loadFromDisk() {
            if (!Files.exists(storagePath)) return false;
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(storagePath))) {
                Object obj = ois.readObject();
                if (obj instanceof List) {
                    schedules.clear();
                    schedules.addAll((List<ScheduleDTO>) obj);
                    return !schedules.isEmpty();
                }
            } catch (Exception ignored) {}
            return false;
        }

        public synchronized void saveToDisk() {
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(storagePath))) {
                oos.writeObject(new ArrayList<>(schedules));
            } catch (Exception ignored) {}
        }
    }

    /**
     * 한국어 자연어 문장에서 날짜, 시간, 장소, 제목을 추출하는 파서.
     *
     * <p>Gemini API 키가 있으면 Gemini(LLM)에 JSON 형식 추출을 요청해 그 결과를 쓴다.
     * 키가 없거나, 호출이 실패하거나, 응답이 형식에 맞지 않으면 내장 정규식 규칙으로 분석한다.</p>
     *
     * <p>키는 환경 변수 <code>GEMINI_API_KEY</code> 또는 시스템 속성 <code>gemini.api.key</code>로 지정하고,
     * 모델은 <code>GEMINI_MODEL</code>(기본값 {@value #DEFAULT_MODEL})로 바꿀 수 있다.</p>
     */
    public static class AIPromptParser {
        public static final String DEFAULT_MODEL = "gemini-3.8-flash";
        private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

        /**
         * @param promptText 사용자가 입력한 문장
         * @param referenceDate "내일", "다음 주" 같은 상대 날짜의 기준일
         * @return 분석 결과. {@link ParsedScheduleDTO#parseConfidenceNote}에 어떤 방식으로 분석했는지 적힌다
         */
        public ParsedScheduleDTO extractScheduleData(String promptText, LocalDate referenceDate) {
            if (promptText == null || promptText.trim().isEmpty()) {
                return new ParsedScheduleDTO("", LocalDateTime.of(referenceDate, LocalTime.of(15, 0)),
                        LocalDateTime.of(referenceDate, LocalTime.of(16, 0)), "", "", "", "입력된 문장이 없습니다.");
            }

            String text = promptText.trim();
            String apiKey = resolveApiKey();
            if (apiKey == null) {
                return parseKoreanNaturalLanguage(text, referenceDate);
            }
            try {
                return callGeminiApi(text, referenceDate, apiKey);
            } catch (Exception e) {
                ParsedScheduleDTO fallback = parseKoreanNaturalLanguage(text, referenceDate);
                fallback.parseConfidenceNote = "Gemini 분석 실패(" + e.getMessage() + "), 내장 파서 사용 — " + fallback.parseConfidenceNote;
                return fallback;
            }
        }

        private static String resolveApiKey() {
            String key = System.getProperty("gemini.api.key");
            if (key == null || key.trim().isEmpty()) key = System.getenv("GEMINI_API_KEY");
            return (key == null || key.trim().isEmpty()) ? null : key.trim();
        }

        private static String resolveModel() {
            String model = System.getenv("GEMINI_MODEL");
            return (model == null || model.trim().isEmpty()) ? DEFAULT_MODEL : model.trim();
        }

        private ParsedScheduleDTO callGeminiApi(String promptText, LocalDate refDate, String apiKey) throws IOException {
            String model = resolveModel();
            HttpURLConnection conn = (HttpURLConnection) new URL(API_BASE + model + ":generateContent").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("x-goog-api-key", apiKey);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(buildGeminiRequest(promptText, refDate).getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            ParsedScheduleDTO result = parseGeminiResponse(body, promptText, refDate);
            result.parseConfidenceNote = "Gemini(" + model + ") " + result.parseConfidenceNote;
            return result;
        }

        static String buildGeminiRequest(String promptText, LocalDate refDate) {
            String instruction = "너는 한국어 일정 문장에서 일정 정보를 추출하는 도우미다.\n"
                    + "기준일은 " + refDate + " (" + refDate.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.KOREAN) + ")이다.\n"
                    + "'내일', '다음 주 수요일' 같은 상대 날짜는 기준일로부터 계산한다.\n"
                    + "date는 YYYY-MM-DD, startTime과 endTime은 24시간제 HH:mm로 쓴다.\n"
                    + "시작 시각이 없으면 15:00, 종료 시각이나 소요 시간이 없으면 endTime은 빈 문자열로 둔다.\n"
                    + "title에는 날짜, 시간, 장소, '등록해줘' 같은 요청 표현을 빼고 일정 이름만 쓴다. 장소가 없으면 location은 빈 문자열이다.\n"
                    + "문장: " + promptText;
            return "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":" + jsonQuote(instruction) + "}]}],"
                    + "\"generationConfig\":{"
                    + "\"responseMimeType\":\"application/json\","
                    + "\"responseSchema\":{\"type\":\"OBJECT\",\"properties\":{"
                    + "\"title\":{\"type\":\"STRING\"},\"date\":{\"type\":\"STRING\"},"
                    + "\"startTime\":{\"type\":\"STRING\"},\"endTime\":{\"type\":\"STRING\"},"
                    + "\"location\":{\"type\":\"STRING\"}},"
                    + "\"required\":[\"title\",\"date\",\"startTime\",\"endTime\",\"location\"]}}}";
        }

        /**
         * generateContent 응답 본문에서 일정 정보를 꺼낸다.
         *
         * @param body Gemini 응답 JSON
         * @param rawPrompt 사용자가 입력한 원문
         * @param refDate 기준일 (날짜를 못 읽었을 때 사용)
         * @return 분석 결과
         * @throws IOException 응답 구조나 날짜·시간 형식이 맞지 않을 때
         */
        static ParsedScheduleDTO parseGeminiResponse(String body, String rawPrompt, LocalDate refDate) throws IOException {
            Object root = MiniJson.parse(body);
            Object text = MiniJson.path(root, "candidates", 0, "content", "parts", 0, "text");
            if (!(text instanceof String)) {
                throw new IOException("응답에 결과 텍스트가 없음");
            }
            Object data = MiniJson.parse((String) text);
            if (!(data instanceof Map)) {
                throw new IOException("결과가 JSON 객체가 아님");
            }
            Map<?, ?> m = (Map<?, ?>) data;
            String title = str(m.get("title"));
            String location = str(m.get("location"));
            LocalDate date;
            LocalTime start;
            try {
                date = str(m.get("date")).isEmpty() ? refDate : LocalDate.parse(str(m.get("date")));
                start = LocalTime.parse(str(m.get("startTime")).isEmpty() ? "15:00" : str(m.get("startTime")));
            } catch (Exception e) {
                throw new IOException("날짜/시간 형식 오류");
            }
            LocalDateTime startDT = LocalDateTime.of(date, start);
            LocalDateTime endDT = startDT.plusHours(1);
            if (!str(m.get("endTime")).isEmpty()) {
                try {
                    LocalDateTime e = LocalDateTime.of(date, LocalTime.parse(str(m.get("endTime"))));
                    if (e.isAfter(startDT)) endDT = e;
                } catch (Exception ignored) {}
            }
            if (title.isEmpty()) {
                title = location.isEmpty() ? "새 스마트 일정" : location + " 미팅";
            }
            String note = String.format("분석 완료: %s %s (%s)",
                    date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
                    start.format(DateTimeFormatter.ofPattern("HH:mm")),
                    location.isEmpty() ? "장소 미지정" : location);
            return new ParsedScheduleDTO(title, startDT, endDT, location,
                    "자연어 입력 원문: \"" + rawPrompt + "\"", rawPrompt, note);
        }

        private static String str(Object o) {
            return o == null ? "" : o.toString().trim();
        }

        private static String jsonQuote(String s) {
            StringBuilder sb = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                }
            }
            return sb.append('"').toString();
        }

        /**
         * 날짜, 시간 범위, 소요 시간, 장소 순으로 문장에서 떼어 내고, 남은 문자열을 제목으로 쓴다.
         * 시간이 없으면 15시, 소요 시간이 없으면 1시간으로 정한다.
         *
         * @param raw 원문
         * @param baseDate 상대 날짜의 기준일
         * @return 분석 결과
         */
        public ParsedScheduleDTO parseKoreanNaturalLanguage(String raw, LocalDate baseDate) {
            String working = raw.trim();
            LocalDate targetDate = baseDate;

            Matcher mFullDate = Pattern.compile("(20\\d{2})[년.\\-/\\s]+(\\d{1,2})[월.\\-/\\s]+(\\d{1,2})일?").matcher(working);
            Matcher mMonthDay = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일").matcher(working);

            if (mFullDate.find()) {
                int y = Integer.parseInt(mFullDate.group(1));
                int m = Math.max(1, Math.min(12, Integer.parseInt(mFullDate.group(2))));
                int d = Math.max(1, Math.min(28, Integer.parseInt(mFullDate.group(3))));
                try { targetDate = LocalDate.of(y, m, Integer.parseInt(mFullDate.group(3))); }
                catch (Exception e) { targetDate = LocalDate.of(y, m, d); }
                working = working.replace(mFullDate.group(0), " ");
            } else if (mMonthDay.find()) {
                int m = Math.max(1, Math.min(12, Integer.parseInt(mMonthDay.group(1))));
                int d = Integer.parseInt(mMonthDay.group(2));
                try { targetDate = LocalDate.of(baseDate.getYear(), m, d); }
                catch (Exception ignored) {}
                working = working.replace(mMonthDay.group(0), " ");
            } else if (working.contains("글피")) {
                targetDate = baseDate.plusDays(3);
                working = working.replaceFirst("글피", " ");
            } else if (working.contains("모레") || working.contains("내일모레")) {
                targetDate = baseDate.plusDays(2);
                working = working.replaceFirst("내일모레|모레", " ");
            } else if (working.contains("내일")) {
                targetDate = baseDate.plusDays(1);
                working = working.replaceFirst("내일", " ");
            } else if (working.contains("오늘") || working.contains("금일")) {
                targetDate = baseDate;
                working = working.replaceFirst("오늘|금일", " ");
            }

            String[] dowNames = {"월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"};
            DayOfWeek[] dows = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
            };
            boolean nextWeek = working.contains("다음주") || working.contains("다음 주");
            if (nextWeek) working = working.replaceFirst("다음\\s*주", " ");
            for (int i = 0; i < dowNames.length; i++) {
                if (working.contains(dowNames[i])) {
                    int diff = dows[i].getValue() - baseDate.getDayOfWeek().getValue();
                    if (diff <= 0 || nextWeek) diff += 7;
                    targetDate = baseDate.plusDays(diff);
                    working = working.replaceFirst(dowNames[i], " ");
                    break;
                }
            }

            int startHour = 15;
            int startMin = 0;
            int durationMin = 60;
            Integer endHour = null;
            Integer endMin = null;

            Pattern rangePat = Pattern.compile("(오전|아침|새벽|오후|저녁|밤|낮)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분|반)?\\s*(?:부터|~|-)\\s*(오전|오후|저녁|밤)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분|반)?(?:까지)?");
            Matcher mRange = rangePat.matcher(working);
            if (mRange.find()) {
                String ampm1 = mRange.group(1);
                int h1 = Integer.parseInt(mRange.group(2));
                int min1 = mRange.group(3) != null ? Integer.parseInt(mRange.group(3)) : (mRange.group(0).contains("시반") || mRange.group(0).contains("시 반") ? 30 : 0);
                startHour = normalizeHour(ampm1, h1);
                startMin = min1;

                String ampm2 = mRange.group(4) != null ? mRange.group(4) : ampm1;
                int h2 = Integer.parseInt(mRange.group(5));
                int min2 = mRange.group(6) != null ? Integer.parseInt(mRange.group(6)) : 0;
                endHour = normalizeHour(ampm2, h2);
                if (endHour < startHour && h2 <= 12) endHour += 12;
                endMin = min2;
                working = working.replace(mRange.group(0), " ");
            } else {
                Pattern singleTimePat = Pattern.compile("(오전|아침|새벽|오후|저녁|밤|낮)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분|\\s*(반))?(?:에)?");
                Matcher mSingle = singleTimePat.matcher(working);
                if (mSingle.find()) {
                    String ampm = mSingle.group(1);
                    int h = Integer.parseInt(mSingle.group(2));
                    int min = 0;
                    if (mSingle.group(3) != null) min = Integer.parseInt(mSingle.group(3));
                    else if (mSingle.group(4) != null) min = 30;
                    startHour = normalizeHour(ampm, h);
                    startMin = min;
                    working = working.replace(mSingle.group(0), " ");
                } else {
                    Pattern colonTime = Pattern.compile("(\\d{1,2}):(\\d{2})");
                    Matcher mColon = colonTime.matcher(working);
                    if (mColon.find()) {
                        startHour = Math.min(23, Integer.parseInt(mColon.group(1)));
                        startMin = Math.min(59, Integer.parseInt(mColon.group(2)));
                        working = working.replace(mColon.group(0), " ");
                    }
                }
            }

            Matcher mDurHour = Pattern.compile("(\\d{1,2})시간(?:\\s*(반))?(?:\\s*동안)?").matcher(working);
            if (mDurHour.find()) {
                durationMin = Integer.parseInt(mDurHour.group(1)) * 60 + (mDurHour.group(2) != null ? 30 : 0);
                working = working.replace(mDurHour.group(0), " ");
            }

            String location = "";
            Matcher mLoc = Pattern.compile("([가-힣A-Za-z0-9\\s]{2,25}?)에서\\b").matcher(working);
            if (mLoc.find()) {
                location = mLoc.group(1).trim();
                location = location.replaceAll("^(오늘|내일|모레|오전|오후|저녁|아침)\\s*", "").trim();
                working = working.replace(mLoc.group(0), " ");
            } else {
                Matcher mPlaceToken = Pattern.compile("([가-힣A-Za-z0-9]+\\s*(?:연구실|회의실\\s*[A-Za-z0-9]*|호관|실습실|도서관|센터|강당|카페|역))").matcher(working);
                if (mPlaceToken.find()) {
                    location = mPlaceToken.group(1).trim();
                    working = working.replaceFirst(Pattern.quote(mPlaceToken.group(0)) + "(?:에서|에)?", " ");
                }
            }

            String title = working.replaceAll("\\s+", " ").trim();
            title = title.replaceAll("^(에서|에|부터|까지|일정|등록|추가)\\s*", "").trim();
            title = title.replaceAll("\\s*(일정\\s*등록|추가해줘|등록해줘|잡아줘|예약해줘|있음|하기)$", "").trim();
            if (title.isEmpty()) {
                title = location.isEmpty() ? "새 스마트 일정" : location + " 미팅";
            }

            LocalTime sTime = LocalTime.of(Math.max(0, Math.min(23, startHour)), Math.max(0, Math.min(59, startMin)));
            LocalDateTime startDT = LocalDateTime.of(targetDate, sTime);
            LocalDateTime endDT;
            if (endHour != null && endMin != null) {
                LocalTime eTime = LocalTime.of(Math.max(0, Math.min(23, endHour)), Math.max(0, Math.min(59, endMin)));
                endDT = LocalDateTime.of(targetDate, eTime);
                if (!endDT.isAfter(startDT)) endDT = startDT.plusMinutes(durationMin);
            } else {
                endDT = startDT.plusMinutes(durationMin);
            }

            String note = String.format("내장 파서 분석 완료: %s %s (%s)",
                    targetDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
                    sTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    location.isEmpty() ? "장소 미지정" : location);

            return new ParsedScheduleDTO(title, startDT, endDT, location,
                    "자연어 입력 원문: \"" + raw + "\"", raw, note);
        }

        private int normalizeHour(String ampm, int hour) {
            if (ampm != null) {
                if ((ampm.contains("오후") || ampm.contains("저녁") || ampm.contains("밤") || ampm.contains("낮")) && hour < 12) {
                    return hour + 12;
                }
                if ((ampm.contains("오전") || ampm.contains("아침") || ampm.contains("새벽")) && hour == 12) {
                    return 0;
                }
            } else {
                if (hour >= 1 && hour <= 6) return hour + 12;
            }
            return Math.max(0, Math.min(23, hour));
        }
    }

    /** Gemini 응답을 읽기 위한 최소 JSON 파서. 객체는 Map, 배열은 List, 숫자는 Double로 돌려준다. */
    static final class MiniJson {
        private final String s;
        private int i;

        private MiniJson(String s) {
            this.s = s;
        }

        static Object parse(String text) throws IOException {
            MiniJson p = new MiniJson(text);
            Object v = p.value();
            p.ws();
            if (p.i != p.s.length()) throw new IOException("JSON 뒤에 불필요한 문자");
            return v;
        }

        /** 키(String)와 인덱스(Integer)를 차례로 따라가 값을 찾는다. 없으면 null. */
        static Object path(Object node, Object... keys) {
            for (Object k : keys) {
                if (k instanceof String && node instanceof Map) node = ((Map<?, ?>) node).get(k);
                else if (k instanceof Integer && node instanceof List && ((List<?>) node).size() > (Integer) k) node = ((List<?>) node).get((Integer) k);
                else return null;
            }
            return node;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private Object value() throws IOException {
            ws();
            if (i >= s.length()) throw new IOException("JSON이 끝남");
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>();
                i++;
                ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    expect(':');
                    m.put(k, value());
                    ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect('}');
                    return m;
                }
            }
            if (c == '[') {
                List<Object> l = new ArrayList<>();
                i++;
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    ws();
                    if (s.charAt(i) == ',') { i++; continue; }
                    expect(']');
                    return l;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new IOException("JSON 형식 오류 (위치 " + i + ")");
            return Double.parseDouble(s.substring(st, i));
        }

        private String string() throws IOException {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: sb.append(e);
                }
            }
            throw new IOException("문자열이 닫히지 않음");
        }

        private void expect(char c) throws IOException {
            if (i >= s.length() || s.charAt(i) != c) throw new IOException("'" + c + "' 필요 (위치 " + i + ")");
            i++;
        }
    }

    /** 대시보드 갱신과 알림 검사를 1초마다 구동하는 Swing 타이머로, 테스트용 시각 이동(시뮬레이션) 기능도 제공한다. */
    public static class SystemTimer {
        private final javax.swing.Timer swingTimer;
        private boolean autoRefreshPaused = false;
        private Duration simulationOffset = Duration.ZERO;
        private final Runnable onTickCallback;

        public SystemTimer(Runnable onTickCallback) {
            this.onTickCallback = onTickCallback;
            this.swingTimer = new javax.swing.Timer(1000, e -> {
                if (onTickCallback != null) {
                    onTickCallback.run();
                }
            });
        }

        public void start() {
            if (!swingTimer.isRunning()) {
                swingTimer.start();
            }
        }

        /** 사용자가 원형 대시보드를 돌리는 동안 현재 시각 자동 추적을 멈춘다. */
        public void pauseAutoRefresh() {
            this.autoRefreshPaused = true;
        }

        /** 현재 시각 자동 추적을 다시 시작한다. */
        public void resumeAutoRefresh() {
            this.autoRefreshPaused = false;
            if (!swingTimer.isRunning()) {
                swingTimer.start();
            }
        }

        public boolean isAutoRefreshPaused() {
            return autoRefreshPaused;
        }

        /** @return 시뮬레이션 오프셋이 반영된 현재 일시 */
        public LocalDateTime now() {
            return LocalDateTime.now().plus(simulationOffset);
        }

        public void addSimulationMinutes(long minutes) {
            simulationOffset = simulationOffset.plusMinutes(minutes);
            if (onTickCallback != null) onTickCallback.run();
        }

        public void setSimulationTarget(LocalDateTime target) {
            simulationOffset = Duration.between(LocalDateTime.now(), target);
            if (onTickCallback != null) onTickCallback.run();
        }

        public void resetSimulationOffset() {
            simulationOffset = Duration.ZERO;
            if (onTickCallback != null) onTickCallback.run();
        }

        public boolean isSimulated() {
            return Math.abs(simulationOffset.toMinutes()) > 0;
        }
    }

    /** 화면과 저장소·파서 사이를 연결하며 기능 명세의 핵심 함수 6개를 구현하는 컨트롤러. */
    public static class ScheduleController {
        private final ScheduleRepository repository;
        private final DraftSessionManager draftManager;
        private final AIPromptParser aiParser;
        private final Set<String> firedNotificationKeys = new HashSet<>();
        private MainFrame mainFrame;

        public ScheduleController(ScheduleRepository repository, DraftSessionManager draftManager, AIPromptParser aiParser) {
            this.repository = repository;
            this.draftManager = draftManager;
            this.aiParser = aiParser;
        }

        public void setMainFrame(MainFrame mainFrame) {
            this.mainFrame = mainFrame;
        }

        public ScheduleRepository getRepository() {
            return repository;
        }

        public DraftSessionManager getDraftManager() {
            return draftManager;
        }

        /**
         * 자연어 문장을 분석해 일정 정보로 변환한다. 선택된 날짜를 기준일로 쓴다.
         *
         * @param promptText 예: "내일 오후 3시 회의실 A에서 팀 프로젝트 회의"
         * @return 제목, 시작/종료 일시, 장소, 설명을 담은 분석 결과
         */
        public ParsedScheduleDTO parseNaturalLanguagePrompt(String promptText) {
            LocalDate baseDate = (mainFrame != null) ? mainFrame.getSelectedDate() : LocalDate.now();
            return aiParser.extractScheduleData(promptText, baseDate);
        }

        /**
         * 일정을 저장하거나 작성 중인 내용을 임시 보관한다.
         *
         * @param scheduleData 저장할 일정
         * @param isDraft true면 검증 없이 임시 보관, false면 제목과 시간을 검증한 뒤 저장하고 임시 보관본을 지운다
         * @return 저장(또는 임시 보관)에 성공하면 true, 검증에 실패하면 false
         */
        public boolean saveOrUpdateSchedule(ScheduleDTO scheduleData, boolean isDraft) {
            if (scheduleData == null) return false;
            if (isDraft) {
                draftManager.saveDraft(scheduleData);
                return true;
            }
            if (scheduleData.title == null || scheduleData.title.trim().isEmpty()) {
                return false;
            }
            if (scheduleData.startDateTime == null || scheduleData.endDateTime == null) {
                return false;
            }
            if (!scheduleData.endDateTime.isAfter(scheduleData.startDateTime)) {
                return false;
            }
            repository.upsert(scheduleData);
            draftManager.clearDraft();
            if (mainFrame != null) {
                mainFrame.refreshAllViews();
            }
            return true;
        }

        /**
         * 하루 1,440분을 360도로 환산해 각 일정의 호 시작 각도와 중심각을 계산한다.
         *
         * @param targetDate 조회할 날짜
         * @param currentTime 진행 중 여부를 판단할 기준 시각
         * @return 일정별 각도, 색상, 진행 여부 목록
         */
        public List<CircularScheduleSegment> calculateCircularSegments(LocalDate targetDate, LocalTime currentTime) {
            List<ScheduleDTO> daySchedules = repository.findByDate(targetDate);
            List<CircularScheduleSegment> segments = new ArrayList<>();

            double currentMinutes = currentTime.getHour() * 60.0 + currentTime.getMinute() + currentTime.getSecond() / 60.0;
            double rotationOffsetDeg = ((currentMinutes - 720.0) / 1440.0) * 360.0;

            LocalDateTime targetNow = LocalDateTime.of(targetDate, currentTime);

            for (int i = 0; i < daySchedules.size(); i++) {
                ScheduleDTO s = daySchedules.get(i);
                LocalDateTime dayStart = targetDate.atStartOfDay();
                LocalDateTime dayEnd = targetDate.atTime(23, 59, 59);

                LocalDateTime effectiveStart = s.startDateTime.isBefore(dayStart) ? dayStart : s.startDateTime;
                LocalDateTime effectiveEnd = s.endDateTime.isAfter(dayEnd) ? dayEnd : s.endDateTime;

                double startMin = effectiveStart.getHour() * 60.0 + effectiveStart.getMinute();
                double endMin = effectiveEnd.getHour() * 60.0 + effectiveEnd.getMinute();
                double durationMin = Math.max(15.0, endMin - startMin);

                // Arc2D 기준 0시는 아래쪽(270°), 음수 sweep으로 시계 방향 진행
                double startAngle = 270.0 - (startMin / 1440.0) * 360.0;
                double sweepAngle = -(durationMin / 1440.0) * 360.0;

                boolean ongoing = s.isOngoingAt(targetNow);
                Color color = ongoing
                        ? PRIMARY_BLUE
                        : SCHEDULE_PALETTE[Math.abs(s.colorIndex) % SCHEDULE_PALETTE.length];

                segments.add(new CircularScheduleSegment(
                        s, startAngle, sweepAngle, rotationOffsetDeg, ongoing, false, color
                ));
            }
            return segments;
        }

        /**
         * [Refresh] 버튼을 누르면 자동 추적을 재개하고 원형 대시보드를 현재 시각 위치로 되돌린다.
         *
         * @param canvas 원형 대시보드 패널
         * @param timer 실시간 갱신 타이머
         */
        public void resyncDashboardToCurrentTime(CircularCanvasPanel canvas, SystemTimer timer) {
            if (timer != null) {
                timer.resumeAutoRefresh();
            }
            if (canvas != null) {
                canvas.animateResetToCurrentTime();
            }
            if (mainFrame != null) {
                mainFrame.updateSyncBannerState(false);
            }
        }

        /**
         * 알림 시각이 된 일정에 알림을 보낸다. 같은 알림은 한 번만 보내며, 반복 알림이면 다음 알림 시각을 갱신한다.
         *
         * @param currentDateTime 현재 일시 (타이머가 1초마다 전달)
         * @return 이번에 보낸 알림 수
         */
        public int checkAndDispatchNotifications(LocalDateTime currentDateTime) {
            List<ScheduleDTO> all = repository.findAll();
            int dispatchedCount = 0;
            LocalDateTime truncatedNow = currentDateTime.truncatedTo(ChronoUnit.MINUTES);

            for (ScheduleDTO s : all) {
                if (!s.notificationEnabled || s.nextNotificationTime == null) continue;
                LocalDateTime triggerMin = s.nextNotificationTime.truncatedTo(ChronoUnit.MINUTES);

                long diffMin = ChronoUnit.MINUTES.between(triggerMin, truncatedNow);
                String dedupKey = s.id + "_" + triggerMin.toString();

                if (diffMin >= 0 && diffMin <= 1 && !firedNotificationKeys.contains(dedupKey)) {
                    firedNotificationKeys.add(dedupKey);
                    dispatchedCount++;

                    if (mainFrame != null) {
                        mainFrame.dispatchNotificationUI(s);
                    }

                    if (s.repeatIntervalMinutes > 0) {
                        LocalDateTime nextTime = triggerMin.plusMinutes(s.repeatIntervalMinutes);
                        if (!nextTime.isAfter(s.endDateTime)) {
                            s.nextNotificationTime = nextTime;
                            repository.upsert(s);
                        }
                    }
                }
            }
            return dispatchedCount;
        }

        /**
         * 제목, 장소, 설명에 키워드가 들어간 일정을 대소문자 구분 없이 찾는다.
         *
         * @param keyword 검색어
         * @param startDate 검색 시작일 (null이면 제한 없음)
         * @param endDate 검색 종료일 (null이면 제한 없음)
         * @return 시작 시각 순으로 정렬된 검색 결과. 검색어가 비어 있으면 빈 목록
         */
        public List<ScheduleSummary> searchSchedulesByKeyword(String keyword, LocalDate startDate, LocalDate endDate) {
            if (keyword == null || keyword.trim().isEmpty()) {
                return Collections.emptyList();
            }
            String q = keyword.trim().toLowerCase(Locale.ROOT);
            LocalDate sDate = (startDate != null) ? startDate : LocalDate.of(2000, 1, 1);
            LocalDate eDate = (endDate != null) ? endDate : LocalDate.of(2099, 12, 31);

            return repository.findAll().stream()
                    .filter(s -> {
                        LocalDate d = s.startDateTime.toLocalDate();
                        return !d.isBefore(sDate) && !d.isAfter(eDate);
                    })
                    .filter(s -> (s.title != null && s.title.toLowerCase(Locale.ROOT).contains(q))
                            || (s.location != null && s.location.toLowerCase(Locale.ROOT).contains(q))
                            || (s.description != null && s.description.toLowerCase(Locale.ROOT).contains(q)))
                    .sorted(Comparator.comparing(s -> s.startDateTime))
                    .map(s -> new ScheduleSummary(s, keyword))
                    .collect(Collectors.toList());
        }
    }

    /** 둥근 모서리 배경과 테두리를 그리는 카드형 패널. */
    public static class RoundedCardPanel extends JPanel {
        private final int radius;
        private Color bgColor;
        private Color borderColor;
        private float borderWidth = 1.2f;
        private Color accentStripeColor = null;
        private boolean doubleFrame = false;

        public RoundedCardPanel(int radius, Color bgColor, Color borderColor) {
            this(radius, bgColor, borderColor, 1.2f, null, false);
        }

        public RoundedCardPanel(int radius, Color bgColor, Color borderColor,
                                float borderWidth, Color accentStripeColor, boolean doubleFrame) {
            this.radius = radius;
            this.bgColor = bgColor;
            this.borderColor = borderColor;
            this.borderWidth = borderWidth;
            this.accentStripeColor = accentStripeColor;
            this.doubleFrame = doubleFrame;
            setOpaque(false);
        }

        public void setCardColors(Color bg, Color border) {
            this.bgColor = bg;
            this.borderColor = border;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int inset = doubleFrame ? 2 : 0;

            if (doubleFrame && borderColor != null) {
                g2.setColor(new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 42));
                g2.setStroke(new BasicStroke(4.5f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, radius + 2, radius + 2);
            }

            if (bgColor != null) {
                g2.setColor(bgColor);
                g2.fillRoundRect(inset, inset, w - 1 - inset * 2, h - 1 - inset * 2, radius, radius);
            }

            if (accentStripeColor != null) {
                Shape oldClip = g2.getClip();
                g2.setClip(new RoundRectangle2D.Double(inset, inset, w - 1 - inset * 2, h - 1 - inset * 2, radius, radius));
                g2.setColor(accentStripeColor);
                g2.fillRect(inset, inset, 6, h - inset * 2);
                g2.setClip(oldClip);
            }

            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(borderWidth));
                g2.drawRoundRect(inset, inset, w - 1 - inset * 2, h - 1 - inset * 2, radius, radius);

                if (doubleFrame) {
                    g2.setColor(new Color(0x1E, 0x1B, 0x4B));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, radius + 2, radius + 2);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** 마우스를 올리면 배경색이 바뀌는 둥근 버튼. */
    public static class ModernButton extends JButton {
        private Color normalBg;
        private Color hoverBg;
        private Color fgColor;
        private Color borderCol;
        private final int radius;
        private boolean hovered = false;

        public ModernButton(String text, Color normalBg, Color hoverBg, Color fgColor, Color borderCol, int radius) {
            super(text);
            this.normalBg = normalBg;
            this.hoverBg = hoverBg;
            this.fgColor = fgColor;
            this.borderCol = borderCol;
            this.radius = radius;
            setMargin(new Insets(0, 4, 0, 4));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(fgColor);
            setFont(uiFont(Font.BOLD, 13));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        public void updateColors(Color normalBg, Color hoverBg, Color fgColor, Color borderCol) {
            this.normalBg = normalBg;
            this.hoverBg = hoverBg;
            this.fgColor = fgColor;
            this.borderCol = borderCol;
            setForeground(fgColor);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovered && isEnabled() ? hoverBg : normalBg);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (borderCol != null) {
                g2.setColor(borderCol);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** 선택 표시가 애니메이션으로 이동하는 [일 | 월 | 년] 보기 전환 버튼. */
    public static class AnimatedSegmentedControl extends JPanel {
        private final String[] labels = {"일", "월", "년"};
        private final String[] keys = {"DAY", "MONTH", "YEAR"};
        private int selectedIndex = 0;
        private double animatedIndex = 0.0;
        private javax.swing.Timer animTimer;
        private final JToggleButton[] toggleButtons = new JToggleButton[3];
        private final ButtonGroup buttonGroup = new ButtonGroup();
        private java.util.function.Consumer<String> onModeChanged;

        public AnimatedSegmentedControl(java.util.function.Consumer<String> onModeChanged) {
            this.onModeChanged = onModeChanged;
            setOpaque(false);
            setPreferredSize(new Dimension(156, 38));
            setLayout(new GridLayout(1, 3, 0, 0));
            setBorder(new EmptyBorder(4, 4, 4, 4));

            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                JToggleButton btn = new JToggleButton(labels[i]);
                btn.setFocusPainted(false);
                btn.setBorderPainted(false);
                btn.setContentAreaFilled(false);
                btn.setOpaque(false);
                btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                btn.setFont(uiFont(Font.BOLD, 13));
                btn.addActionListener(e -> selectIndex(idx, true));
                toggleButtons[i] = btn;
                buttonGroup.add(btn);
                add(btn);
            }
            toggleButtons[0].setSelected(true);
            updateLabelColors();
        }

        public void selectMode(String modeKey, boolean triggerCallback) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equals(modeKey)) {
                    selectIndex(i, triggerCallback);
                    break;
                }
            }
        }

        private void selectIndex(int targetIdx, boolean triggerCallback) {
            this.selectedIndex = targetIdx;
            toggleButtons[targetIdx].setSelected(true);
            updateLabelColors();

            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            final double startPos = animatedIndex;
            final double endPos = targetIdx;
            final long startTime = System.currentTimeMillis();
            final int durationMs = 220;

            animTimer = new javax.swing.Timer(12, e -> {
                double t = Math.min(1.0, (System.currentTimeMillis() - startTime) / (double) durationMs);
                double eased = 1.0 - Math.pow(1.0 - t, 3);
                animatedIndex = startPos + (endPos - startPos) * eased;
                repaint();
                if (t >= 1.0) {
                    animatedIndex = endPos;
                    ((javax.swing.Timer) e.getSource()).stop();
                }
            });
            animTimer.start();

            if (triggerCallback && onModeChanged != null) {
                onModeChanged.accept(keys[targetIdx]);
            }
        }

        private void updateLabelColors() {
            for (int i = 0; i < toggleButtons.length; i++) {
                toggleButtons[i].setForeground(i == selectedIndex ? PRIMARY_BLUE : TEXT_SECONDARY);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            g2.setColor(BG_SUBTLE);
            g2.fillRoundRect(0, 0, w, h, 12, 12);
            g2.setColor(BORDER_COLOR);
            g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);

            int pad = 4;
            double cellW = (w - pad * 2) / 3.0;
            int pillX = (int) Math.round(pad + animatedIndex * cellW);
            int pillW = (int) Math.round(cellW);
            int pillH = h - pad * 2;

            g2.setColor(new Color(0, 0, 0, 12));
            g2.fillRoundRect(pillX, pad + 1, pillW, pillH, 9, 9);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(pillX, pad, pillW, pillH, 9, 9);
            g2.setColor(new Color(0xDC, 0xE3, 0xF0));
            g2.drawRoundRect(pillX, pad, pillW - 1, pillH - 1, 9, 9);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** 내용 패널을 가로 또는 세로 방향 애니메이션으로 펼치고 접는 컨테이너. */
    public static class SmoothCollapsiblePanel extends JPanel {
        public enum Direction { HORIZONTAL, VERTICAL }

        private final JComponent content;
        private final Direction direction;
        private final int targetSize;
        private double progress = 1.0;
        private boolean expanded = true;
        private javax.swing.Timer timer;

        public SmoothCollapsiblePanel(JComponent content, Direction direction, int targetSize, boolean initiallyExpanded) {
            super(new BorderLayout());
            this.content = content;
            this.direction = direction;
            this.targetSize = targetSize;
            this.expanded = initiallyExpanded;
            this.progress = initiallyExpanded ? 1.0 : 0.0;
            setOpaque(false);
            if (initiallyExpanded) {
                add(content, BorderLayout.CENTER);
            }
        }

        public boolean isExpanded() {
            return expanded;
        }

        public void toggle() {
            setExpanded(!expanded, true);
        }

        public void setExpanded(boolean expand, boolean animate) {
            if (this.expanded == expand && (timer == null || !timer.isRunning())) return;
            this.expanded = expand;
            if (!animate) {
                this.progress = expand ? 1.0 : 0.0;
                removeAll();
                if (expand) add(content, BorderLayout.CENTER);
                revalidate();
                repaint();
                return;
            }

            if (expand && getComponentCount() == 0) {
                add(content, BorderLayout.CENTER);
            }

            if (timer != null && timer.isRunning()) timer.stop();
            final double startP = this.progress;
            final double endP = expand ? 1.0 : 0.0;
            final long startTime = System.currentTimeMillis();
            final int durationMs = 260;

            timer = new javax.swing.Timer(12, e -> {
                double t = Math.min(1.0, (System.currentTimeMillis() - startTime) / (double) durationMs);
                double eased = 1.0 - Math.pow(1.0 - t, 3);
                progress = startP + (endP - startP) * eased;
                revalidate();
                if (getParent() != null) {
                    getParent().revalidate();
                    getParent().repaint();
                }
                if (t >= 1.0) {
                    progress = endP;
                    if (!expanded) {
                        removeAll();
                    }
                    revalidate();
                    ((javax.swing.Timer) e.getSource()).stop();
                }
            });
            timer.start();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = content.getPreferredSize();
            if (direction == Direction.HORIZONTAL) {
                int w = (int) Math.round(targetSize * progress);
                return new Dimension(w, d.height);
            } else {
                int h = (int) Math.round(targetSize * progress);
                return new Dimension(d.width, h);
            }
        }
    }

    /** 일정을 24시간 원형 시계 위의 호로 그리고, 마우스 드래그나 휠 회전으로 다른 시간대를 미리 보여 주는 대시보드 패널. */
    public static class CircularCanvasPanel extends JPanel {
        private final ScheduleController controller;
        private final SystemTimer systemTimer;
        private LocalDate currentDate = LocalDate.now();

        private double userRotationDeg = 0.0;
        private double targetRotationDeg = 0.0;
        private javax.swing.Timer smoothRotateTimer;

        private Point lastDragPoint = null;
        private double startDragAngle = 0.0;
        private CircularScheduleSegment hoveredSegment = null;
        private String highlightedScheduleId = null;
        private List<CircularScheduleSegment> currentSegments = new ArrayList<>();

        private final Runnable onUserRotateCallback;
        private final java.util.function.Consumer<ScheduleDTO> onScheduleClickCallback;

        public CircularCanvasPanel(ScheduleController controller, SystemTimer systemTimer,
                                   Runnable onUserRotateCallback,
                                   java.util.function.Consumer<ScheduleDTO> onScheduleClickCallback) {
            this.controller = controller;
            this.systemTimer = systemTimer;
            this.onUserRotateCallback = onUserRotateCallback;
            this.onScheduleClickCallback = onScheduleClickCallback;
            setOpaque(false);
            setPreferredSize(new Dimension(540, 440));
            initMouseInteraction();
        }

        public void setCurrentDate(LocalDate date) {
            this.currentDate = date;
            repaint();
        }

        public LocalDate getCurrentDate() {
            return currentDate;
        }

        public double getUserRotationDeg() {
            return userRotationDeg;
        }

        public void setHighlightedScheduleId(String scheduleId) {
            this.highlightedScheduleId = scheduleId;
            repaint();
        }

        /**
         * 자동 추적을 멈추고 지정한 일정이 위쪽에 오도록 회전한다.
         *
         * @param target 보여 줄 일정
         */
        public void smoothRotateToSchedule(ScheduleDTO target) {
            if (target == null || target.startDateTime == null) return;
            systemTimer.pauseAutoRefresh();
            if (onUserRotateCallback != null) onUserRotateCallback.run();

            int targetMin = target.startDateTime.getHour() * 60 + target.startDateTime.getMinute()
                    + (int) Math.min(30, ChronoUnit.MINUTES.between(target.startDateTime, target.endDateTime) / 2);
            double desiredDeg = ((targetMin - 720.0) / 1440.0) * 360.0;
            animateRotationTo(desiredDeg, null);
        }

        /** 회전 각도를 0으로 되돌려 현재 시각 기준 화면으로 복귀한다. */
        public void animateResetToCurrentTime() {
            highlightedScheduleId = null;
            animateRotationTo(0.0, this::repaint);
        }

        private void animateRotationTo(double destDeg, Runnable onComplete) {
            if (smoothRotateTimer != null && smoothRotateTimer.isRunning()) {
                smoothRotateTimer.stop();
            }
            final double startDeg = this.userRotationDeg;
            final double delta = destDeg - startDeg;
            final long startTime = System.currentTimeMillis();
            final int durationMs = 380;

            smoothRotateTimer = new javax.swing.Timer(12, e -> {
                double t = Math.min(1.0, (System.currentTimeMillis() - startTime) / (double) durationMs);
                double eased = 1.0 - Math.pow(1.0 - t, 3);
                userRotationDeg = startDeg + delta * eased;
                targetRotationDeg = userRotationDeg;
                if (onUserRotateCallback != null) onUserRotateCallback.run();
                repaint();
                if (t >= 1.0) {
                    userRotationDeg = destDeg;
                    targetRotationDeg = destDeg;
                    ((javax.swing.Timer) e.getSource()).stop();
                    if (onComplete != null) onComplete.run();
                }
            });
            smoothRotateTimer.start();
        }

        /** @return 현재 회전 상태에서 위쪽(12시 방향)에 해당하는 시각 */
        public LocalTime getPreviewedTimeAtTop() {
            double shiftedMin = 720.0 + (userRotationDeg / 360.0) * 1440.0;
            int normMin = ((int) Math.round(shiftedMin) % 1440 + 1440) % 1440;
            return LocalTime.of(normMin / 60, normMin % 60);
        }

        /** @return 회전하지 않았으면 진행 중(없으면 다음) 일정, 회전 중이면 위쪽 시각에 가장 가까운 일정 */
        public ScheduleDTO getActiveOrPreviewSchedule() {
            List<ScheduleDTO> list = controller.getRepository().findByDate(currentDate);
            if (list.isEmpty()) return null;

            if (Math.abs(userRotationDeg) < 1.5 && !systemTimer.isAutoRefreshPaused()) {
                LocalDateTime now = LocalDateTime.of(currentDate, systemTimer.now().toLocalTime());
                for (ScheduleDTO s : list) {
                    if (s.isOngoingAt(now)) return s;
                }
                for (ScheduleDTO s : list) {
                    if (s.startDateTime.isAfter(now)) return s;
                }
                return list.get(list.size() - 1);
            } else {
                LocalTime previewTime = getPreviewedTimeAtTop();
                LocalDateTime pdt = LocalDateTime.of(currentDate, previewTime);
                ScheduleDTO best = null;
                long bestDist = Long.MAX_VALUE;
                for (ScheduleDTO s : list) {
                    if (s.isOngoingAt(pdt)) return s;
                    long dist = Math.abs(ChronoUnit.MINUTES.between(pdt, s.startDateTime));
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = s;
                    }
                }
                return best;
            }
        }

        /**
         * @param primary 기준 일정
         * @return 기준 일정 바로 다음에 시작하는 일정, 없으면 null
         */
        public ScheduleDTO getImmediateNextSchedule(ScheduleDTO primary) {
            if (primary == null) return null;
            List<ScheduleDTO> list = controller.getRepository().findByDate(currentDate);
            // 현재 일정이 끝난 뒤 시작하는 일정을 우선
            for (ScheduleDTO s : list) {
                if (!Objects.equals(s.id, primary.id) && !s.startDateTime.isBefore(primary.endDateTime)) {
                    return s;
                }
            }
            // 없으면 시간이 겹치더라도 더 늦게 시작하는 일정
            for (ScheduleDTO s : list) {
                if (!Objects.equals(s.id, primary.id) && s.startDateTime.isAfter(primary.startDateTime)) {
                    return s;
                }
            }
            return null;
        }

        private void initMouseInteraction() {
            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Point p = e.getPoint();
                    lastDragPoint = p;
                    startDragAngle = getAngleFromCenter(p);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    Point p = e.getPoint();
                    for (CircularScheduleSegment seg : currentSegments) {
                        if ((seg.renderedArcShape != null && seg.renderedArcShape.contains(p))
                                || (seg.calloutCardBounds != null && seg.calloutCardBounds.contains(p))) {
                            if (onScheduleClickCallback != null) {
                                onScheduleClickCallback.accept(seg.schedule);
                            }
                            return;
                        }
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (lastDragPoint == null) return;
                    double currentAngle = getAngleFromCenter(e.getPoint());
                    double delta = currentAngle - startDragAngle;
                    if (delta > 180) delta -= 360;
                    if (delta < -180) delta += 360;

                    if (Math.abs(delta) > 0.4) {
                        systemTimer.pauseAutoRefresh();
                        userRotationDeg += delta;
                        targetRotationDeg = userRotationDeg;
                        startDragAngle = currentAngle;
                        if (onUserRotateCallback != null) {
                            onUserRotateCallback.run();
                        }
                        repaint();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    Point p = e.getPoint();
                    CircularScheduleSegment found = null;
                    for (CircularScheduleSegment seg : currentSegments) {
                        if ((seg.renderedArcShape != null && seg.renderedArcShape.contains(p))
                                || (seg.calloutCardBounds != null && seg.calloutCardBounds.contains(p))) {
                            found = seg;
                            break;
                        }
                    }
                    if (found != hoveredSegment) {
                        hoveredSegment = found;
                        setCursor(found != null
                                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                                : Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        if (found != null) {
                            setToolTipText(String.format("<html><b>%s</b><br/>시간: %s<br/>장소: %s<br/><i>(클릭하여 상세 정보 및 수정/삭제)</i></html>",
                                    found.schedule.title, found.schedule.getTimeRangeText(),
                                    found.schedule.location.isEmpty() ? "미지정" : found.schedule.location));
                        } else {
                            setToolTipText("마우스 드래그 또는 휠 스크롤로 24시간 타임라인을 회전하여 다음 일정을 프리뷰할 수 있습니다.");
                        }
                        repaint();
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    systemTimer.pauseAutoRefresh();
                    targetRotationDeg += e.getPreciseWheelRotation() * 15.0;
                    userRotationDeg = targetRotationDeg;
                    if (onUserRotateCallback != null) {
                        onUserRotateCallback.run();
                    }
                    repaint();
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
            addMouseWheelListener(adapter);
        }

        private double getAngleFromCenter(Point p) {
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            return Math.toDegrees(Math.atan2(p.y - cy, p.x - cx));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth();
            int h = getHeight();
            double cx = w / 2.0;
            double cy = h / 2.0 + 4.0;
            double outerTickRadius = Math.min(w, h) * 0.39;
            double ringRadius = outerTickRadius - 14.0;
            double innerCircleRadius = ringRadius - 14.0;

            LocalTime nowTime = systemTimer.now().toLocalTime();
            currentSegments = controller.calculateCircularSegments(currentDate, nowTime);

            // 15분 간격 눈금 (정각/6시간 단위는 더 길게)
            for (int i = 0; i < 96; i++) {
                double deg = 90.0 - (i / 96.0) * 360.0 + userRotationDeg;
                double rad = Math.toRadians(deg);
                boolean isMajorQuad = (i % 24 == 0);
                boolean isHour = (i % 4 == 0);

                double r1 = isMajorQuad ? outerTickRadius - 7 : (isHour ? outerTickRadius - 5 : outerTickRadius - 3);
                double r2 = isMajorQuad ? outerTickRadius + 3 : outerTickRadius;

                double x1 = cx + r1 * Math.cos(rad);
                double y1 = cy - r1 * Math.sin(rad);
                double x2 = cx + r2 * Math.cos(rad);
                double y2 = cy - r2 * Math.sin(rad);

                g2.setColor(isMajorQuad ? new Color(0x94, 0xA3, 0xB8) : new Color(0xCB, 0xD5, 0xE1));
                g2.setStroke(new BasicStroke(isMajorQuad ? 1.8f : (isHour ? 1.3f : 1.0f)));
                g2.draw(new Line2D.Double(x1, y1, x2, y2));
            }

            drawCardinalLabel(g2, cx, cy - outerTickRadius - 26, "12", "정오");
            drawCardinalLabel(g2, cx + outerTickRadius + 34, cy, "18", "저녁");
            drawCardinalLabel(g2, cx, cy + outerTickRadius + 28, "24", "자정");
            drawCardinalLabel(g2, cx - outerTickRadius - 34, cy, "06", "아침");

            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(cx - ringRadius, cy - ringRadius, ringRadius * 2, ringRadius * 2));

            g2.setColor(new Color(0xF1, 0xF5, 0xF9));
            g2.setStroke(new BasicStroke(11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Ellipse2D.Double(cx - ringRadius, cy - ringRadius, ringRadius * 2, ringRadius * 2));

            g2.setColor(new Color(0xE2, 0xE8, 0xF0));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Ellipse2D.Double(cx - innerCircleRadius, cy - innerCircleRadius, innerCircleRadius * 2, innerCircleRadius * 2));

            ScheduleDTO activeOrPreview = getActiveOrPreviewSchedule();
            ScheduleDTO nextAfterActive = getImmediateNextSchedule(activeOrPreview);

            CircularScheduleSegment primarySeg = null;
            CircularScheduleSegment secondarySeg = null;
            for (CircularScheduleSegment seg : currentSegments) {
                if (activeOrPreview != null && Objects.equals(seg.schedule.id, activeOrPreview.id)) {
                    primarySeg = seg;
                }
                if (nextAfterActive != null && Objects.equals(seg.schedule.id, nextAfterActive.id)) {
                    secondarySeg = seg;
                }
            }
            if (primarySeg == null && !currentSegments.isEmpty()) {
                primarySeg = currentSegments.get(0);
                ScheduleDTO fallbackNext = getImmediateNextSchedule(primarySeg.schedule);
                for (CircularScheduleSegment seg : currentSegments) {
                    if (fallbackNext != null && Objects.equals(seg.schedule.id, fallbackNext.id)) {
                        secondarySeg = seg;
                        break;
                    }
                }
            }

            Color nextTealColor = new Color(0x0D, 0x94, 0x88);
            Color currentOuterBorderColor = new Color(0x1E, 0x1B, 0x4B);

            for (CircularScheduleSegment seg : currentSegments) {
                double shiftedStartAngle = seg.rawStartAngleDeg + userRotationDeg;
                Arc2D.Double arc = new Arc2D.Double(
                        cx - ringRadius, cy - ringRadius,
                        ringRadius * 2, ringRadius * 2,
                        shiftedStartAngle, seg.sweepAngleDeg, Arc2D.OPEN
                );

                boolean isPrimary = (seg == primarySeg);
                boolean isSecondary = (seg == secondarySeg);
                boolean isHoveredOrHighlighted = Objects.equals(seg.schedule.id, highlightedScheduleId) || seg == hoveredSegment;

                if (isPrimary) {
                    g2.setColor(new Color(PRIMARY_BLUE.getRed(), PRIMARY_BLUE.getGreen(), PRIMARY_BLUE.getBlue(), 55));
                    g2.setStroke(new BasicStroke(21f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(arc);

                    g2.setColor(currentOuterBorderColor);
                    g2.setStroke(new BasicStroke(16.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(arc);

                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(13.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(arc);

                    g2.setColor(PRIMARY_BLUE);
                    g2.setStroke(new BasicStroke(9.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(arc);

                    double startRad = Math.toRadians(shiftedStartAngle);
                    double endRad = Math.toRadians(shiftedStartAngle + seg.sweepAngleDeg);
                    double sx = cx + ringRadius * Math.cos(startRad);
                    double sy = cy - ringRadius * Math.sin(startRad);
                    double ex = cx + ringRadius * Math.cos(endRad);
                    double ey = cy - ringRadius * Math.sin(endRad);

                    g2.setColor(Color.WHITE);
                    g2.fill(new Ellipse2D.Double(sx - 4.2, sy - 4.2, 8.4, 8.4));
                    g2.fill(new Ellipse2D.Double(ex - 4.2, ey - 4.2, 8.4, 8.4));
                    g2.setColor(currentOuterBorderColor);
                    g2.setStroke(new BasicStroke(2.2f));
                    g2.draw(new Ellipse2D.Double(sx - 4.2, sy - 4.2, 8.4, 8.4));
                    g2.draw(new Ellipse2D.Double(ex - 4.2, ey - 4.2, 8.4, 8.4));
                } else if (isSecondary) {
                    g2.setColor(new Color(0x0D, 0x94, 0x88, 42));
                    g2.setStroke(new BasicStroke(16.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(arc);

                    g2.setColor(new Color(0x0F, 0x76, 0x6E));
                    g2.setStroke(new BasicStroke(12.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(arc);

                    g2.setColor(nextTealColor);
                    g2.setStroke(new BasicStroke(9.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(arc);
                } else {
                    if (isHoveredOrHighlighted) {
                        g2.setColor(new Color(PRIMARY_BLUE.getRed(), PRIMARY_BLUE.getGreen(), PRIMARY_BLUE.getBlue(), 40));
                        g2.setStroke(new BasicStroke(16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.draw(arc);
                    }
                    LocalDateTime refNow = LocalDateTime.of(currentDate, nowTime);
                    boolean isPast = seg.schedule.endDateTime.isBefore(refNow);
                    Color otherCol = isHoveredOrHighlighted
                            ? PRIMARY_BLUE
                            : (isPast ? new Color(0xCB, 0xD5, 0xE1) : new Color(0x8D, 0x99, 0xAE));
                    g2.setColor(otherCol);
                    g2.setStroke(new BasicStroke(isHoveredOrHighlighted ? 11.0f : 9.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(arc);
                }

                seg.renderedArcShape = new BasicStroke(22f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(arc);
            }

            double nowMinutes = nowTime.getHour() * 60.0 + nowTime.getMinute();
            double nowAngleDeg = 270.0 - (nowMinutes / 1440.0) * 360.0 + userRotationDeg;
            double nowRad = Math.toRadians(nowAngleDeg);
            double markerX = cx + ringRadius * Math.cos(nowRad);
            double markerY = cy - ringRadius * Math.sin(nowRad);
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(markerX - 5.5, markerY - 5.5, 11, 11));
            g2.setColor( DANGER_RED );
            g2.setStroke(new BasicStroke(2.6f));
            g2.draw(new Ellipse2D.Double(markerX - 5.5, markerY - 5.5, 11, 11));

            drawScheduleLegendBar(g2, cx, h - 14);

            g2.setFont(uiFont(Font.PLAIN, 11));
            g2.setColor(TEXT_MUTED);
            String centerCaption = systemTimer.isAutoRefreshPaused()
                    ? String.format("탐색 기준 %s (드래그/휠 회전)", getPreviewedTimeAtTop().format(DateTimeFormatter.ofPattern("HH:mm")))
                    : "24시간 타임라인 (현재 vs 다음 일정 구분)";
            FontMetrics fmCap = g2.getFontMetrics();
            g2.drawString(centerCaption, (int) (cx - fmCap.stringWidth(centerCaption) / 2.0), (int) (cy + 11));

            if (currentSegments.isEmpty()) {
                drawEmptyStateInsideCircle(g2, cx, cy);
            } else if (primarySeg != null) {
                double card1W = Math.min(242, innerCircleRadius * 1.50);
                double card1H = 86;
                double card1X = cx - card1W / 2.0;
                double card1Y = cy - innerCircleRadius * 0.67;

                double pMidAngle = Math.toRadians(primarySeg.rawStartAngleDeg + userRotationDeg + primarySeg.sweepAngleDeg / 2.0);
                double pArcX = cx + innerCircleRadius * Math.cos(pMidAngle);
                double pArcY = cy - innerCircleRadius * Math.sin(pMidAngle);

                g2.setColor(PRIMARY_BLUE);
                g2.setStroke(new BasicStroke(2.0f));
                g2.draw(new Line2D.Double(cx, card1Y, pArcX, pArcY));
                g2.setColor(Color.WHITE);
                g2.fill(new Ellipse2D.Double(pArcX - 4, pArcY - 4, 8, 8));
                g2.setColor(currentOuterBorderColor);
                g2.setStroke(new BasicStroke(2.0f));
                g2.draw(new Ellipse2D.Double(pArcX - 4, pArcY - 4, 8, 8));

                String badgeText = systemTimer.isAutoRefreshPaused()
                        ? "▣ 회전 프리뷰 일정"
                        : (primarySeg.isOngoing ? "▣ 현재 진행 일정" : "▣ 현재 기준 일정");
                drawInnerScheduleCard(g2, primarySeg, card1X, card1Y, card1W, card1H,
                        PRIMARY_LIGHT_BG, PRIMARY_BLUE, badgeText, true);

                if (secondarySeg != null) {
                    double card2W = Math.min(224, innerCircleRadius * 1.38);
                    double card2H = 74;
                    double card2X = cx - card2W / 2.0;
                    double card2Y = cy + innerCircleRadius * 0.19;

                    double sMidAngle = Math.toRadians(secondarySeg.rawStartAngleDeg + userRotationDeg + secondarySeg.sweepAngleDeg / 2.0);
                    double sArcX = cx + innerCircleRadius * Math.cos(sMidAngle);
                    double sArcY = cy - innerCircleRadius * Math.sin(sMidAngle);

                    double startLineX = (sArcX >= cx) ? (card2X + card2W) : card2X;
                    double startLineY = card2Y + card2H / 2.0;
                    double elbowX = (sArcX >= cx) ? Math.min(sArcX, startLineX + 18) : Math.max(sArcX, startLineX - 18);

                    Stroke prevStroke = g2.getStroke();
                    g2.setColor(nextTealColor);
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            10.0f, new float[]{4.0f, 3.0f}, 0.0f));
                    Path2D.Double pointerPath = new Path2D.Double();
                    pointerPath.moveTo(startLineX, startLineY);
                    pointerPath.lineTo(elbowX, startLineY);
                    pointerPath.lineTo(sArcX, sArcY);
                    g2.draw(pointerPath);
                    g2.setStroke(prevStroke);

                    g2.setColor(nextTealColor);
                    g2.fill(new Ellipse2D.Double(sArcX - 3.5, sArcY - 3.5, 7, 7));

                    long gapMin = ChronoUnit.MINUTES.between(primarySeg.schedule.endDateTime, secondarySeg.schedule.startDateTime);
                    String nextBadge = gapMin > 0
                            ? String.format("⏭ 바로 다음 일정 (종료 %s 후)", MainFrame.formatDurationKor(gapMin))
                            : "⏭ 바로 다음 일정 (직후 이어짐)";

                    drawInnerScheduleCard(g2, secondarySeg, card2X, card2Y, card2W, card2H,
                            new Color(0xF0, 0xFD, 0xFA), nextTealColor, nextBadge, false);
                }
            }

            g2.dispose();
        }

        private void drawScheduleLegendBar(Graphics2D g2, double cx, double y) {
            g2.setFont(uiFont(Font.BOLD, 11));
            String item1 = "▣ 현재 일정(이중 테두리)";
            String item2 = "● 바로 다음 일정";
            String item3 = "● 이후/완료 일정";
            FontMetrics fm = g2.getFontMetrics();
            int gap = 16;
            int totalW = fm.stringWidth(item1) + fm.stringWidth(item2) + fm.stringWidth(item3) + gap * 2;
            int startX = (int) Math.round(cx - totalW / 2.0);

            g2.setColor(PRIMARY_BLUE);
            g2.drawString(item1, startX, (int) y);
            startX += fm.stringWidth(item1) + gap;

            g2.setColor(new Color(0x0D, 0x94, 0x88));
            g2.drawString(item2, startX, (int) y);
            startX += fm.stringWidth(item2) + gap;

            g2.setColor(TEXT_MUTED);
            g2.drawString(item3, startX, (int) y);
        }

        private void drawCardinalLabel(Graphics2D g2, double x, double y, String numStr, String korLabel) {
            g2.setFont(uiFont(Font.BOLD, 16));
            g2.setColor(TEXT_PRIMARY);
            FontMetrics fm1 = g2.getFontMetrics();
            g2.drawString(numStr, (int) (x - fm1.stringWidth(numStr) / 2.0), (int) (y - 2));

            g2.setFont(uiFont(Font.PLAIN, 11));
            g2.setColor(TEXT_MUTED);
            FontMetrics fm2 = g2.getFontMetrics();
            g2.drawString(korLabel, (int) (x - fm2.stringWidth(korLabel) / 2.0), (int) (y + 13));
        }

        private void drawInnerScheduleCard(Graphics2D g2, CircularScheduleSegment seg,
                                           double x, double y, double w, double h,
                                           Color bg, Color border, String statusBadge, boolean isPrimary) {
            RoundRectangle2D.Double rect = new RoundRectangle2D.Double(x, y, w, h, 18, 18);
            seg.calloutCardBounds = rect.getBounds2D();

            boolean hovered = (seg == hoveredSegment) || Objects.equals(seg.schedule.id, highlightedScheduleId);

            if (isPrimary) {
                g2.setColor(new Color(PRIMARY_BLUE.getRed(), PRIMARY_BLUE.getGreen(), PRIMARY_BLUE.getBlue(), 42));
                g2.fill(new RoundRectangle2D.Double(x - 3, y - 3, w + 6, h + 6, 22, 22));

                g2.setColor(hovered ? new Color(0xE0, 0xE7, 0xFF) : bg);
                g2.fill(rect);

                g2.setColor(new Color(0x1E, 0x1B, 0x4B));
                g2.setStroke(new BasicStroke(3.4f));
                g2.draw(new RoundRectangle2D.Double(x - 1.2, y - 1.2, w + 2.4, h + 2.4, 20, 20));

                g2.setColor(PRIMARY_BLUE);
                g2.setStroke(new BasicStroke(2.2f));
                g2.draw(rect);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.0f));
                g2.draw(new RoundRectangle2D.Double(x + 2.2, y + 2.2, w - 4.4, h - 4.4, 14, 14));
            } else {
                g2.setColor(hovered ? new Color(0xCC, 0xFB, 0xF1) : bg);
                g2.fill(rect);
                g2.setColor(border);
                g2.setStroke(new BasicStroke(hovered ? 2.2f : 1.6f));
                g2.draw(rect);
            }

            double curY = y + (isPrimary ? 21 : 19);
            if (statusBadge != null) {
                g2.setFont(uiFont(Font.BOLD, 11));
                FontMetrics fmB = g2.getFontMetrics();
                int bw = fmB.stringWidth(statusBadge) + 16;
                int bh = 18;
                double bx = x + (w - bw) / 2.0;
                double by = curY - 13;

                if (isPrimary) {
                    g2.setColor(PRIMARY_BLUE);
                    g2.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 10, 10));
                    g2.setColor(Color.WHITE);
                } else {
                    g2.setColor(new Color(0xCC, 0xFB, 0xF1));
                    g2.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 10, 10));
                    g2.setColor(new Color(0x0F, 0x76, 0x6E));
                }
                g2.drawString(statusBadge, (int) (x + (w - fmB.stringWidth(statusBadge)) / 2.0), (int) curY);
                curY += (isPrimary ? 21 : 19);
            }

            g2.setFont(uiFont(Font.BOLD, isPrimary ? 13 : 12));
            g2.setColor(isPrimary ? PRIMARY_BLUE : new Color(0x0F, 0x76, 0x6E));
            String timeTxt = seg.schedule.getShortTimeRangeText();
            FontMetrics fmT = g2.getFontMetrics();
            g2.drawString(timeTxt, (int) (x + (w - fmT.stringWidth(timeTxt)) / 2.0), (int) curY);

            curY += (isPrimary ? 22 : 20);
            g2.setFont(uiFont(Font.BOLD, isPrimary ? 15 : 13));
            g2.setColor(TEXT_PRIMARY);
            String title = ellipsize(seg.schedule.title, g2.getFontMetrics(), (int) w - 24);
            FontMetrics fmTitle = g2.getFontMetrics();
            g2.drawString(title, (int) (x + (w - fmTitle.stringWidth(title)) / 2.0), (int) curY);
        }

        private void drawEmptyStateInsideCircle(Graphics2D g2, double cx, double cy) {
            double w = 220, h = 72;
            RoundRectangle2D.Double rect = new RoundRectangle2D.Double(cx - w / 2, cy - h - 10, w, h, 16, 16);
            g2.setColor(BG_SUBTLE);
            g2.fill(rect);
            g2.setColor(BORDER_COLOR);
            g2.draw(rect);

            g2.setFont(uiFont(Font.BOLD, 13));
            g2.setColor(TEXT_SECONDARY);
            String msg1 = "등록된 금일 일정이 없습니다";
            FontMetrics fm1 = g2.getFontMetrics();
            g2.drawString(msg1, (int) (cx - fm1.stringWidth(msg1) / 2.0), (int) (cy - h + 22));

            g2.setFont(uiFont(Font.PLAIN, 11));
            g2.setColor(TEXT_MUTED);
            String msg2 = "우측 하단 버튼으로 새 일정을 추가하세요";
            FontMetrics fm2 = g2.getFontMetrics();
            g2.drawString(msg2, (int) (cx - fm2.stringWidth(msg2) / 2.0), (int) (cy - h + 44));
        }
    }

    private static String ellipsize(String text, FontMetrics fm, int maxWidth) {
        if (text == null) return "";
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int len = text.length();
        while (len > 1 && fm.stringWidth(text.substring(0, len) + ellipsis) > maxWidth) {
            len--;
        }
        return text.substring(0, len) + ellipsis;
    }

    /** 클릭하면 날짜를 선택하고 더블 클릭하면 일간 원형 대시보드로 전환하는 6x7 격자 월간 달력. */
    public static class MonthCalendarPanel extends JPanel {
        private final ScheduleController controller;
        private YearMonth currentYearMonth;
        private LocalDate selectedDate;
        private final JLabel lblMonthTitle;
        private final JPanel gridPanel;
        private final java.util.function.Consumer<LocalDate> onSingleClickDate;
        private final java.util.function.Consumer<LocalDate> onDoubleClickDate;

        public MonthCalendarPanel(ScheduleController controller, LocalDate initialDate,
                                  java.util.function.Consumer<LocalDate> onSingleClickDate,
                                  java.util.function.Consumer<LocalDate> onDoubleClickDate) {
            super(new BorderLayout(0, 14));
            this.controller = controller;
            this.selectedDate = initialDate;
            this.currentYearMonth = YearMonth.from(initialDate);
            this.onSingleClickDate = onSingleClickDate;
            this.onDoubleClickDate = onDoubleClickDate;

            setBackground(Color.WHITE);
            setBorder(new EmptyBorder(18, 28, 22, 28));

            JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
            header.setOpaque(false);

            JButton btnPrevYear = createCircleNavButton("«", "이전 연도");
            JButton btnPrevMonth = createCircleNavButton("‹", "이전 달");
            lblMonthTitle = new JLabel("", SwingConstants.CENTER);
            lblMonthTitle.setFont(uiFont(Font.BOLD, 24));
            lblMonthTitle.setForeground(TEXT_PRIMARY);
            lblMonthTitle.setBorder(new EmptyBorder(0, 14, 0, 14));

            JButton btnNextMonth = createCircleNavButton("›", "다음 달");
            JButton btnNextYear = createCircleNavButton("»", "다음 연도");

            btnPrevYear.addActionListener(e -> changeMonth(currentYearMonth.minusYears(1)));
            btnPrevMonth.addActionListener(e -> changeMonth(currentYearMonth.minusMonths(1)));
            btnNextMonth.addActionListener(e -> changeMonth(currentYearMonth.plusMonths(1)));
            btnNextYear.addActionListener(e -> changeMonth(currentYearMonth.plusYears(1)));

            header.add(btnPrevYear);
            header.add(btnPrevMonth);
            header.add(lblMonthTitle);
            header.add(btnNextMonth);
            header.add(btnNextYear);

            add(header, BorderLayout.NORTH);

            JPanel calendarContainer = new JPanel(new BorderLayout(0, 0));
            calendarContainer.setOpaque(false);

            JPanel dowHeader = new JPanel(new GridLayout(1, 7, 0, 0));
            dowHeader.setBackground(Color.WHITE);
            dowHeader.setBorder(new MatteBorder(0, 0, 2, 0, BORDER_COLOR));
            String[] dows = {"일", "월", "화", "수", "목", "금", "토"};
            for (int i = 0; i < 7; i++) {
                JLabel lbl = new JLabel(dows[i], SwingConstants.CENTER);
                lbl.setFont(uiFont(Font.BOLD, 15));
                lbl.setBorder(new EmptyBorder(10, 0, 10, 0));
                if (i == 0) lbl.setForeground(SUNDAY_RED);
                else if (i == 6) lbl.setForeground(SATURDAY_BLUE);
                else lbl.setForeground(TEXT_PRIMARY);
                dowHeader.add(lbl);
            }
            calendarContainer.add(dowHeader, BorderLayout.NORTH);

            gridPanel = new JPanel(new GridLayout(6, 7, 0, 0));
            gridPanel.setBackground(BORDER_COLOR);
            calendarContainer.add(gridPanel, BorderLayout.CENTER);

            add(calendarContainer, BorderLayout.CENTER);
            rebuildCalendarGrid();
        }

        private JButton createCircleNavButton(String symbol, String tooltip) {
            JButton btn = new JButton(symbol) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getModel().isRollover() ? new Color(0xDC, 0xE3, 0xED) : new Color(0xE8, 0xEC, 0xF2));
                    g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.setColor(new Color(0xD0, 0xD7, 0xE2));
                    g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btn.setPreferredSize(new Dimension(38, 38));
            btn.setMargin(new Insets(0, 0, 0, 0));
            btn.setFont(uiFont(Font.BOLD, 16));
            btn.setForeground(TEXT_PRIMARY);
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setToolTipText(tooltip);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return btn;
        }

        public void setSelectedDate(LocalDate date) {
            this.selectedDate = date;
            this.currentYearMonth = YearMonth.from(date);
            rebuildCalendarGrid();
        }

        private void changeMonth(YearMonth ym) {
            this.currentYearMonth = ym;
            rebuildCalendarGrid();
        }

        public void rebuildCalendarGrid() {
            lblMonthTitle.setText(String.format("%d년 %d월", currentYearMonth.getYear(), currentYearMonth.getMonthValue()));
            gridPanel.removeAll();

            LocalDate firstOfMonth = currentYearMonth.atDay(1);
            int startOffset = firstOfMonth.getDayOfWeek().getValue() % 7;
            LocalDate startDate = firstOfMonth.minusDays(startOffset);

            for (int i = 0; i < 42; i++) {
                LocalDate cellDate = startDate.plusDays(i);
                boolean inMonth = YearMonth.from(cellDate).equals(currentYearMonth);
                List<ScheduleDTO> daySchedules = controller.getRepository().findByDate(cellDate);
                DayCellButton cell = new DayCellButton(cellDate, inMonth, cellDate.equals(selectedDate), daySchedules);
                gridPanel.add(cell);
            }
            gridPanel.revalidate();
            gridPanel.repaint();
        }

        private class DayCellButton extends JPanel {
            private final LocalDate date;
            private final boolean inCurrentMonth;
            private final boolean isHighlightedDay;
            private final List<ScheduleDTO> schedules;
            private boolean hovered = false;

            public DayCellButton(LocalDate date, boolean inCurrentMonth, boolean isHighlightedDay, List<ScheduleDTO> schedules) {
                this.date = date;
                this.inCurrentMonth = inCurrentMonth;
                this.isHighlightedDay = isHighlightedDay;
                this.schedules = schedules;
                setBackground(inCurrentMonth ? Color.WHITE : new Color(0xF8, 0xFA, 0xFC));
                setBorder(new MatteBorder(0, 0, 1, 1, new Color(0xE8, 0xEE, 0xF5)));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText(date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))
                        + " (클릭: 우측 브리핑 열람 / 더블클릭: 24시간 원형 대시보드로 이동)");

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        selectedDate = date;
                        rebuildCalendarGrid();
                        if (e.getClickCount() >= 2) {
                            if (onDoubleClickDate != null) onDoubleClickDate.accept(date);
                        } else {
                            if (onSingleClickDate != null) onSingleClickDate.accept(date);
                        }
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

                int w = getWidth();
                int h = getHeight();

                if (hovered) {
                    g2.setColor(new Color(0xF1, 0xF5, 0xFF));
                    g2.fillRect(0, 0, w, h);
                }

                String dayStr = String.valueOf(date.getDayOfMonth());
                int circleR = 16;
                int cx = w / 2;
                int cy = 21;

                if (isHighlightedDay) {
                    g2.setColor(INDIGO_PURPLE);
                    g2.fillOval(cx - circleR, cy - circleR, circleR * 2, circleR * 2);
                    g2.setFont(uiFont(Font.BOLD, 15));
                    g2.setColor(Color.WHITE);
                } else {
                    g2.setFont(uiFont(Font.PLAIN, 15));
                    if (!inCurrentMonth) {
                        g2.setColor(new Color(0xB0, 0xB8, 0xC4));
                    } else if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                        g2.setColor(SUNDAY_RED);
                    } else if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
                        g2.setColor(SATURDAY_BLUE);
                    } else {
                        g2.setColor(TEXT_PRIMARY);
                    }
                }
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(dayStr, cx - fm.stringWidth(dayStr) / 2, cy + fm.getAscent() / 2 - 2);

                int curY = cy + 20;
                if (isHighlightedDay || !schedules.isEmpty()) {
                    if (isHighlightedDay) {
                        String todayLabel = "오늘";
                        g2.setFont(uiFont(Font.BOLD, 11));
                        FontMetrics fmT = g2.getFontMetrics();
                        int badgeDiameter = 17;
                        int totalW = fmT.stringWidth(todayLabel) + 4 + (schedules.isEmpty() ? 0 : badgeDiameter);
                        int startX = cx - totalW / 2;

                        g2.setColor(new Color(0x47, 0x55, 0x69));
                        g2.drawString(todayLabel, startX, curY + 11);

                        if (!schedules.isEmpty()) {
                            int bx = startX + fmT.stringWidth(todayLabel) + 4;
                            g2.setColor(new Color(0x3B, 0x34, 0x86));
                            g2.fillOval(bx, curY - 2, badgeDiameter, badgeDiameter);
                            g2.setColor(Color.WHITE);
                            g2.setFont(uiFont(Font.BOLD, 10));
                            String cnt = String.valueOf(schedules.size());
                            FontMetrics fmC = g2.getFontMetrics();
                            g2.drawString(cnt, bx + (badgeDiameter - fmC.stringWidth(cnt)) / 2, curY + 10);
                        }
                        curY += 20;
                    }

                    int maxPills = Math.max(0, (h - curY - 3) / 18);
                    for (int i = 0; i < Math.min(schedules.size(), maxPills); i++) {
                        ScheduleDTO s = schedules.get(i);
                        Color pillColor = SCHEDULE_PALETTE[Math.abs(s.colorIndex) % SCHEDULE_PALETTE.length];
                        g2.setColor(new Color(pillColor.getRed(), pillColor.getGreen(), pillColor.getBlue(), 28));
                        g2.fillRoundRect(6, curY, w - 12, 16, 6, 6);
                        g2.setColor(pillColor);
                        g2.fillRoundRect(6, curY, 3, 16, 2, 2);

                        g2.setFont(uiFont(Font.PLAIN, 10));
                        g2.setColor(TEXT_PRIMARY);
                        String pillTxt = s.startDateTime.format(DateTimeFormatter.ofPattern("HH:mm")) + " " + s.title;
                        pillTxt = ellipsize(pillTxt, g2.getFontMetrics(), w - 20);
                        g2.drawString(pillTxt, 12, curY + 12);
                        curY += 18;
                    }
                }

                g2.dispose();
            }
        }
    }

    /** 12개월 미니 달력으로 연간 일정 분포를 보여 주는 연간 보기. */
    public static class YearCalendarPanel extends JPanel {
        private final ScheduleController controller;
        private int currentYear;
        private final JLabel lblYearTitle;
        private final JPanel monthsGrid;
        private final java.util.function.Consumer<LocalDate> onSelectDateToDayView;
        private final java.util.function.Consumer<YearMonth> onSelectMonthView;

        public YearCalendarPanel(ScheduleController controller, int initialYear,
                                 java.util.function.Consumer<LocalDate> onSelectDateToDayView,
                                 java.util.function.Consumer<YearMonth> onSelectMonthView) {
            super(new BorderLayout(0, 12));
            this.controller = controller;
            this.currentYear = initialYear;
            this.onSelectDateToDayView = onSelectDateToDayView;
            this.onSelectMonthView = onSelectMonthView;

            setBackground(BG_MAIN);
            setBorder(new EmptyBorder(16, 24, 20, 24));

            JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 2));
            header.setOpaque(false);
            ModernButton btnPrev = new ModernButton("‹ 이전 해", Color.WHITE, BG_SUBTLE, TEXT_PRIMARY, BORDER_COLOR, 10);
            ModernButton btnNext = new ModernButton("다음 해 ›", Color.WHITE, BG_SUBTLE, TEXT_PRIMARY, BORDER_COLOR, 10);
            lblYearTitle = new JLabel(currentYear + "년 연간 스케줄 오버뷰", SwingConstants.CENTER);
            lblYearTitle.setFont(uiFont(Font.BOLD, 22));
            lblYearTitle.setForeground(TEXT_PRIMARY);

            btnPrev.addActionListener(e -> { currentYear--; rebuildYearGrid(); });
            btnNext.addActionListener(e -> { currentYear++; rebuildYearGrid(); });

            header.add(btnPrev);
            header.add(lblYearTitle);
            header.add(btnNext);
            add(header, BorderLayout.NORTH);

            monthsGrid = new JPanel(new GridLayout(3, 4, 14, 14));
            monthsGrid.setOpaque(false);
            add(monthsGrid, BorderLayout.CENTER);

            rebuildYearGrid();
        }

        public void setYear(int year) {
            this.currentYear = year;
            rebuildYearGrid();
        }

        public void rebuildYearGrid() {
            lblYearTitle.setText(currentYear + "년 연간 스케줄 한눈에 보기");
            monthsGrid.removeAll();

            List<ScheduleDTO> all = controller.getRepository().findAll();
            Map<LocalDate, Integer> countByDate = new HashMap<>();
            for (ScheduleDTO s : all) {
                LocalDate d = s.startDateTime.toLocalDate();
                if (d.getYear() == currentYear) {
                    countByDate.put(d, countByDate.getOrDefault(d, 0) + 1);
                }
            }

            for (int m = 1; m <= 12; m++) {
                final int monthVal = m;
                final YearMonth ym = YearMonth.of(currentYear, monthVal);
                RoundedCardPanel card = new RoundedCardPanel(14, Color.WHITE, BORDER_COLOR);
                card.setLayout(new BorderLayout(0, 6));
                card.setBorder(new EmptyBorder(10, 12, 10, 12));

                JPanel topRow = new JPanel(new BorderLayout());
                topRow.setOpaque(false);
                JLabel lblM = new JLabel(m + "월");
                lblM.setFont(uiFont(Font.BOLD, 15));
                lblM.setForeground(TEXT_PRIMARY);

                int monthTotal = countByDate.entrySet().stream()
                        .filter(e -> e.getKey().getMonthValue() == monthVal)
                        .mapToInt(Map.Entry::getValue).sum();
                JLabel lblCnt = new JLabel(monthTotal > 0 ? ("일정 " + monthTotal + "건 ›") : "일정 없음");
                lblCnt.setFont(uiFont(Font.BOLD, 11));
                lblCnt.setForeground(monthTotal > 0 ? PRIMARY_BLUE : TEXT_MUTED);
                lblCnt.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                lblCnt.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (onSelectMonthView != null) onSelectMonthView.accept(ym);
                    }
                });

                topRow.add(lblM, BorderLayout.WEST);
                topRow.add(lblCnt, BorderLayout.EAST);
                card.add(topRow, BorderLayout.NORTH);

                JPanel miniGrid = new JPanel(new GridLayout(7, 7, 2, 2));
                miniGrid.setOpaque(false);
                String[] dows = {"일", "월", "화", "수", "목", "금", "토"};
                for (int i = 0; i < 7; i++) {
                    JLabel dLbl = new JLabel(dows[i], SwingConstants.CENTER);
                    dLbl.setFont(uiFont(Font.BOLD, 9));
                    dLbl.setForeground(i == 0 ? SUNDAY_RED : (i == 6 ? SATURDAY_BLUE : TEXT_MUTED));
                    miniGrid.add(dLbl);
                }

                LocalDate first = ym.atDay(1);
                int offset = first.getDayOfWeek().getValue() % 7;
                int length = ym.lengthOfMonth();
                for (int cellIdx = 0; cellIdx < 42; cellIdx++) {
                    int dayNum = cellIdx - offset + 1;
                    if (dayNum < 1 || dayNum > length) {
                        miniGrid.add(new JLabel(""));
                    } else {
                        final LocalDate d = ym.atDay(dayNum);
                        final int cnt = countByDate.getOrDefault(d, 0);
                        JLabel dayBtn = new JLabel(String.valueOf(dayNum), SwingConstants.CENTER) {
                            @Override
                            protected void paintComponent(Graphics g) {
                                if (cnt > 0) {
                                    Graphics2D g2 = (Graphics2D) g.create();
                                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                                    g2.setColor(cnt >= 2 ? PRIMARY_BLUE : PRIMARY_LIGHT_BG);
                                    int r = Math.min(getWidth(), getHeight()) - 2;
                                    g2.fillOval((getWidth() - r) / 2, (getHeight() - r) / 2, r, r);
                                    g2.dispose();
                                }
                                super.paintComponent(g);
                            }
                        };
                        dayBtn.setFont(uiFont(cnt > 0 ? Font.BOLD : Font.PLAIN, 10));
                        dayBtn.setForeground(cnt >= 2 ? Color.WHITE : (cnt == 1 ? PRIMARY_BLUE : TEXT_PRIMARY));
                        dayBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        if (cnt > 0) {
                            dayBtn.setToolTipText(d + " : 일정 " + cnt + "건 (클릭 시 이동)");
                        }
                        dayBtn.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                if (onSelectDateToDayView != null) onSelectDateToDayView.accept(d);
                            }
                        });
                        miniGrid.add(dayBtn);
                    }
                }

                card.add(miniGrid, BorderLayout.CENTER);
                monthsGrid.add(card);
            }
            monthsGrid.revalidate();
            monthsGrid.repaint();
        }
    }

    /** 일정 등록/수정 다이얼로그. 자연어 빠른 입력, 임시 저장 복원, 알림 설정, 시간 중복 확인을 포함한다. */
    public static class ScheduleFormDialog extends JDialog {
        private final ScheduleController controller;
        private final ScheduleDTO editingSchedule;
        private final boolean isEditMode;

        private JTextArea promptArea;
        private ModernButton btnParse;
        private JLabel lblParseStatus;
        private JPanel draftRestoredBanner;

        private JTextField titleField;
        private JLabel lblTitleError;
        private JSpinner spinStartDate;
        private JSpinner spnStartTime;
        private JSpinner spinEndDate;
        private JSpinner spinEndTime;
        private JTextField locationField;
        private JTextArea descriptionArea;
        private JCheckBox chkEnableNoti;
        private JComboBox<String> cmbNotiTime;
        private JComboBox<String> cmbRepeatTime;
        private JLabel lblDraftStatus;
        private ModernButton btnCancel;
        private ModernButton btnSubmit;

        private boolean suppressDraftCaching = false;
        private boolean committedOrExplicitlyCleared = false;

        private static final String[] NOTI_TIME_LABELS = {
            "정각 (0분 전)", "5분 전", "10분 전", "15분 전", "30분 전", "1시간 전", "1일 전"
        };
        private static final int[] NOTI_TIME_MINS = {0, 5, 10, 15, 30, 60, 1440};

        private static final String[] REPEAT_LABELS = {
            "반복 없음", "5분마다 반복", "10분마다 반복", "30분마다 반복", "1시간마다 반복", "1일마다 반복"
        };
        private static final int[] REPEAT_MINS = {0, 5, 10, 30, 60, 1440};

        public ScheduleFormDialog(Frame owner, ScheduleController controller,
                                  ScheduleDTO existingToEdit, ParsedScheduleDTO preParsed,
                                  LocalDate defaultDate) {
            super(owner, existingToEdit != null ? "일정 수정" : "일정 등록", true);
            this.controller = controller;
            this.isEditMode = (existingToEdit != null);
            this.editingSchedule = existingToEdit != null ? existingToEdit.copy() : new ScheduleDTO();

            setSize(580, 710);
            setMinimumSize(new Dimension(540, 660));
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout());
            getContentPane().setBackground(Color.WHITE);

            buildUI(defaultDate);

            suppressDraftCaching = true;
            if (isEditMode) {
                populateFromScheduleDTO(editingSchedule);
            } else if (preParsed != null) {
                populateFromParsedDTO(preParsed);
            } else if (controller.getDraftManager().hasDraft()) {
                ScheduleDTO draft = controller.getDraftManager().getDraft();
                populateFromScheduleDTO(draft);
                draftRestoredBanner.setVisible(true);
                lblDraftStatus.setText("● 이전 임시 저장 데이터 복원됨");
            }
            suppressDraftCaching = false;

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (!isEditMode && !committedOrExplicitlyCleared) {
                        cacheCurrentFormAsDraft();
                    }
                }
            });
        }

        private void buildUI(LocalDate defaultDate) {
            JPanel mainContent = new JPanel();
            mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
            mainContent.setBackground(Color.WHITE);
            mainContent.setBorder(new EmptyBorder(18, 24, 16, 24));

            draftRestoredBanner = new RoundedCardPanel(10, WARNING_LIGHT, new Color(0xFC, 0xD3, 0x4D));
            draftRestoredBanner.setLayout(new BorderLayout(8, 0));
            draftRestoredBanner.setBorder(new EmptyBorder(8, 12, 8, 12));
            draftRestoredBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            JLabel lblBanner = new JLabel("💡 작성 중이던 임시 저장 데이터를 자동으로 복구했습니다.");
            lblBanner.setFont(uiFont(Font.BOLD, 12));
            lblBanner.setForeground(new Color(0x92, 0x40, 0x0E));
            ModernButton btnClearDraft = new ModernButton("비우기 (초기화)", Color.WHITE, BG_SUBTLE, TEXT_PRIMARY, BORDER_COLOR, 8);
            btnClearDraft.setFont(uiFont(Font.BOLD, 11));
            btnClearDraft.setPreferredSize(new Dimension(106, 24));
            btnClearDraft.addActionListener(e -> {
                suppressDraftCaching = true;
                controller.getDraftManager().clearDraft();
                resetFormToBlank(defaultDate);
                draftRestoredBanner.setVisible(false);
                lblDraftStatus.setText("임시 저장 캐시 초기화됨");
                suppressDraftCaching = false;
            });
            draftRestoredBanner.add(lblBanner, BorderLayout.WEST);
            draftRestoredBanner.add(btnClearDraft, BorderLayout.EAST);
            draftRestoredBanner.setVisible(false);
            mainContent.add(draftRestoredBanner);
            mainContent.add(Box.createVerticalStrut(8));

            RoundedCardPanel aiSection = new RoundedCardPanel(14, Color.WHITE, BORDER_COLOR);
            aiSection.setLayout(new BorderLayout(0, 10));
            aiSection.setBorder(BorderFactory.createCompoundBorder(
                    new TitledBorder(new LineBorder(BORDER_COLOR, 1, true), " AI 자연어 빠른 등록 ",
                            TitledBorder.LEFT, TitledBorder.TOP, uiFont(Font.BOLD, 13), TEXT_PRIMARY),
                    new EmptyBorder(8, 12, 12, 12)
            ));
            aiSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 175));

            promptArea = new JTextArea(3, 30);
            promptArea.setLineWrap(true);
            promptArea.setWrapStyleWord(true);
            promptArea.setFont(uiFont(Font.PLAIN, 13));
            promptArea.setForeground(TEXT_PRIMARY);
            promptArea.setText("내일 오후 3시 소프트웨어공학 연구실에서 캡스톤 프로젝트 최종 회의");
            promptArea.setBorder(new EmptyBorder(8, 10, 8, 10));

            JScrollPane promptScroll = new JScrollPane(promptArea);
            promptScroll.setBorder(new LineBorder(BORDER_COLOR, 1, true));
            promptScroll.setPreferredSize(new Dimension(480, 68));

            JPanel aiBottomBar = new JPanel(new BorderLayout(0, 4));
            aiBottomBar.setOpaque(false);
            btnParse = new ModernButton("⚡ AI 자동 분석 및 필드 완성",
                    PRIMARY_LIGHT_BG, new Color(0xE0, 0xE7, 0xFF), INDIGO_PURPLE, PRIMARY_BORDER, 10);
            btnParse.setPreferredSize(new Dimension(480, 38));
            btnParse.setFont(uiFont(Font.BOLD, 13));
            btnParse.addActionListener(e -> runAsyncAIParsing());

            lblParseStatus = new JLabel("자연어 문장을 입력하고 버튼을 누르면 아래 폼 필드가 자동으로 채워집니다.");
            lblParseStatus.setFont(uiFont(Font.PLAIN, 11));
            lblParseStatus.setForeground(TEXT_MUTED);

            aiBottomBar.add(btnParse, BorderLayout.CENTER);
            aiBottomBar.add(lblParseStatus, BorderLayout.SOUTH);

            aiSection.add(promptScroll, BorderLayout.CENTER);
            aiSection.add(aiBottomBar, BorderLayout.SOUTH);
            mainContent.add(aiSection);
            mainContent.add(Box.createVerticalStrut(16));

            JPanel formGrid = new JPanel(new GridBagLayout());
            formGrid.setOpaque(false);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 4, 6, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.22;
            formGrid.add(createFieldLabel("일정 제목 *"), gbc);

            JPanel titleWrap = new JPanel(new BorderLayout(0, 2));
            titleWrap.setOpaque(false);
            titleField = createStyledTextField("");
            lblTitleError = new JLabel("필수 입력 항목입니다: 일정 제목을 입력해 주세요.");
            lblTitleError.setFont(uiFont(Font.BOLD, 11));
            lblTitleError.setForeground(DANGER_RED);
            lblTitleError.setVisible(false);
            titleWrap.add(titleField, BorderLayout.CENTER);
            titleWrap.add(lblTitleError, BorderLayout.SOUTH);

            gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.78;
            formGrid.add(titleWrap, gbc);

            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.22;
            formGrid.add(createFieldLabel("시작 일시 *"), gbc);

            Date initStart = toDate(LocalDateTime.of(defaultDate, LocalTime.of(15, 0)));
            spinStartDate = createDateSpinner(initStart, "yyyy. MM. dd.");
            spnStartTime = createDateSpinner(initStart, "HH:mm");
            JPanel startRow = new JPanel(new GridLayout(1, 2, 10, 0));
            startRow.setOpaque(false);
            startRow.add(spinStartDate);
            startRow.add(spnStartTime);

            gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.78;
            formGrid.add(startRow, gbc);

            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.22;
            formGrid.add(createFieldLabel("종료 일시 *"), gbc);

            Date initEnd = toDate(LocalDateTime.of(defaultDate, LocalTime.of(16, 0)));
            spinEndDate = createDateSpinner(initEnd, "yyyy. MM. dd.");
            spinEndTime = createDateSpinner(initEnd, "HH:mm");
            JPanel endRow = new JPanel(new GridLayout(1, 2, 10, 0));
            endRow.setOpaque(false);
            endRow.add(spinEndDate);
            endRow.add(spinEndTime);

            gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.78;
            formGrid.add(endRow, gbc);

            gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.22;
            formGrid.add(createFieldLabel("장소"), gbc);

            locationField = createStyledTextField("");
            gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 0.78;
            formGrid.add(locationField, gbc);

            gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.22;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            formGrid.add(createFieldLabel("상세 내용"), gbc);

            descriptionArea = new JTextArea(3, 25);
            descriptionArea.setLineWrap(true);
            descriptionArea.setWrapStyleWord(true);
            descriptionArea.setFont(uiFont(Font.PLAIN, 13));
            descriptionArea.setBorder(new EmptyBorder(8, 10, 8, 10));
            JScrollPane descScroll = new JScrollPane(descriptionArea);
            descScroll.setBorder(new LineBorder(BORDER_COLOR, 1, true));
            descScroll.setPreferredSize(new Dimension(360, 74));

            gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 0.78;
            formGrid.add(descScroll, gbc);

            mainContent.add(formGrid);
            mainContent.add(Box.createVerticalStrut(12));

            JSeparator sep = new JSeparator();
            sep.setForeground(BORDER_COLOR);
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
            mainContent.add(sep);
            mainContent.add(Box.createVerticalStrut(10));

            JPanel notiPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
            notiPanel.setOpaque(false);
            notiPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

            chkEnableNoti = new JCheckBox("알림 설정", true);
            chkEnableNoti.setOpaque(false);
            chkEnableNoti.setFont(uiFont(Font.BOLD, 13));
            chkEnableNoti.setForeground(TEXT_PRIMARY);

            cmbNotiTime = new JComboBox<>(NOTI_TIME_LABELS);
            cmbNotiTime.setSelectedIndex(4);
            cmbNotiTime.setFont(uiFont(Font.PLAIN, 13));
            cmbNotiTime.setPreferredSize(new Dimension(135, 34));

            cmbRepeatTime = new JComboBox<>(REPEAT_LABELS);
            cmbRepeatTime.setSelectedIndex(0);
            cmbRepeatTime.setFont(uiFont(Font.PLAIN, 13));
            cmbRepeatTime.setPreferredSize(new Dimension(135, 34));

            chkEnableNoti.addActionListener(e -> {
                boolean en = chkEnableNoti.isSelected();
                cmbNotiTime.setEnabled(en);
                cmbRepeatTime.setEnabled(en);
                cacheCurrentFormAsDraft();
            });

            notiPanel.add(chkEnableNoti);
            notiPanel.add(cmbNotiTime);
            notiPanel.add(cmbRepeatTime);
            mainContent.add(notiPanel);

            add(mainContent, BorderLayout.CENTER);

            JPanel footer = new JPanel(new BorderLayout());
            footer.setBackground(new Color(0xF8, 0xFA, 0xFC));
            footer.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(1, 0, 0, 0, BORDER_COLOR),
                    new EmptyBorder(12, 24, 12, 24)
            ));

            lblDraftStatus = new JLabel(isEditMode ? "기존 일정을 수정합니다." : "● 입력 내용 자동 임시 저장 활성");
            lblDraftStatus.setFont(uiFont(Font.PLAIN, 12));
            lblDraftStatus.setForeground(TEXT_MUTED);
            footer.add(lblDraftStatus, BorderLayout.WEST);

            JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            btnRow.setOpaque(false);

            if (isEditMode) {
                ModernButton btnDelete = new ModernButton("삭제", DANGER_LIGHT, new Color(0xFE, 0xCA, 0xCA), DANGER_RED, new Color(0xFC, 0xA5, 0xA5), 8);
                btnDelete.setPreferredSize(new Dimension(80, 38));
                btnDelete.addActionListener(e -> handleDelete());
                btnRow.add(btnDelete);
            }

            btnCancel = new ModernButton("취소", Color.WHITE, BG_SUBTLE, TEXT_PRIMARY, BORDER_COLOR, 8);
            btnCancel.setPreferredSize(new Dimension(92, 38));
            btnCancel.addActionListener(e -> {
                if (!isEditMode && !committedOrExplicitlyCleared) {
                    cacheCurrentFormAsDraft();
                }
                dispose();
            });

            btnSubmit = new ModernButton("저장", DARK_NAVY_BTN, new Color(0x1E, 0x22, 0x29), Color.WHITE, null, 8);
            btnSubmit.setPreferredSize(new Dimension(98, 38));
            btnSubmit.addActionListener(e -> handleCommitSave());

            btnRow.add(btnCancel);
            btnRow.add(btnSubmit);
            footer.add(btnRow, BorderLayout.EAST);

            add(footer, BorderLayout.SOUTH);

            DocumentListener docListener = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { onFieldEdited(); }
                @Override public void removeUpdate(DocumentEvent e) { onFieldEdited(); }
                @Override public void changedUpdate(DocumentEvent e) { onFieldEdited(); }
            };
            titleField.getDocument().addDocumentListener(docListener);
            locationField.getDocument().addDocumentListener(docListener);
            descriptionArea.getDocument().addDocumentListener(docListener);
        }

        private void onFieldEdited() {
            if (titleField.getText() != null && !titleField.getText().trim().isEmpty()) {
                lblTitleError.setVisible(false);
                titleField.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(BORDER_COLOR, 1, true),
                        new EmptyBorder(7, 10, 7, 10)
                ));
            }
            if (!isEditMode && !suppressDraftCaching) {
                cacheCurrentFormAsDraft();
            }
        }

        private void cacheCurrentFormAsDraft() {
            ScheduleDTO dto = buildScheduleDTOFromForm();
            controller.saveOrUpdateSchedule(dto, true);
            if (controller.getDraftManager().hasDraft()) {
                lblDraftStatus.setText("● 임시 저장됨 (" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + ")");
                lblDraftStatus.setForeground(PRIMARY_BLUE);
            }
        }

        private void runAsyncAIParsing() {
            final String raw = promptArea.getText();
            if (raw == null || raw.trim().isEmpty()) {
                lblParseStatus.setText("⚠ 분석할 자연어 문장을 먼저 입력해 주세요.");
                lblParseStatus.setForeground(DANGER_RED);
                return;
            }
            btnParse.setEnabled(false);
            btnParse.setText("⏳ AI가 일정 문장을 분석 중입니다...");

            SwingWorker<ParsedScheduleDTO, Void> worker = new SwingWorker<>() {
                @Override
                protected ParsedScheduleDTO doInBackground() {
                    return controller.parseNaturalLanguagePrompt(raw);
                }

                @Override
                protected void done() {
                    btnParse.setEnabled(true);
                    btnParse.setText("⚡ AI 자동 분석 및 필드 완성");
                    try {
                        ParsedScheduleDTO parsed = get();
                        populateFromParsedDTO(parsed);
                        lblParseStatus.setText("✓ " + parsed.parseConfidenceNote + " — 내용을 확인 후 [저장]을 누르세요.");
                        lblParseStatus.setForeground(SUCCESS_GREEN);
                        cacheCurrentFormAsDraft();
                    } catch (Exception ex) {
                        lblParseStatus.setText("⚠ 분석 중 오류가 발생했습니다. 수동으로 입력해 주세요.");
                        lblParseStatus.setForeground(DANGER_RED);
                    }
                }
            };
            worker.execute();
        }

        /** @param parsed 자연어 분석 결과. 폼의 각 필드를 채운다. */
        public void populateFromParsedDTO(ParsedScheduleDTO parsed) {
            if (parsed == null) return;
            if (parsed.rawPrompt != null && !parsed.rawPrompt.isEmpty()) {
                promptArea.setText(parsed.rawPrompt);
            }
            if (parsed.title != null) titleField.setText(parsed.title);
            if (parsed.location != null) locationField.setText(parsed.location);
            if (parsed.description != null) descriptionArea.setText(parsed.description);
            if (parsed.startDateTime != null) {
                Date sd = toDate(parsed.startDateTime);
                spinStartDate.setValue(sd);
                spnStartTime.setValue(sd);
            }
            if (parsed.endDateTime != null) {
                Date ed = toDate(parsed.endDateTime);
                spinEndDate.setValue(ed);
                spinEndTime.setValue(ed);
            }
        }

        private void populateFromScheduleDTO(ScheduleDTO dto) {
            if (dto == null) return;
            titleField.setText(dto.title != null ? dto.title : "");
            locationField.setText(dto.location != null ? dto.location : "");
            descriptionArea.setText(dto.description != null ? dto.description : "");
            if (dto.startDateTime != null) {
                Date sd = toDate(dto.startDateTime);
                spinStartDate.setValue(sd);
                spnStartTime.setValue(sd);
            }
            if (dto.endDateTime != null) {
                Date ed = toDate(dto.endDateTime);
                spinEndDate.setValue(ed);
                spinEndTime.setValue(ed);
            }
            chkEnableNoti.setSelected(dto.notificationEnabled);
            cmbNotiTime.setEnabled(dto.notificationEnabled);
            cmbRepeatTime.setEnabled(dto.notificationEnabled);

            for (int i = 0; i < NOTI_TIME_MINS.length; i++) {
                if (NOTI_TIME_MINS[i] == dto.notificationLeadMinutes) {
                    cmbNotiTime.setSelectedIndex(i);
                    break;
                }
            }
            for (int i = 0; i < REPEAT_MINS.length; i++) {
                if (REPEAT_MINS[i] == dto.repeatIntervalMinutes) {
                    cmbRepeatTime.setSelectedIndex(i);
                    break;
                }
            }
        }

        private void resetFormToBlank(LocalDate defaultDate) {
            titleField.setText("");
            locationField.setText("");
            descriptionArea.setText("");
            Date s = toDate(LocalDateTime.of(defaultDate, LocalTime.of(15, 0)));
            Date e = toDate(LocalDateTime.of(defaultDate, LocalTime.of(16, 0)));
            spinStartDate.setValue(s);
            spnStartTime.setValue(s);
            spinEndDate.setValue(e);
            spinEndTime.setValue(e);
            chkEnableNoti.setSelected(true);
            cmbNotiTime.setSelectedIndex(4);
            cmbRepeatTime.setSelectedIndex(0);
        }

        private ScheduleDTO buildScheduleDTOFromForm() {
            LocalDate sDate = toLocalDate((Date) spinStartDate.getValue());
            LocalTime sTime = toLocalTime((Date) spnStartTime.getValue());
            LocalDate eDate = toLocalDate((Date) spinEndDate.getValue());
            LocalTime eTime = toLocalTime((Date) spinEndTime.getValue());

            int notiMin = NOTI_TIME_MINS[Math.max(0, cmbNotiTime.getSelectedIndex())];
            int repMin = REPEAT_MINS[Math.max(0, cmbRepeatTime.getSelectedIndex())];

            return new ScheduleDTO(
                    editingSchedule.id,
                    titleField.getText().trim(),
                    LocalDateTime.of(sDate, sTime),
                    LocalDateTime.of(eDate, eTime),
                    locationField.getText().trim(),
                    descriptionArea.getText().trim(),
                    chkEnableNoti.isSelected(),
                    notiMin,
                    repMin,
                    editingSchedule.colorIndex
            );
        }

        private void handleCommitSave() {
            ScheduleDTO dto = buildScheduleDTOFromForm();

            if (dto.title.isEmpty()) {
                lblTitleError.setVisible(true);
                titleField.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(DANGER_RED, 2, true),
                        new EmptyBorder(6, 9, 6, 9)
                ));
                titleField.requestFocusInWindow();
                return;
            }

            if (!dto.endDateTime.isAfter(dto.startDateTime)) {
                JOptionPane.showMessageDialog(this,
                        "종료 일시는 시작 일시보다 이후여야 합니다.\n시작 및 종료 시간을 다시 확인해 주세요.",
                        "유효하지 않은 시간 설정", JOptionPane.WARNING_MESSAGE);
                return;
            }

            List<ScheduleDTO> overlaps = controller.getRepository().findOverlapping(
                    dto.startDateTime, dto.endDateTime, isEditMode ? dto.id : null);
            if (!overlaps.isEmpty()) {
                ScheduleDTO firstConflict = overlaps.get(0);
                String[] options = {"시간 조정하기", "그대로 중복 등록"};
                int choice = JOptionPane.showOptionDialog(this,
                        String.format("해당 시간대에 이미 등록된 일정이 있습니다:\n• [%s] (%s)\n\n시간을 다시 조정하시겠습니까, 아니면 중복 등록하시겠습니까?",
                                firstConflict.title, firstConflict.getTimeRangeText()),
                        "일정 시간 중복 감지",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null, options, options[0]);
                if (choice == 0 || choice == JOptionPane.CLOSED_OPTION) {
                    return;
                }
            }

            boolean ok = controller.saveOrUpdateSchedule(dto, false);
            if (ok) {
                committedOrExplicitlyCleared = true;
                dispose();
            }
        }

        private void handleDelete() {
            int confirm = JOptionPane.showConfirmDialog(this,
                    String.format("'%s' 일정을 정말 삭제하시겠습니까?\n삭제된 일정과 알림 대기열은 복구할 수 없습니다.", editingSchedule.title),
                    "일정 삭제 확인", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                controller.getRepository().deleteById(editingSchedule.id);
                committedOrExplicitlyCleared = true;
                if (controller.mainFrame != null) {
                    controller.mainFrame.refreshAllViews();
                }
                dispose();
            }
        }

        private JLabel createFieldLabel(String text) {
            JLabel lbl = new JLabel(text);
            lbl.setFont(uiFont(Font.BOLD, 13));
            lbl.setForeground(TEXT_PRIMARY);
            return lbl;
        }

        private JTextField createStyledTextField(String text) {
            JTextField tf = new JTextField(text);
            tf.setFont(uiFont(Font.PLAIN, 13));
            tf.setForeground(TEXT_PRIMARY);
            tf.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(BORDER_COLOR, 1, true),
                    new EmptyBorder(7, 10, 7, 10)
            ));
            return tf;
        }

        private JSpinner createDateSpinner(Date initialDate, String formatPattern) {
            SpinnerDateModel model = new SpinnerDateModel(initialDate, null, null, Calendar.MINUTE);
            JSpinner spinner = new JSpinner(model);
            JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, formatPattern);
            editor.getTextField().setFont(uiFont(Font.PLAIN, 13));
            editor.getTextField().setBorder(new EmptyBorder(6, 8, 6, 8));
            spinner.setEditor(editor);
            spinner.setBorder(new LineBorder(BORDER_COLOR, 1, true));
            return spinner;
        }

        private static Date toDate(LocalDateTime ldt) {
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        }

        private static LocalDate toLocalDate(Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        private static LocalTime toLocalTime(Date d) {
            LocalTime t = d.toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
            return LocalTime.of(t.getHour(), t.getMinute());
        }
    }

    /** 자연어 문장만 입력해 일정을 빠르게 만드는 다이얼로그. */
    public static class PromptInputModal extends JDialog {
        public PromptInputModal(Frame owner, ScheduleController controller, LocalDate targetDate) {
            super(owner, "AI 자연어 프롬프트 일정 등록", true);
            setSize(520, 310);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout());
            getContentPane().setBackground(Color.WHITE);

            JPanel body = new JPanel(new BorderLayout(0, 12));
            body.setBackground(Color.WHITE);
            body.setBorder(new EmptyBorder(20, 24, 16, 24));

            JLabel title = new JLabel("💬 AI 자연어 한 줄 일정 등록");
            title.setFont(uiFont(Font.BOLD, 17));
            title.setForeground(TEXT_PRIMARY);

            JLabel sub = new JLabel("<html>일상적인 문장으로 일정을 입력하면 AI가 날짜·시간·장소·제목을 추출하여<br/>확인용 등록 폼에 자동으로 채워 넣습니다.</html>");
            sub.setFont(uiFont(Font.PLAIN, 12));
            sub.setForeground(TEXT_SECONDARY);

            JPanel topHead = new JPanel(new BorderLayout(0, 6));
            topHead.setOpaque(false);
            topHead.add(title, BorderLayout.NORTH);
            topHead.add(sub, BorderLayout.SOUTH);
            body.add(topHead, BorderLayout.NORTH);

            JTextArea inputArea = new JTextArea("내일 오후 3시 회의실 A에서 팀 프로젝트 최종 회의");
            inputArea.setFont(uiFont(Font.PLAIN, 14));
            inputArea.setLineWrap(true);
            inputArea.setWrapStyleWord(true);
            inputArea.setBorder(new EmptyBorder(10, 12, 10, 12));
            JScrollPane scroll = new JScrollPane(inputArea);
            scroll.setBorder(new LineBorder(PRIMARY_BORDER, 1, true));
            body.add(scroll, BorderLayout.CENTER);

            JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            chips.setOpaque(false);
            String[] examples = {
                "오늘 오후 2시 공대 7호관에서 알고리즘 세미나",
                "모레 오전 10시 반 도서관에서 캡스톤 보고서 작성",
                "10월 22일 저녁 7시 강남역에서 팀 저녁 모임"
            };
            for (String ex : examples) {
                JButton chip = new JButton("예시: " + (ex.length() > 16 ? ex.substring(0, 16) + "…" : ex));
                chip.setFont(uiFont(Font.PLAIN, 11));
                chip.setFocusPainted(false);
                chip.setBackground(BG_SUBTLE);
                chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                chip.addActionListener(e -> inputArea.setText(ex));
                chips.add(chip);
            }
            body.add(chips, BorderLayout.SOUTH);

            add(body, BorderLayout.CENTER);

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
            footer.setBackground(BG_MAIN);
            footer.setBorder(new MatteBorder(1, 0, 0, 0, BORDER_COLOR));

            ModernButton btnCancel = new ModernButton("취소", Color.WHITE, BG_SUBTLE, TEXT_PRIMARY, BORDER_COLOR, 8);
            btnCancel.setPreferredSize(new Dimension(88, 36));
            btnCancel.addActionListener(e -> dispose());

            ModernButton btnAnalyze = new ModernButton("⚡ AI 분석 및 등록 폼 열기", PRIMARY_BLUE, PRIMARY_BLUE_HOVER, Color.WHITE, null, 8);
            btnAnalyze.setPreferredSize(new Dimension(190, 36));
            btnAnalyze.addActionListener(e -> {
                String text = inputArea.getText().trim();
                if (text.isEmpty()) return;
                btnAnalyze.setEnabled(false);
                btnAnalyze.setText("분석 중...");
                SwingWorker<ParsedScheduleDTO, Void> worker = new SwingWorker<>() {
                    @Override
                    protected ParsedScheduleDTO doInBackground() {
                        return controller.parseNaturalLanguagePrompt(text);
                    }

                    @Override
                    protected void done() {
                        try {
                            ParsedScheduleDTO parsed = get();
                            dispose();
                            ScheduleFormDialog form = new ScheduleFormDialog(owner, controller, null, parsed, targetDate);
                            form.setVisible(true);
                        } catch (Exception ignored) {
                            dispose();
                        }
                    }
                };
                worker.execute();
            });

            footer.add(btnCancel);
            footer.add(btnAnalyze);
            add(footer, BorderLayout.SOUTH);
        }
    }

    /** 일정 상세 정보를 보여 주고 수정이나 삭제를 할 수 있는 다이얼로그. */
    public static class ScheduleDetailDialog extends JDialog {
        public ScheduleDetailDialog(Frame owner, ScheduleController controller, ScheduleDTO scheduleIdHolder) {
            super(owner, "일정 상세 정보", true);
            Optional<ScheduleDTO> opt = controller.getRepository().findById(scheduleIdHolder.id);
            if (opt.isEmpty()) {
                JOptionPane.showMessageDialog(owner,
                        "존재하지 않거나 삭제된 일정입니다.",
                        "일정 조회 안내", JOptionPane.WARNING_MESSAGE);
                if (controller.mainFrame != null) controller.mainFrame.refreshAllViews();
                dispose();
                return;
            }
            ScheduleDTO dto = opt.get();

            setSize(460, 380);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout());
            getContentPane().setBackground(Color.WHITE);

            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBackground(Color.WHITE);
            content.setBorder(new EmptyBorder(22, 26, 18, 26));

            JLabel badge = new JLabel(" ● 일정 상세 메타데이터 ");
            badge.setFont(uiFont(Font.BOLD, 12));
            badge.setForeground(PRIMARY_BLUE);
            content.add(badge);
            content.add(Box.createVerticalStrut(6));

            JLabel lblTitle = new JLabel(dto.title);
            lblTitle.setFont(uiFont(Font.BOLD, 20));
            lblTitle.setForeground(TEXT_PRIMARY);
            content.add(lblTitle);
            content.add(Box.createVerticalStrut(16));

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E) HH:mm", Locale.KOREAN);
            content.add(createDetailRow("🕒 시작 일시", dto.startDateTime.format(dtf)));
            content.add(Box.createVerticalStrut(8));
            content.add(createDetailRow("🏁 종료 일시", dto.endDateTime.format(dtf)));
            content.add(Box.createVerticalStrut(8));
            content.add(createDetailRow("📍 장소", dto.location.isEmpty() ? "지정된 장소 없음" : dto.location));
            content.add(Box.createVerticalStrut(8));
            content.add(createDetailRow("🔔 알림 규칙", dto.getNotificationSummary()));
            content.add(Box.createVerticalStrut(12));

            JLabel lblDescHead = new JLabel("📝 상세 설명");
            lblDescHead.setFont(uiFont(Font.BOLD, 12));
            lblDescHead.setForeground(TEXT_SECONDARY);
            content.add(lblDescHead);
            content.add(Box.createVerticalStrut(4));

            JTextArea descBox = new JTextArea(dto.description.isEmpty() ? "(상세 설명이 없습니다.)" : dto.description);
            descBox.setEditable(false);
            descBox.setLineWrap(true);
            descBox.setWrapStyleWord(true);
            descBox.setFont(uiFont(Font.PLAIN, 13));
            descBox.setBackground(BG_MAIN);
            descBox.setBorder(new EmptyBorder(8, 10, 8, 10));
            JScrollPane scroll = new JScrollPane(descBox);
            scroll.setBorder(new LineBorder(BORDER_COLOR, 1, true));
            scroll.setPreferredSize(new Dimension(380, 75));
            content.add(scroll);

            add(content, BorderLayout.CENTER);

            JPanel footer = new JPanel(new BorderLayout());
            footer.setBackground(BG_MAIN);
            footer.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(1, 0, 0, 0, BORDER_COLOR),
                    new EmptyBorder(10, 20, 10, 20)
            ));

            ModernButton btnDel = new ModernButton("삭제", DANGER_LIGHT, new Color(0xFE, 0xCA, 0xCA), DANGER_RED, new Color(0xFC, 0xA5, 0xA5), 8);
            btnDel.setPreferredSize(new Dimension(76, 36));
            btnDel.addActionListener(e -> {
                int ans = JOptionPane.showConfirmDialog(this,
                        String.format("'%s' 일정을 삭제하시겠습니까?", dto.title),
                        "등록 일정 삭제 확인", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (ans == JOptionPane.YES_OPTION) {
                    controller.getRepository().deleteById(dto.id);
                    if (controller.mainFrame != null) controller.mainFrame.refreshAllViews();
                    dispose();
                }
            });
            footer.add(btnDel, BorderLayout.WEST);

            JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            rightBtns.setOpaque(false);
            ModernButton btnClose = new ModernButton("닫기", Color.WHITE, BG_SUBTLE, TEXT_PRIMARY, BORDER_COLOR, 8);
            btnClose.setPreferredSize(new Dimension(76, 36));
            btnClose.addActionListener(e -> dispose());

            ModernButton btnEdit = new ModernButton("수정", PRIMARY_BLUE, PRIMARY_BLUE_HOVER, Color.WHITE, null, 8);
            btnEdit.setPreferredSize(new Dimension(86, 36));
            btnEdit.addActionListener(e -> {
                dispose();
                ScheduleFormDialog editDlg = new ScheduleFormDialog(owner, controller, dto, null, dto.startDateTime.toLocalDate());
                editDlg.setVisible(true);
            });

            rightBtns.add(btnClose);
            rightBtns.add(btnEdit);
            footer.add(rightBtns, BorderLayout.EAST);

            add(footer, BorderLayout.SOUTH);
        }

        private JPanel createDetailRow(String key, String val) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            JLabel k = new JLabel(key);
            k.setFont(uiFont(Font.BOLD, 12));
            k.setForeground(TEXT_SECONDARY);
            k.setPreferredSize(new Dimension(95, 22));
            JLabel v = new JLabel(val);
            v.setFont(uiFont(Font.PLAIN, 13));
            v.setForeground(TEXT_PRIMARY);
            row.add(k, BorderLayout.WEST);
            row.add(v, BorderLayout.CENTER);
            return row;
        }
    }

    /** 상단 검색과 보기 전환, 가운데 일/월/년 화면, 오른쪽 일과 목록, 아래쪽 진행 중 일정 카드로 구성된 메인 창. */
    public static class MainFrame extends JFrame {
        private final ScheduleController controller;
        private final SystemTimer systemTimer;

        private LocalDate selectedDate = LocalDate.of(2026, 10, 15);
        private String currentViewMode = "DAY";

        private JTextField searchField;
        private JPopupMenu searchPopup;
        private AnimatedSegmentedControl viewToggleButtons;
        private JLabel lblLiveClock;

        private SmoothCollapsiblePanel notificationBannerDrawer;
        private JLabel lblBannerNotificationText;
        private ScheduleDTO lastNotifiedSchedule;

        private CardLayout centerCardLayout;
        private JPanel centerCardContainer;
        private CircularCanvasPanel circularCanvasPanel;
        private MonthCalendarPanel monthCalendarPanel;
        private YearCalendarPanel yearCalendarPanel;

        private JLabel lblCurrentDateTitle;
        private JPanel refreshSyncRow;
        private ModernButton btnRefreshSync;
        private JLabel lblRotateStatusText;

        private SmoothCollapsiblePanel rightTimelineDrawer;
        private JPanel timelineListContainer;
        private JLabel lblDrawerSummary;
        private ModernButton btnToggleRightDrawer;

        private JLabel lblBottomCardHeader;
        private JLabel lblBottomScheduleTitle;
        private JLabel lblBottomScheduleLocation;
        private JLabel lblBottomRemainingCaption;
        private JLabel lblBottomRemainingValue;
        private JProgressBar ongoingProgressBar;
        private SmoothCollapsiblePanel bottomExpandableDrawer;
        private JPanel bottomExpandedContent;
        private ModernButton btnToggleBottomDetail;
        private ModernButton btnAIPrompt;
        private ModernButton btnAddSchedule;

        public MainFrame(ScheduleController controller) {
            super("ChronoCircle — 24시간 원형 대시보드 스마트 스케줄러");
            this.controller = controller;
            this.controller.setMainFrame(this);

            this.systemTimer = new SystemTimer(this::onSystemTimerTick);

            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setMinimumSize(new Dimension(1020, 760));
            setSize(1220, 820);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());
            getContentPane().setBackground(BG_MAIN);

            buildTopNavigationAndNotificationBanner();
            buildCenterViewsAndSideDrawer();
            buildBottomStatusAndActionArea();

            refreshAllViews();
            this.systemTimer.setSimulationTarget(LocalDateTime.of(2026, 10, 15, 12, 18, 0));
            this.systemTimer.start();
        }

        public LocalDate getSelectedDate() {
            return selectedDate;
        }

        public CircularCanvasPanel getCircularCanvasPanel() {
            return circularCanvasPanel;
        }

        public MonthCalendarPanel getMonthCalendarPanel() {
            return monthCalendarPanel;
        }

        public YearCalendarPanel getYearCalendarPanel() {
            return yearCalendarPanel;
        }

        public SmoothCollapsiblePanel getRightTimelineDrawer() {
            return rightTimelineDrawer;
        }

        private void buildTopNavigationAndNotificationBanner() {
            JPanel northWrapper = new JPanel(new BorderLayout());
            northWrapper.setBackground(Color.WHITE);

            JPanel topBar = new JPanel(new BorderLayout(16, 0));
            topBar.setBackground(Color.WHITE);
            topBar.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(0, 0, 1, 0, BORDER_COLOR),
                    new EmptyBorder(12, 26, 12, 26)
            ));

            JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
            brandPanel.setOpaque(false);
            JLabel logoIcon = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(PRIMARY_BLUE);
                    g2.setStroke(new BasicStroke(2.4f));
                    g2.drawOval(2, 2, 24, 24);
                    g2.drawLine(14, 7, 14, 14);
                    g2.drawLine(14, 14, 19, 14);
                    g2.fillOval(21, 3, 5, 5);
                    g2.dispose();
                }
            };
            logoIcon.setPreferredSize(new Dimension(28, 28));
            JLabel brandTitle = new JLabel("ChronoCircle");
            brandTitle.setFont(uiFont(Font.BOLD, 20));
            brandTitle.setForeground(TEXT_PRIMARY);
            brandPanel.add(logoIcon);
            brandPanel.add(brandTitle);
            topBar.add(brandPanel, BorderLayout.WEST);

            JPanel centerSearchWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            centerSearchWrap.setOpaque(false);

            RoundedCardPanel searchBox = new RoundedCardPanel(20, BG_MAIN, BORDER_COLOR);
            searchBox.setLayout(new BorderLayout(8, 0));
            searchBox.setBorder(new EmptyBorder(6, 14, 6, 14));
            searchBox.setPreferredSize(new Dimension(370, 38));

            searchField = new JTextField();
            searchField.setOpaque(false);
            searchField.setBorder(null);
            searchField.setFont(uiFont(Font.PLAIN, 13));
            searchField.setForeground(TEXT_PRIMARY);
            searchField.setToolTipText("제목, 장소, 설명 키워드로 일정을 즉시 검색합니다 (Enter 또는 타이핑)");

            JLabel searchHintIcon = new JLabel("🔍");
            searchHintIcon.setFont(uiFont(Font.PLAIN, 13));
            searchHintIcon.setForeground(TEXT_MUTED);
            searchHintIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            searchHintIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    triggerSearchPopup(true);
                }
            });

            searchBox.add(searchField, BorderLayout.CENTER);
            searchBox.add(searchHintIcon, BorderLayout.EAST);

            searchPopup = new JPopupMenu();
            searchPopup.setBackground(Color.WHITE);
            searchPopup.setBorder(new LineBorder(PRIMARY_BORDER, 1, true));
            searchPopup.setFocusable(false);

            searchField.addActionListener(e -> triggerSearchPopup(true));
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { triggerSearchPopup(false); }
                @Override public void removeUpdate(DocumentEvent e) { triggerSearchPopup(false); }
                @Override public void changedUpdate(DocumentEvent e) { triggerSearchPopup(false); }
            });

            centerSearchWrap.add(searchBox);
            topBar.add(centerSearchWrap, BorderLayout.CENTER);

            JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            rightControls.setOpaque(false);

            lblLiveClock = new JLabel("🕒 12:18");
            lblLiveClock.setFont(uiFont(Font.BOLD, 12));
            lblLiveClock.setForeground(PRIMARY_BLUE);
            lblLiveClock.setBorder(new EmptyBorder(0, 4, 0, 4));
            lblLiveClock.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            lblLiveClock.setToolTipText("클릭하면 시뮬레이션 시각(+15분 / 현재 PC 실제 시각 / 데모 시각 12:18)을 전환하거나 알림을 즉시 테스트할 수 있습니다.");
            lblLiveClock.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showTimeAndNotificationControlMenu(lblLiveClock);
                }
            });

            btnToggleRightDrawer = new ModernButton("📋 한눈에 보기 탭 닫기",
                    PRIMARY_LIGHT_BG, new Color(0xE0, 0xE7, 0xFF), PRIMARY_BLUE, PRIMARY_BORDER, 10);
            btnToggleRightDrawer.setFont(uiFont(Font.BOLD, 12));
            btnToggleRightDrawer.setPreferredSize(new Dimension(148, 36));
            btnToggleRightDrawer.addActionListener(e -> {
                rightTimelineDrawer.toggle();
                boolean exp = rightTimelineDrawer.isExpanded();
                btnToggleRightDrawer.setText(exp ? "📋 한눈에 보기 탭 닫기" : "📋 한눈에 보기 탭 열기");
            });

            viewToggleButtons = new AnimatedSegmentedControl(this::switchViewMode);

            rightControls.add(lblLiveClock);
            rightControls.add(btnToggleRightDrawer);
            rightControls.add(viewToggleButtons);
            topBar.add(rightControls, BorderLayout.EAST);

            northWrapper.add(topBar, BorderLayout.NORTH);

            RoundedCardPanel bannerCard = new RoundedCardPanel(0, new Color(0xEE, 0xF2, 0xFF), new Color(0xC7, 0xD2, 0xFE));
            bannerCard.setLayout(new BorderLayout(12, 0));
            bannerCard.setBorder(new EmptyBorder(8, 26, 8, 26));

            lblBannerNotificationText = new JLabel("🔔 [스마트 알림] 예정된 일정이 곧 시작됩니다.");
            lblBannerNotificationText.setFont(uiFont(Font.BOLD, 13));
            lblBannerNotificationText.setForeground(INDIGO_PURPLE);
            lblBannerNotificationText.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            lblBannerNotificationText.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (lastNotifiedSchedule != null) {
                        new ScheduleDetailDialog(MainFrame.this, controller, lastNotifiedSchedule).setVisible(true);
                    }
                }
            });

            JPanel bannerBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            bannerBtns.setOpaque(false);
            ModernButton btnViewNotiDetail = new ModernButton("상세 보기", PRIMARY_BLUE, PRIMARY_BLUE_HOVER, Color.WHITE, null, 8);
            btnViewNotiDetail.setFont(uiFont(Font.BOLD, 11));
            btnViewNotiDetail.setPreferredSize(new Dimension(80, 26));
            btnViewNotiDetail.addActionListener(e -> {
                if (lastNotifiedSchedule != null) {
                    new ScheduleDetailDialog(MainFrame.this, controller, lastNotifiedSchedule).setVisible(true);
                }
            });

            ModernButton btnCloseBanner = new ModernButton("접기 ▲", Color.WHITE, BG_SUBTLE, TEXT_SECONDARY, BORDER_COLOR, 8);
            btnCloseBanner.setFont(uiFont(Font.BOLD, 11));
            btnCloseBanner.setPreferredSize(new Dimension(68, 26));
            btnCloseBanner.addActionListener(e -> notificationBannerDrawer.setExpanded(false, true));

            bannerBtns.add(btnViewNotiDetail);
            bannerBtns.add(btnCloseBanner);

            bannerCard.add(lblBannerNotificationText, BorderLayout.CENTER);
            bannerCard.add(bannerBtns, BorderLayout.EAST);

            notificationBannerDrawer = new SmoothCollapsiblePanel(
                    bannerCard, SmoothCollapsiblePanel.Direction.VERTICAL, 44, false);
            northWrapper.add(notificationBannerDrawer, BorderLayout.SOUTH);

            add(northWrapper, BorderLayout.NORTH);
        }

        private void buildCenterViewsAndSideDrawer() {
            JPanel centerOuter = new JPanel(new BorderLayout(0, 0));
            centerOuter.setBackground(BG_MAIN);

            centerCardLayout = new CardLayout();
            centerCardContainer = new JPanel(centerCardLayout);
            centerCardContainer.setOpaque(false);

            JPanel dayViewPanel = new JPanel(new BorderLayout(0, 4));
            dayViewPanel.setOpaque(false);
            dayViewPanel.setBorder(new EmptyBorder(14, 24, 6, 16));

            JPanel dayNorth = new JPanel();
            dayNorth.setOpaque(false);
            dayNorth.setLayout(new BoxLayout(dayNorth, BoxLayout.Y_AXIS));

            JPanel dateNavRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 2));
            dateNavRow.setOpaque(false);

            ModernButton btnPrevDay = new ModernButton("‹", Color.WHITE, BG_SUBTLE, TEXT_PRIMARY, BORDER_COLOR, 10);
            btnPrevDay.setPreferredSize(new Dimension(36, 36));
            btnPrevDay.setFont(uiFont(Font.BOLD, 16));
            btnPrevDay.addActionListener(e -> changeSelectedDate(selectedDate.minusDays(1)));

            lblCurrentDateTitle = new JLabel("2026년 10월 15일 (목)", SwingConstants.CENTER);
            lblCurrentDateTitle.setFont(uiFont(Font.BOLD, 24));
            lblCurrentDateTitle.setForeground(TEXT_PRIMARY);

            ModernButton btnNextDay = new ModernButton("›", Color.WHITE, BG_SUBTLE, TEXT_PRIMARY, BORDER_COLOR, 10);
            btnNextDay.setPreferredSize(new Dimension(36, 36));
            btnNextDay.setFont(uiFont(Font.BOLD, 16));
            btnNextDay.addActionListener(e -> changeSelectedDate(selectedDate.plusDays(1)));

            ModernButton btnTodayJump = new ModernButton("기준일(10/15)", Color.WHITE, PRIMARY_LIGHT_BG, PRIMARY_BLUE, PRIMARY_BORDER, 10);
            btnTodayJump.setFont(uiFont(Font.BOLD, 11));
            btnTodayJump.setPreferredSize(new Dimension(112, 30));
            btnTodayJump.addActionListener(e -> {
                systemTimer.setSimulationTarget(LocalDateTime.of(2026, 10, 15, 12, 18, 0));
                changeSelectedDate(LocalDate.of(2026, 10, 15));
                controller.resyncDashboardToCurrentTime(circularCanvasPanel, systemTimer);
            });

            dateNavRow.add(btnPrevDay);
            dateNavRow.add(lblCurrentDateTitle);
            dateNavRow.add(btnNextDay);
            dateNavRow.add(btnTodayJump);
            dayNorth.add(dateNavRow);

            refreshSyncRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
            refreshSyncRow.setOpaque(false);
            refreshSyncRow.setPreferredSize(new Dimension(480, 38));

            btnRefreshSync = new ModernButton("↻ 현재 시각 동기화 (Refresh)",
                    Color.WHITE, PRIMARY_LIGHT_BG, PRIMARY_BLUE, PRIMARY_BORDER, 18);
            btnRefreshSync.setFont(uiFont(Font.BOLD, 12));
            btnRefreshSync.setPreferredSize(new Dimension(198, 30));
            btnRefreshSync.addActionListener(e -> controller.resyncDashboardToCurrentTime(circularCanvasPanel, systemTimer));

            lblRotateStatusText = new JLabel("실시간 자동 트래킹 중 (드래그/휠로 미래 일정 프리뷰)");
            lblRotateStatusText.setFont(uiFont(Font.PLAIN, 12));
            lblRotateStatusText.setForeground(TEXT_MUTED);

            refreshSyncRow.add(btnRefreshSync);
            refreshSyncRow.add(lblRotateStatusText);
            btnRefreshSync.setVisible(false);
            dayNorth.add(refreshSyncRow);

            dayViewPanel.add(dayNorth, BorderLayout.NORTH);

            circularCanvasPanel = new CircularCanvasPanel(
                    controller,
                    systemTimer,
                    () -> {
                        updateSyncBannerState(true);
                        updateBottomOngoingCard();
                    },
                    schedule -> new ScheduleDetailDialog(MainFrame.this, controller, schedule).setVisible(true)
            );
            dayViewPanel.add(circularCanvasPanel, BorderLayout.CENTER);

            monthCalendarPanel = new MonthCalendarPanel(
                    controller,
                    selectedDate,
                    clickedDate -> {
                        selectedDate = clickedDate;
                        circularCanvasPanel.setCurrentDate(clickedDate);
                        if (!rightTimelineDrawer.isExpanded()) {
                            rightTimelineDrawer.setExpanded(true, true);
                            btnToggleRightDrawer.setText("📋 한눈에 보기 탭 닫기");
                        }
                        refreshSideTimelineDrawer();
                        updateBottomOngoingCard();
                    },
                    doubleClickedDate -> {
                        selectedDate = doubleClickedDate;
                        viewToggleButtons.selectMode("DAY", true);
                    }
            );

            yearCalendarPanel = new YearCalendarPanel(
                    controller,
                    selectedDate.getYear(),
                    dateToJump -> {
                        selectedDate = dateToJump;
                        viewToggleButtons.selectMode("DAY", true);
                    },
                    ymToJump -> {
                        selectedDate = ymToJump.atDay(1);
                        viewToggleButtons.selectMode("MONTH", true);
                    }
            );

            centerCardContainer.add(dayViewPanel, "DAY");
            centerCardContainer.add(monthCalendarPanel, "MONTH");
            centerCardContainer.add(yearCalendarPanel, "YEAR");

            centerOuter.add(centerCardContainer, BorderLayout.CENTER);

            JPanel drawerCard = new JPanel(new BorderLayout(0, 10));
            drawerCard.setBackground(Color.WHITE);
            drawerCard.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(0, 1, 0, 0, BORDER_COLOR),
                    new EmptyBorder(16, 16, 16, 16)
            ));

            JPanel drawerHead = new JPanel(new BorderLayout(0, 4));
            drawerHead.setOpaque(false);
            JLabel lblDrawerTitle = new JLabel("📌 한눈에 보는 일과 타임라인");
            lblDrawerTitle.setFont(uiFont(Font.BOLD, 15));
            lblDrawerTitle.setForeground(TEXT_PRIMARY);
            lblDrawerSummary = new JLabel("항목에 마우스를 올리면 원형 시계가 회전합니다.");
            lblDrawerSummary.setFont(uiFont(Font.PLAIN, 11));
            lblDrawerSummary.setForeground(TEXT_MUTED);
            drawerHead.add(lblDrawerTitle, BorderLayout.NORTH);
            drawerHead.add(lblDrawerSummary, BorderLayout.SOUTH);
            drawerCard.add(drawerHead, BorderLayout.NORTH);

            timelineListContainer = new JPanel();
            timelineListContainer.setLayout(new BoxLayout(timelineListContainer, BoxLayout.Y_AXIS));
            timelineListContainer.setBackground(Color.WHITE);

            JScrollPane drawerScroll = new JScrollPane(timelineListContainer);
            drawerScroll.setBorder(null);
            drawerScroll.getVerticalScrollBar().setUnitIncrement(14);
            drawerCard.add(drawerScroll, BorderLayout.CENTER);

            rightTimelineDrawer = new SmoothCollapsiblePanel(
                    drawerCard, SmoothCollapsiblePanel.Direction.HORIZONTAL, 320, true);
            centerOuter.add(rightTimelineDrawer, BorderLayout.EAST);

            add(centerOuter, BorderLayout.CENTER);
        }

        private void buildBottomStatusAndActionArea() {
            JPanel southContainer = new JPanel(new BorderLayout(18, 0));
            southContainer.setBackground(BG_MAIN);
            southContainer.setBorder(new EmptyBorder(6, 26, 18, 26));

            RoundedCardPanel currentCard = new RoundedCardPanel(18, Color.WHITE, BORDER_COLOR);
            currentCard.setLayout(new BorderLayout(0, 8));
            currentCard.setBorder(new EmptyBorder(16, 22, 16, 22));

            JPanel mainCardRow = new JPanel(new BorderLayout(16, 0));
            mainCardRow.setOpaque(false);

            JPanel leftInfo = new JPanel();
            leftInfo.setOpaque(false);
            leftInfo.setLayout(new BoxLayout(leftInfo, BoxLayout.Y_AXIS));

            JPanel headLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            headLine.setOpaque(false);
            lblBottomCardHeader = new JLabel("💡 현재 진행 중인 일정");
            lblBottomCardHeader.setFont(uiFont(Font.BOLD, 13));
            lblBottomCardHeader.setForeground(TEXT_PRIMARY);

            btnToggleBottomDetail = new ModernButton("진행률 & 상세 펼치기 ▲",
                    BG_SUBTLE, new Color(0xE2, 0xE8, 0xF0), TEXT_SECONDARY, null, 8);
            btnToggleBottomDetail.setFont(uiFont(Font.BOLD, 11));
            btnToggleBottomDetail.setPreferredSize(new Dimension(150, 22));
            btnToggleBottomDetail.addActionListener(e -> {
                bottomExpandableDrawer.toggle();
                boolean exp = bottomExpandableDrawer.isExpanded();
                btnToggleBottomDetail.setText(exp ? "상세 브리핑 접기 ▼" : "진행률 & 상세 펼치기 ▲");
            });

            headLine.add(lblBottomCardHeader);
            headLine.add(Box.createHorizontalStrut(8));
            headLine.add(btnToggleBottomDetail);
            headLine.setAlignmentX(Component.LEFT_ALIGNMENT);
            leftInfo.add(headLine);
            leftInfo.add(Box.createVerticalStrut(6));

            lblBottomScheduleTitle = new JLabel("제목: 소프트웨어공학 특강 (11:00 ~ 13:00)");
            lblBottomScheduleTitle.setFont(uiFont(Font.BOLD, 15));
            lblBottomScheduleTitle.setForeground(TEXT_PRIMARY);
            lblBottomScheduleTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            leftInfo.add(lblBottomScheduleTitle);
            leftInfo.add(Box.createVerticalStrut(4));

            lblBottomScheduleLocation = new JLabel("📍 장소: 공대 7호관");
            lblBottomScheduleLocation.setFont(uiFont(Font.PLAIN, 13));
            lblBottomScheduleLocation.setForeground(TEXT_SECONDARY);
            lblBottomScheduleLocation.setAlignmentX(Component.LEFT_ALIGNMENT);
            leftInfo.add(lblBottomScheduleLocation);

            mainCardRow.add(leftInfo, BorderLayout.CENTER);

            JPanel rightRemain = new JPanel(new BorderLayout(16, 0));
            rightRemain.setOpaque(false);
            JSeparator vSep = new JSeparator(SwingConstants.VERTICAL);
            vSep.setForeground(BORDER_COLOR);
            vSep.setPreferredSize(new Dimension(2, 54));
            rightRemain.add(vSep, BorderLayout.WEST);

            JPanel remainTextCol = new JPanel();
            remainTextCol.setOpaque(false);
            remainTextCol.setLayout(new BoxLayout(remainTextCol, BoxLayout.Y_AXIS));
            remainTextCol.setBorder(new EmptyBorder(4, 8, 4, 16));

            lblBottomRemainingCaption = new JLabel("남은 시간:");
            lblBottomRemainingCaption.setFont(uiFont(Font.PLAIN, 12));
            lblBottomRemainingCaption.setForeground(TEXT_MUTED);
            lblBottomRemainingValue = new JLabel("42분 남음");
            lblBottomRemainingValue.setFont(uiFont(Font.BOLD, 22));
            lblBottomRemainingValue.setForeground(PRIMARY_BLUE);

            remainTextCol.add(lblBottomRemainingCaption);
            remainTextCol.add(Box.createVerticalStrut(2));
            remainTextCol.add(lblBottomRemainingValue);
            rightRemain.add(remainTextCol, BorderLayout.CENTER);

            mainCardRow.add(rightRemain, BorderLayout.EAST);
            currentCard.add(mainCardRow, BorderLayout.CENTER);

            bottomExpandedContent = new JPanel(new BorderLayout(12, 6));
            bottomExpandedContent.setOpaque(false);
            bottomExpandedContent.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(1, 0, 0, 0, BORDER_COLOR),
                    new EmptyBorder(10, 2, 2, 2)
            ));

            ongoingProgressBar = new JProgressBar(0, 100);
            ongoingProgressBar.setValue(65);
            ongoingProgressBar.setStringPainted(true);
            ongoingProgressBar.setFont(uiFont(Font.BOLD, 11));
            ongoingProgressBar.setForeground(PRIMARY_BLUE);
            ongoingProgressBar.setBackground(PRIMARY_LIGHT_BG);
            ongoingProgressBar.setPreferredSize(new Dimension(260, 20));
            bottomExpandedContent.add(ongoingProgressBar, BorderLayout.CENTER);

            bottomExpandableDrawer = new SmoothCollapsiblePanel(
                    bottomExpandedContent, SmoothCollapsiblePanel.Direction.VERTICAL, 44, false);
            currentCard.add(bottomExpandableDrawer, BorderLayout.SOUTH);

            southContainer.add(currentCard, BorderLayout.CENTER);

            JPanel actionBtnsPanel = new JPanel(new GridLayout(2, 1, 0, 10));
            actionBtnsPanel.setOpaque(false);
            actionBtnsPanel.setPreferredSize(new Dimension(225, 102));

            btnAIPrompt = new ModernButton("💬  AI 프롬프트 등록",
                    PRIMARY_BLUE, PRIMARY_BLUE_HOVER, Color.WHITE, null, 14);
            btnAIPrompt.setFont(uiFont(Font.BOLD, 14));
            btnAIPrompt.addActionListener(e -> {
                PromptInputModal modal = new PromptInputModal(MainFrame.this, controller, selectedDate);
                modal.setVisible(true);
            });

            btnAddSchedule = new ModernButton("＋  일반 일정 등록",
                    Color.WHITE, BG_SUBTLE, TEXT_PRIMARY, BORDER_COLOR, 14);
            btnAddSchedule.setFont(uiFont(Font.BOLD, 14));
            btnAddSchedule.addActionListener(e -> {
                ScheduleFormDialog dlg = new ScheduleFormDialog(MainFrame.this, controller, null, null, selectedDate);
                dlg.setVisible(true);
            });

            actionBtnsPanel.add(btnAIPrompt);
            actionBtnsPanel.add(btnAddSchedule);
            southContainer.add(actionBtnsPanel, BorderLayout.EAST);

            add(southContainer, BorderLayout.SOUTH);
        }

        private void switchViewMode(String modeKey) {
            this.currentViewMode = modeKey;
            centerCardLayout.show(centerCardContainer, modeKey);
            refreshAllViews();
        }

        public void changeSelectedDate(LocalDate newDate) {
            this.selectedDate = newDate;
            refreshAllViews();
        }

        /** @param rotatedByUser true면 [Refresh] 버튼을 표시하고, false면 숨긴다 */
        public void updateSyncBannerState(boolean rotatedByUser) {
            if (rotatedByUser || systemTimer.isAutoRefreshPaused()) {
                btnRefreshSync.setVisible(true);
                LocalTime previewT = circularCanvasPanel.getPreviewedTimeAtTop();
                lblRotateStatusText.setText(String.format("타임라인 회전 중 (탐색 시각 %s)",
                        previewT.format(DateTimeFormatter.ofPattern("HH:mm"))));
                lblRotateStatusText.setForeground(PRIMARY_BLUE);
            } else {
                btnRefreshSync.setVisible(false);
                lblRotateStatusText.setText("실시간 자동 트래킹 중 (드래그/휠로 미래 일정 프리뷰)");
                lblRotateStatusText.setForeground(TEXT_MUTED);
            }
            refreshSyncRow.revalidate();
            refreshSyncRow.repaint();
        }

        private void onSystemTimerTick() {
            LocalDateTime now = systemTimer.now();
            lblLiveClock.setText(String.format("🕒 %s %s",
                    now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    systemTimer.isSimulated() ? "(데모 시각)" : ""));

            controller.checkAndDispatchNotifications(now);

            if (!systemTimer.isAutoRefreshPaused()) {
                circularCanvasPanel.repaint();
                updateBottomOngoingCard();
            }
        }

        /**
         * 화면 상단 알림 배너를 펼치고, 시스템 트레이를 쓸 수 있으면 트레이 알림도 띄운다.
         *
         * @param s 알림 대상 일정
         */
        public void dispatchNotificationUI(ScheduleDTO s) {
            this.lastNotifiedSchedule = s;
            String msg = String.format("🔔 [일정 알림] '%s' (%s, 장소: %s) 시작 %s 전입니다! (클릭하여 상세 보기)",
                    s.title, s.getShortTimeRangeText(),
                    s.location.isEmpty() ? "미지정" : s.location,
                    ScheduleDTO.formatMinutes(s.notificationLeadMinutes));
            lblBannerNotificationText.setText(msg);
            notificationBannerDrawer.setExpanded(true, true);

            if (SystemTray.isSupported()) {
                try {
                    SystemTray tray = SystemTray.getSystemTray();
                    TrayIcon[] icons = tray.getTrayIcons();
                    if (icons.length > 0) {
                        icons[0].displayMessage("ChronoCircle 일정 알림", s.title + " (" + s.getTimeRangeText() + ")", TrayIcon.MessageType.INFO);
                    }
                } catch (Exception ignored) {}
            }
        }

        private void showTimeAndNotificationControlMenu(Component invoker) {
            JPopupMenu menu = new JPopupMenu();
            menu.setBackground(Color.WHITE);
            menu.setBorder(new LineBorder(BORDER_COLOR, 1, true));

            JMenuItem itemDemo = new JMenuItem("🎯 와이어프레임 데모 시각으로 설정 (2026-10-15 12:18, 42분 남음)");
            itemDemo.setFont(uiFont(Font.BOLD, 12));
            itemDemo.addActionListener(e -> {
                selectedDate = LocalDate.of(2026, 10, 15);
                systemTimer.setSimulationTarget(LocalDateTime.of(2026, 10, 15, 12, 18, 0));
                controller.resyncDashboardToCurrentTime(circularCanvasPanel, systemTimer);
                refreshAllViews();
            });

            JMenuItem itemAdd15 = new JMenuItem("⏩ 시뮬레이션 시각 +15분 빨리감기");
            itemAdd15.setFont(uiFont(Font.PLAIN, 12));
            itemAdd15.addActionListener(e -> systemTimer.addSimulationMinutes(15));

            JMenuItem itemRealTime = new JMenuItem("🕒 현재 실제 시스템 시각으로 동기화");
            itemRealTime.setFont(uiFont(Font.PLAIN, 12));
            itemRealTime.addActionListener(e -> {
                selectedDate = LocalDate.now();
                systemTimer.resetSimulationOffset();
                controller.resyncDashboardToCurrentTime(circularCanvasPanel, systemTimer);
                refreshAllViews();
            });

            JMenuItem itemTestNoti = new JMenuItem("🔔 스마트 푸시 알림 배너 즉시 테스트 (UC-06)");
            itemTestNoti.setFont(uiFont(Font.BOLD, 12));
            itemTestNoti.setForeground(INDIGO_PURPLE);
            itemTestNoti.addActionListener(e -> {
                ScheduleDTO active = circularCanvasPanel.getActiveOrPreviewSchedule();
                if (active != null) {
                    dispatchNotificationUI(active);
                } else {
                    List<ScheduleDTO> all = controller.getRepository().findAll();
                    if (!all.isEmpty()) dispatchNotificationUI(all.get(0));
                }
            });

            menu.add(itemDemo);
            menu.add(itemAdd15);
            menu.add(itemRealTime);
            menu.addSeparator();
            menu.add(itemTestNoti);
            menu.show(invoker, 0, invoker.getHeight() + 4);
        }

        private void triggerSearchPopup(boolean showEmptyNotice) {
            String keyword = searchField.getText();
            if (keyword == null || keyword.trim().isEmpty()) {
                searchPopup.setVisible(false);
                return;
            }
            List<ScheduleSummary> results = controller.searchSchedulesByKeyword(
                    keyword, LocalDate.of(2020, 1, 1), LocalDate.of(2035, 12, 31));

            searchPopup.removeAll();
            JPanel listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setBackground(Color.WHITE);
            listPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

            if (results.isEmpty()) {
                JLabel emptyLbl = new JLabel("검색 결과가 없습니다 — 다른 키워드로 재검색해 보세요.");
                emptyLbl.setFont(uiFont(Font.PLAIN, 12));
                emptyLbl.setForeground(TEXT_MUTED);
                emptyLbl.setBorder(new EmptyBorder(10, 12, 10, 12));
                listPanel.add(emptyLbl);
            } else {
                JLabel head = new JLabel(String.format("🔎 '%s' 검색 결과 (%d건)", keyword.trim(), results.size()));
                head.setFont(uiFont(Font.BOLD, 11));
                head.setForeground(PRIMARY_BLUE);
                head.setBorder(new EmptyBorder(4, 8, 6, 8));
                listPanel.add(head);

                DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREAN);
                for (ScheduleSummary sum : results) {
                    JPanel item = new JPanel(new BorderLayout(8, 2));
                    item.setBackground(Color.WHITE);
                    item.setBorder(BorderFactory.createCompoundBorder(
                            new MatteBorder(0, 0, 1, 0, BG_SUBTLE),
                            new EmptyBorder(7, 8, 7, 8)
                    ));
                    item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                    JLabel t = new JLabel(sum.title + (sum.location.isEmpty() ? "" : " · " + sum.location));
                    t.setFont(uiFont(Font.BOLD, 13));
                    t.setForeground(TEXT_PRIMARY);

                    JLabel sub = new JLabel(sum.startDateTime.format(df) + " | " + sum.matchedSnippet);
                    sub.setFont(uiFont(Font.PLAIN, 11));
                    sub.setForeground(TEXT_SECONDARY);

                    item.add(t, BorderLayout.NORTH);
                    item.add(sub, BorderLayout.SOUTH);

                    item.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) { item.setBackground(PRIMARY_LIGHT_BG); }
                        @Override
                        public void mouseExited(MouseEvent e) { item.setBackground(Color.WHITE); }
                        @Override
                        public void mousePressed(MouseEvent e) {
                            searchPopup.setVisible(false);
                            selectedDate = sum.startDateTime.toLocalDate();
                            refreshAllViews();
                            Optional<ScheduleDTO> target = controller.getRepository().findById(sum.scheduleId);
                            target.ifPresent(dto -> new ScheduleDetailDialog(MainFrame.this, controller, dto).setVisible(true));
                        }
                    });
                    listPanel.add(item);
                }
            }

            searchPopup.add(listPanel);
            searchPopup.pack();
            searchPopup.setPopupSize(Math.max(370, searchField.getWidth() + 40), Math.min(320, listPanel.getPreferredSize().height + 12));
            searchPopup.show(searchField.getParent(), 0, searchField.getParent().getHeight() + 4);
        }

        /** 모든 보기(원형 대시보드, 달력, 일과 목록, 하단 카드)를 저장소 내용으로 다시 그린다. */
        public void refreshAllViews() {
            DateTimeFormatter titleFmt = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN);
            lblCurrentDateTitle.setText(selectedDate.format(titleFmt));

            circularCanvasPanel.setCurrentDate(selectedDate);
            monthCalendarPanel.setSelectedDate(selectedDate);
            yearCalendarPanel.setYear(selectedDate.getYear());

            refreshSideTimelineDrawer();
            updateBottomOngoingCard();
            repaint();
        }

        private void refreshSideTimelineDrawer() {
            timelineListContainer.removeAll();
            List<ScheduleDTO> list = controller.getRepository().findByDate(selectedDate);
            LocalTime nowT = systemTimer.now().toLocalTime();
            LocalDateTime refNow = LocalDateTime.of(selectedDate, nowT);

            ScheduleDTO activePrimary = circularCanvasPanel.getActiveOrPreviewSchedule();
            ScheduleDTO immediateNext = circularCanvasPanel.getImmediateNextSchedule(activePrimary);

            lblDrawerSummary.setText(String.format("%s · 총 %d건 (클릭: 상세/수정, 호버: 회전 프리뷰)",
                    selectedDate.format(DateTimeFormatter.ofPattern("M월 d일(E)", Locale.KOREAN)), list.size()));

            if (list.isEmpty()) {
                JLabel empty = new JLabel("<html><div style='padding:16px;color:#94A3B8;'>이 날짜에 등록된 일정이 없습니다.<br/>우측 하단 버튼으로 새 일정을 등록해 보세요.</div></html>");
                empty.setFont(uiFont(Font.PLAIN, 12));
                timelineListContainer.add(empty);
            } else {
                Color nextTeal = new Color(0x0D, 0x94, 0x88);
                for (int i = 0; i < list.size(); i++) {
                    ScheduleDTO s = list.get(i);
                    boolean ongoing = s.isOngoingAt(refNow) || (activePrimary != null && Objects.equals(s.id, activePrimary.id) && !systemTimer.isAutoRefreshPaused());
                    boolean isImmediateNext = (immediateNext != null && Objects.equals(s.id, immediateNext.id));
                    boolean finished = !ongoing && !isImmediateNext && s.endDateTime.isBefore(refNow);

                    Color cardBg = ongoing
                            ? PRIMARY_LIGHT_BG
                            : (isImmediateNext ? new Color(0xF0, 0xFD, 0xFA) : (finished ? BG_MAIN : Color.WHITE));
                    Color cardBorder = ongoing
                            ? PRIMARY_BLUE
                            : (isImmediateNext ? nextTeal : BORDER_COLOR);
                    float strokeW = ongoing ? 2.6f : (isImmediateNext ? 2.0f : 1.2f);
                    Color stripeCol = ongoing ? PRIMARY_BLUE : (isImmediateNext ? nextTeal : null);

                    RoundedCardPanel itemCard = new RoundedCardPanel(12, cardBg, cardBorder, strokeW, stripeCol, ongoing);
                    itemCard.setLayout(new BorderLayout(8, 4));
                    itemCard.setBorder(new EmptyBorder(10, 14, 10, 12));
                    itemCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
                    itemCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                    String statusTag = ongoing
                            ? "▣ 현재 일정 (테두리 강조)"
                            : (isImmediateNext ? "⏭ 바로 다음 일정" : (finished ? "✓ 완료" : "이후 예정"));
                    Color statusCol = ongoing ? PRIMARY_BLUE : (isImmediateNext ? nextTeal : (finished ? TEXT_MUTED : TEXT_SECONDARY));

                    JPanel topLine = new JPanel(new BorderLayout());
                    topLine.setOpaque(false);
                    JLabel lblTime = new JLabel(s.getTimeRangeText());
                    lblTime.setFont(uiFont(Font.BOLD, 12));
                    lblTime.setForeground(ongoing ? PRIMARY_BLUE : TEXT_SECONDARY);

                    JLabel lblState = new JLabel(statusTag);
                    lblState.setFont(uiFont(Font.BOLD, 11));
                    lblState.setForeground(statusCol);
                    topLine.add(lblTime, BorderLayout.WEST);
                    topLine.add(lblState, BorderLayout.EAST);

                    JLabel lblTitle = new JLabel(s.title);
                    lblTitle.setFont(uiFont(Font.BOLD, 14));
                    lblTitle.setForeground(TEXT_PRIMARY);

                    JLabel lblLoc = new JLabel("📍 " + (s.location.isEmpty() ? "장소 미지정" : s.location)
                            + (s.notificationEnabled ? "  🔔 " + ScheduleDTO.formatMinutes(s.notificationLeadMinutes) + " 전" : ""));
                    lblLoc.setFont(uiFont(Font.PLAIN, 11));
                    lblLoc.setForeground(TEXT_MUTED);

                    itemCard.add(topLine, BorderLayout.NORTH);
                    itemCard.add(lblTitle, BorderLayout.CENTER);
                    itemCard.add(lblLoc, BorderLayout.SOUTH);

                    itemCard.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            circularCanvasPanel.setHighlightedScheduleId(s.id);
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            circularCanvasPanel.setHighlightedScheduleId(null);
                        }

                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (SwingUtilities.isRightMouseButton(e)) {
                                circularCanvasPanel.smoothRotateToSchedule(s);
                            } else {
                                circularCanvasPanel.smoothRotateToSchedule(s);
                                new ScheduleDetailDialog(MainFrame.this, controller, s).setVisible(true);
                            }
                        }
                    });

                    timelineListContainer.add(itemCard);
                    timelineListContainer.add(Box.createVerticalStrut(8));

                    if (i < list.size() - 1) {
                        ScheduleDTO next = list.get(i + 1);
                        long gapMin = ChronoUnit.MINUTES.between(s.endDateTime, next.startDateTime);
                        if (gapMin >= 30) {
                            JLabel gapLabel = new JLabel(String.format("   ↕ 여유 시간 %s (%s ~ %s)",
                                    formatDurationKor(gapMin),
                                    s.endDateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                                    next.startDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))));
                            gapLabel.setFont(uiFont(Font.PLAIN, 11));
                            gapLabel.setForeground(new Color(0x0D, 0x94, 0x88));
                            timelineListContainer.add(gapLabel);
                            timelineListContainer.add(Box.createVerticalStrut(8));
                        }
                    }
                }
            }
            timelineListContainer.revalidate();
            timelineListContainer.repaint();
        }

        private void updateBottomOngoingCard() {
            ScheduleDTO target = circularCanvasPanel.getActiveOrPreviewSchedule();
            LocalDateTime now = LocalDateTime.of(selectedDate, systemTimer.now().toLocalTime());

            if (target == null) {
                lblBottomCardHeader.setText("💡 금일 일정 안내");
                lblBottomScheduleTitle.setText("제목: 오늘 예정된 일정이 없습니다.");
                lblBottomScheduleLocation.setText("📍 우측 하단 [AI 프롬프트 등록] 또는 [일반 일정 등록]을 눌러 추가해 보세요.");
                lblBottomRemainingCaption.setText("일정 상태:");
                lblBottomRemainingValue.setText("일정 없음");
                ongoingProgressBar.setValue(0);
                ongoingProgressBar.setString("금일 등록된 일정 없음");
                return;
            }

            boolean ongoing = target.isOngoingAt(now);
            if (systemTimer.isAutoRefreshPaused()) {
                lblBottomCardHeader.setText("🔍 타임라인 회전 프리뷰 일정 (클릭 시 상세/수정)");
            } else if (ongoing) {
                lblBottomCardHeader.setText("💡 현재 진행 중인 일정");
            } else {
                lblBottomCardHeader.setText("⏭ 다음 예정 일정 브리핑");
            }

            ScheduleDTO nextAfterTarget = circularCanvasPanel.getImmediateNextSchedule(target);
            String nextInfo = (nextAfterTarget != null)
                    ? String.format("   |   ⏭ 바로 다음 일정: %s (%s)", nextAfterTarget.title, nextAfterTarget.getTimeRangeText())
                    : "   |   ⏭ 금일 이후 예정 일정 없음";
            lblBottomScheduleTitle.setText(String.format("제목: %s (%s)", target.title, target.getTimeRangeText()));
            lblBottomScheduleLocation.setText("📍 장소: " + (target.location.isEmpty() ? "미지정" : target.location) + nextInfo);

            if (ongoing) {
                long remainMin = Math.max(1, ChronoUnit.MINUTES.between(now, target.endDateTime));
                long totalMin = Math.max(1, ChronoUnit.MINUTES.between(target.startDateTime, target.endDateTime));
                long elapsedMin = Math.max(0, totalMin - remainMin);
                int pct = (int) Math.min(100, Math.round((elapsedMin * 100.0) / totalMin));

                lblBottomRemainingCaption.setText("남은 시간:");
                lblBottomRemainingValue.setText(formatDurationKor(remainMin) + " 남음");
                ongoingProgressBar.setValue(pct);
                ongoingProgressBar.setString(String.format("현재 일정 진행률 %d%% (경과 %s / 총 %s)",
                        pct, formatDurationKor(elapsedMin), formatDurationKor(totalMin)));
            } else if (target.startDateTime.isAfter(now)) {
                long untilMin = Math.max(1, ChronoUnit.MINUTES.between(now, target.startDateTime));
                lblBottomRemainingCaption.setText("시작까지:");
                lblBottomRemainingValue.setText(formatDurationKor(untilMin) + " 후");
                ongoingProgressBar.setValue(0);
                ongoingProgressBar.setString("시작 대기 중 (" + target.startDateTime.format(DateTimeFormatter.ofPattern("HH:mm")) + " 시작)");
            } else {
                lblBottomRemainingCaption.setText("일정 상태:");
                lblBottomRemainingValue.setText("종료됨");
                ongoingProgressBar.setValue(100);
                ongoingProgressBar.setString("완료된 일정입니다.");
            }
        }

        public static String formatDurationKor(long minutes) {
            if (minutes < 60) return minutes + "분";
            long h = minutes / 60;
            long m = minutes % 60;
            return m == 0 ? (h + "시간") : (h + "시간 " + m + "분");
        }
    }

    /**
     * 프로그램 시작점. <code>--verify-headless [출력 폴더]</code> 인자를 주면 창 없이 핵심 함수 검증만 수행한다.
     *
     * @param args 명령행 인자
     */
    public static void main(String[] args) {
        initFontAndLook();

        ScheduleRepository repo = new ScheduleRepository();
        DraftSessionManager draftMgr = new DraftSessionManager();
        AIPromptParser parser = new AIPromptParser();
        ScheduleController controller = new ScheduleController(repo, draftMgr, parser);

        if (args.length > 0 && "--verify-headless".equals(args[0])) {
            String outDir = args.length > 1 ? args[1] : ".";
            runSelfVerificationAndRenderPreviews(controller, outDir);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(controller);
            frame.setVisible(true);
        });
    }

    private static void runSelfVerificationAndRenderPreviews(ScheduleController controller, String outDir) {
        System.out.println("=== [ChronoCircle] 6대 핵심 명세 함수 및 UI 렌더링 검증 시작 ===");

        ParsedScheduleDTO parsed = controller.parseNaturalLanguagePrompt(
                "내일 오후 3시 소프트웨어공학 연구실에서 캡스톤 프로젝트 최종 회의");
        System.out.println("[1] parseNaturalLanguagePrompt 결과 -> 제목: " + parsed.title
                + " | 시작: " + parsed.startDateTime + " | 종료: " + parsed.endDateTime
                + " | 장소: " + parsed.location);

        ScheduleDTO draftSample = new ScheduleDTO(null, "작성 중이던 임시 일정",
                LocalDateTime.of(2026, 10, 15, 17, 0), LocalDateTime.of(2026, 10, 15, 18, 0),
                "공대 도서관", "실수로 창을 닫아도 보존되는 메모", true, 30, 0, 2);
        boolean draftOk = controller.saveOrUpdateSchedule(draftSample, true);
        System.out.println("[2] saveOrUpdateSchedule(isDraft=true) -> " + draftOk
                + " | Draft 복원 확인: " + controller.getDraftManager().getDraft().title);
        controller.getDraftManager().clearDraft();

        List<CircularScheduleSegment> segs = controller.calculateCircularSegments(
                LocalDate.of(2026, 10, 15), LocalTime.of(12, 18));
        System.out.println("[3] calculateCircularSegments(2026-10-15, 12:18) -> 세그먼트 수: " + segs.size());

        int notiCount = controller.checkAndDispatchNotifications(LocalDateTime.of(2026, 10, 15, 10, 30));
        System.out.println("[4] checkAndDispatchNotifications(2026-10-15 10:30) -> 발송 건수: " + notiCount);

        List<ScheduleSummary> searchRes = controller.searchSchedulesByKeyword(
                "소프트웨어공학", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        System.out.println("[5] searchSchedulesByKeyword('소프트웨어공학') -> 검색 건수: " + searchRes.size());

        System.out.println("=== [ChronoCircle] 모든 검증 완료 ===");
    }
}