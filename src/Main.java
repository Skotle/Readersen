import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import crawler.*;
import counter.CustomAnalyzer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static JLabel lblStatus  = new JLabel("상태: 대기 중");
    private static JLabel lblPage    = new JLabel("페이지 진행: -");
    private static JLabel lblComment = new JLabel("댓글 진행: -");
    private static JLabel lblSpeed   = new JLabel("현재 속도: -");
    private static JLabel lblEta     = new JLabel("남은 시간: -");
    private static final JLabel lblElapsed = new JLabel("경과 시간: 00:00:00");
    private static final JLabel lblCounts = new JLabel("누적: 글 0 | 댓글 0 | 작성자 0");
    private static final JLabel lblDateRange = new JLabel("날짜 진행: -");
    private static final JProgressBar overallProgress = createProgressBar(3, "실행 대기");
    private static final JProgressBar pageProgress = createProgressBar(1, "페이지 대기");
    private static final JProgressBar commentProgress = createProgressBar(1, "댓글 대기");
    private static final SpeedChartPanel speedChart = new SpeedChartPanel();
    private static final JTextArea txtLog = new JTextArea();
    private static final int MAX_LOG_CHARS = 200_000;
    private static final Pattern FRACTION_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static final Pattern SPEED_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*r/s");
    private static final Pattern ETA_PATTERN = Pattern.compile("남은\\s*(\\d+)초");
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "날짜 범위:\\s*(\\d{4}-\\d{2}-\\d{2})\\s*~\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static javax.swing.Timer executionTimer;
    private static long executionStartedAt;
    private static int collectedPosts;
    private static int collectedComments;
    private static int uniqueAuthors;

    private static final List<PostProcessScript> POST_PROCESS_SCRIPTS = List.of(
            new PostProcessScript("ExcelPrinter.py", null),
            new PostProcessScript("GraphPrinter.py", null),
            new PostProcessScript("GraphPrinter_sub.py", null),
            new PostProcessScript("daily-count.py", Main::askDailyCountInput),
            new PostProcessScript("analyzer.py", null),
            new PostProcessScript("move.py", Main::askMoveInput),
            new PostProcessScript("move1.py", Main::askMoveInput),
            new PostProcessScript("move2.py", Main::askMoveInput),
            new PostProcessScript("temp.py", Main::askTempModeInput),
            new PostProcessScript("live-graph.py", null)
    );

    public static void main(String[] args) {
        JFrame frame = new JFrame("Readersen");
        frame.setSize(920, 880);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(15, 15));

        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 5, 5));

        JTextField txtId = new JTextField("");
        JButton btnFindGallery = new JButton("갤러리 검색");
        JTextField txtStart = new JTextField("1");
        JTextField txtEnd = new JTextField("1");
        JLabel lblStartInput = new JLabel(" Start page:");
        JLabel lblEndInput = new JLabel(" End page:");

        JComboBox<String> rangeModeBox = new JComboBox<>();
        rangeModeBox.addItem("PAGE");
        rangeModeBox.addItem("DATE");
        rangeModeBox.addItem("ALL");
        rangeModeBox.setSelectedItem("PAGE");
        JTextField txtStartDate = new JTextField(LocalDate.now().minusDays(7).toString());
        JTextField txtEndDate = new JTextField(LocalDate.now().toString());
        JButton btnStartDatePicker = new JButton("선택");
        JButton btnEndDatePicker = new JButton("선택");

        JComboBox<String> typeBox = new JComboBox<>();
        typeBox.addItem("main");
        typeBox.addItem("m");
        typeBox.addItem("mini");
        typeBox.setSelectedItem("m");

        JComboBox<Integer> threadBox = new JComboBox<>();
        threadBox.addItem(1);
        for (int i = 2; i <= 120; i += 2) threadBox.addItem(i);
        threadBox.setSelectedItem(30);

        JComboBox<String> runScopeBox = new JComboBox<>();
        runScopeBox.addItem("페이지 파서만");
        runScopeBox.addItem("댓글까지 진행");
        runScopeBox.setSelectedItem("댓글까지 진행");

        JButton btnRun = new JButton("분석 시작");
        JButton btnRunPostProcess = new JButton("선택 실행");
        JButton btnSelectAll = new JButton("전체 선택");
        JButton btnClearAll = new JButton("선택 해제");
        Map<JCheckBox, PostProcessScript> scriptChecks = new LinkedHashMap<>();

        JPanel galleryInputPanel = new JPanel(new BorderLayout(5, 0));
        galleryInputPanel.add(txtId, BorderLayout.CENTER);
        galleryInputPanel.add(btnFindGallery, BorderLayout.EAST);
        JPanel startDatePanel = new JPanel(new BorderLayout(5, 0));
        startDatePanel.add(txtStartDate, BorderLayout.CENTER);
        startDatePanel.add(btnStartDatePicker, BorderLayout.EAST);
        JPanel endDatePanel = new JPanel(new BorderLayout(5, 0));
        endDatePanel.add(txtEndDate, BorderLayout.CENTER);
        endDatePanel.add(btnEndDatePicker, BorderLayout.EAST);

        inputPanel.add(new JLabel(" TYPE:")); inputPanel.add(typeBox);
        inputPanel.add(new JLabel(" Range mode:")); inputPanel.add(rangeModeBox);
        inputPanel.add(new JLabel(" Start date:")); inputPanel.add(startDatePanel);
        inputPanel.add(new JLabel(" End date:")); inputPanel.add(endDatePanel);
        inputPanel.add(new JLabel(" 갤러리 ID:")); inputPanel.add(galleryInputPanel);
        inputPanel.add(new JLabel(" 시작 페이지:")); inputPanel.add(txtStart);
        inputPanel.add(new JLabel(" 종료 페이지:")); inputPanel.add(txtEnd);
        inputPanel.add(new JLabel(" 실행 범위:")); inputPanel.add(runScopeBox);
        inputPanel.add(new JLabel(" 댓글 스레드 수:")); inputPanel.add(threadBox);
        inputPanel.add(new JLabel(" 작업 실행:")); inputPanel.add(btnRun);

        JPanel statusPanel = new JPanel(new BorderLayout(8, 8));
        statusPanel.setBorder(BorderFactory.createTitledBorder("실시간 실행 대시보드"));

        Font boldFont = new Font("맑은 고딕", Font.BOLD, 15);

        lblStatus.setFont(boldFont);
        lblStatus.setForeground(Color.BLUE);
        lblPage.setFont(boldFont);
        lblComment.setFont(boldFont);
        lblSpeed.setFont(boldFont);
        lblSpeed.setForeground(new Color(0, 120, 0));
        lblEta.setFont(boldFont);
        lblEta.setForeground(new Color(150, 80, 0));
        lblElapsed.setFont(boldFont);
        lblCounts.setFont(boldFont.deriveFont(13f));
        lblCounts.setForeground(new Color(55, 65, 81));
        lblDateRange.setFont(boldFont.deriveFont(13f));
        lblDateRange.setForeground(new Color(126, 72, 25));

        JPanel statusHeader = new JPanel(new BorderLayout(8, 0));
        statusHeader.add(lblStatus, BorderLayout.CENTER);
        statusHeader.add(lblElapsed, BorderLayout.EAST);

        JPanel progressPanel = new JPanel(new GridLayout(3, 1, 4, 4));
        progressPanel.add(overallProgress);
        progressPanel.add(pageProgress);
        progressPanel.add(commentProgress);

        JPanel metricPanel = new JPanel(new GridLayout(2, 3, 8, 3));
        metricPanel.add(lblPage);
        metricPanel.add(lblComment);
        metricPanel.add(lblCounts);
        metricPanel.add(lblSpeed);
        metricPanel.add(lblEta);
        metricPanel.add(lblDateRange);

        JPanel dashboardCenter = new JPanel(new BorderLayout(8, 6));
        dashboardCenter.add(progressPanel, BorderLayout.NORTH);
        dashboardCenter.add(metricPanel, BorderLayout.CENTER);
        dashboardCenter.add(speedChart, BorderLayout.SOUTH);

        statusPanel.add(statusHeader, BorderLayout.NORTH);
        statusPanel.add(dashboardCenter, BorderLayout.CENTER);

        JPanel postProcessPanel = new JPanel(new BorderLayout(8, 8));
        postProcessPanel.setBorder(BorderFactory.createTitledBorder("후가공 스크립트"));

        JPanel scriptListPanel = new JPanel();
        scriptListPanel.setLayout(new BoxLayout(scriptListPanel, BoxLayout.Y_AXIS));
        for (PostProcessScript script : POST_PROCESS_SCRIPTS) {
            JCheckBox checkBox = new JCheckBox(script.fileName());
            if (!new File(script.fileName()).exists()) {
                checkBox.setEnabled(false);
                checkBox.setText(script.fileName() + " (파일 없음)");
            }
            scriptChecks.put(checkBox, script);
            scriptListPanel.add(checkBox);
        }

        JPanel postButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        postButtonPanel.add(btnSelectAll);
        postButtonPanel.add(btnClearAll);
        postButtonPanel.add(btnRunPostProcess);

        postProcessPanel.add(new JScrollPane(scriptListPanel), BorderLayout.CENTER);
        postProcessPanel.add(postButtonPanel, BorderLayout.SOUTH);

        txtLog.setEditable(false);
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);
        txtLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(txtLog);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("실행 로그"));
        logScrollPane.setPreferredSize(new Dimension(0, 190));

        CollectionProgress.setListener(Main::updateCollectionDashboard);
        redirectSystemStreams();

        runScopeBox.addActionListener(e -> {
            boolean includeComments = "댓글까지 진행".equals(runScopeBox.getSelectedItem());
            threadBox.setEnabled(includeComments);
        });

        Runnable updateRangeInputs = () -> {
            String selectedMode = String.valueOf(rangeModeBox.getSelectedItem());
            boolean dateMode = "DATE".equals(selectedMode);
            boolean pageMode = "PAGE".equals(selectedMode);
            txtStart.setEnabled(pageMode);
            txtEnd.setEnabled(pageMode);
            txtStartDate.setEnabled(dateMode);
            txtEndDate.setEnabled(dateMode);
            btnStartDatePicker.setEnabled(dateMode);
            btnEndDatePicker.setEnabled(dateMode);
        };
        rangeModeBox.addActionListener(e -> updateRangeInputs.run());
        updateRangeInputs.run();
        btnStartDatePicker.addActionListener(e -> pickDate(frame, txtStartDate));
        btnEndDatePicker.addActionListener(e -> pickDate(frame, txtEndDate));

        btnFindGallery.addActionListener(e -> searchAndApplyGallery(frame, txtId, typeBox, btnFindGallery));

        btnSelectAll.addActionListener(e -> scriptChecks.keySet().stream()
                .filter(JCheckBox::isEnabled)
                .forEach(checkBox -> checkBox.setSelected(true)));
        btnClearAll.addActionListener(e -> scriptChecks.keySet()
                .forEach(checkBox -> checkBox.setSelected(false)));

        btnRunPostProcess.addActionListener(e -> {
            List<PostProcessScript> selectedScripts = scriptChecks.entrySet().stream()
                    .filter(entry -> entry.getKey().isSelected() && entry.getKey().isEnabled())
                    .map(Map.Entry::getValue)
                    .toList();

            if (selectedScripts.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "실행할 후가공 스크립트를 선택하세요.");
                return;
            }

            clearLog();
            new Thread(() -> {
                try {
                    setPostProcessButtonsEnabled(btnRunPostProcess, btnSelectAll, btnClearAll, false);
                    lblStatus.setForeground(Color.BLUE);
                    lblStatus.setText("상태: 후가공 실행 중...");
                    runPostProcessScripts(frame, selectedScripts);
                    lblStatus.setText("상태: 후가공 완료");
                    JOptionPane.showMessageDialog(frame, "선택한 후가공 스크립트 실행이 완료되었습니다.");
                } catch (Exception ex) {
                    lblStatus.setText("상태: 후가공 에러 발생!");
                    lblStatus.setForeground(Color.RED);
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(frame, ex.getMessage(), "후가공 에러", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setPostProcessButtonsEnabled(btnRunPostProcess, btnSelectAll, btnClearAll, true);
                }
            }).start();
        });

        btnRun.addActionListener(e -> {
            clearLog();
            resetExecutionDashboard();
            startExecutionTimer();
            new Thread(() -> {
                try {
                    btnRun.setEnabled(false);
                    lblStatus.setForeground(Color.BLUE);
                    lblPage.setText("페이지 진행: 대기 중...");
                    lblComment.setText("댓글 진행: 대기 중...");
                    lblSpeed.setText("현재 속도: -");
                    lblEta.setText("남은 시간: -");

                    String type = (String) typeBox.getSelectedItem();
                    String id = txtId.getText().trim();
                    String rangeMode = String.valueOf(rangeModeBox.getSelectedItem());
                    boolean dateMode = "DATE".equals(rangeMode);
                    boolean allMode = "ALL".equals(rangeMode);
                    int start = 0;
                    int end = 0;
                    LocalDate startDate = null;
                    LocalDate endDate = null;
                    if (dateMode) {
                        startDate = LocalDate.parse(txtStartDate.getText().trim());
                        endDate = LocalDate.parse(txtEndDate.getText().trim());
                    } else if (!allMode) {
                        start = Integer.parseInt(txtStart.getText().trim());
                        end = Integer.parseInt(txtEnd.getText().trim());
                    }
                    int threads = (int) threadBox.getSelectedItem();
                    boolean includeComments = "댓글까지 진행".equals(runScopeBox.getSelectedItem());

                    if (id.isEmpty()) {
                        throw new IllegalArgumentException("갤러리 ID를 입력하세요.");
                    }
                    if (!dateMode && !allMode && (start <= 0 || end < start)) {
                        throw new IllegalArgumentException("페이지 범위가 올바르지 않습니다.");
                    }

                    if (dateMode && endDate.isBefore(startDate)) {
                        throw new IllegalArgumentException("End date must be the same as or after start date.");
                    }

                    long startTime = System.currentTimeMillis();

                    System.out.println("===== 실행 설정 =====");
                    System.out.println("TYPE: " + type);
                    System.out.println("ID: " + id);
                    System.out.println("Range mode: " + rangeMode);
                    if (dateMode) {
                        System.out.println("Date range: " + startDate + " ~ " + endDate);
                    } else if (allMode) {
                        System.out.println("Page range: ALL");
                    } else {
                        System.out.println("Page range: " + start + " ~ " + end);
                    }
                    System.out.println("Threads: " + threads);
                    System.out.println("Scope: " + (includeComments ? "페이지+댓글" : "페이지만"));
                    Path runDir = allMode
                            ? createAllRunDirectory(id, type, includeComments)
                            : (dateMode
                                    ? createDateRunDirectory(id, type, startDate, endDate, includeComments)
                                    : createRunDirectory(id, type, start, end, includeComments));
                    System.out.println("Output: " + runDir.toAbsolutePath());
                    prepareDateDashboard(dateMode, startDate, endDate);

                    lblStatus.setText("상태: [1/3] 페이지 목록 수집 중...");
                    setExecutionStage(1, "1/3 페이지 목록 수집 중");
                    CrawlerResult result = allMode
                            ? page_parser.CrawlerAll(id, type, 60)
                            : (dateMode
                                    ? page_parser.CrawlerByDate(id, type, startDate, endDate, 60)
                                    : page_parser.Crawler(id, type, start, end, 60));
                    System.out.println("Detected pages: " + result.startPage + " ~ " + result.endPage);
                    lblPage.setText("페이지 진행: 수집 완료");
                    completePageDashboard(result);
                    completeDateDashboard(dateMode, result.DayBox);

                    if (!result.gallType.isBlank() && !result.gallType.equals(type)) {
                        type = result.gallType;
                        System.out.println("댓글 요청 TYPE도 보정된 값으로 변경: " + type);
                    }

                    lblStatus.setText("상태: [2/3] 댓글 데이터 파싱 중...");
                    setExecutionStage(2, "2/3 댓글 데이터 파싱 중");
                    prepareCommentDashboard(result.RepleTrueBox.size(), includeComments);
                    subResult commsub;
                    if (!includeComments) {
                        System.out.println("실행 범위가 페이지 파서만으로 설정되어 댓글 파싱을 건너뜁니다.");
                        lblComment.setText("댓글 진행: 건너뜀");
                        commsub = new subResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                    } else if (result.RepleTrueBox.isEmpty()) {
                        System.out.println("댓글이 있는 글을 찾지 못해 댓글 파싱을 건너뜁니다.");
                        lblComment.setText("댓글 진행: 건너뜀");
                        commsub = new subResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                    } else {
                        commsub = comment_Parser.geulp(id, type, result.RepleTrueBox, threads);
                        lblComment.setText("댓글 진행: 파싱 완료");
                    }
                    lblSpeed.setText("현재 속도: -");
                    lblEta.setText("남은 시간: -");
                    completeCommentDashboard(includeComments && !result.RepleTrueBox.isEmpty());

                    lblStatus.setText("상태: [3/3] 최종 결과 분석 중...");
                    setExecutionStage(3, "3/3 최종 결과 분석 중");
                    CustomAnalyzer analyzer = new CustomAnalyzer();
                    analyzer.analyzeData(
                            result.authorBox, result.IDBox, result.IpBox,
                            result.viewBox, result.recomBox, result.repleBox);
                    analyzer.applyCommList(commsub.Names, commsub.IDs, commsub.Ips);
                    analyzer.printSummary(runDir.resolve("example.txt").toString());
                    analyzer.textprinter(result.DayBox, runDir.resolve("date-data.txt").toString());
                    analyzer.textprinter(commsub.Contents, runDir.resolve("contents_data.txt").toString());
                    analyzer.saveUserLog(result.authorBox, result.IDBox, result.IpBox, result.DayBox, runDir.resolve("daily-data.txt").toString());
                    analyzer.textprinter(commsub.Days, runDir.resolve("date-data-comment.txt").toString());
                    analyzer.saveUserLog(commsub.Names, commsub.IDs, commsub.Ips, commsub.Days, runDir.resolve("daily-data-comment.txt").toString());
                    writeRunMetadata(runDir, id, type, rangeMode, start, end, startDate, endDate,
                            threads, includeComments, result, commsub, startTime);
                    mirrorLatestSourceOutputs(runDir);

                    System.out.println("총소요: " + (System.currentTimeMillis() - startTime) / 1000 + "초");
                    System.out.println("대상 타겟: " + result.RepleTrueBox.size() + "/" + result.IDBox.size() + "개");
                    System.out.println("실행별 산출 폴더: " + runDir.toAbsolutePath());

                    lblStatus.setText("상태: 모든 작업 완료");
                    completeExecutionDashboard();
                    JOptionPane.showMessageDialog(frame, "분석 완료! 결과 파일을 확인하세요.");
                } catch (Exception ex) {
                    lblStatus.setText("상태: 에러 발생!");
                    lblStatus.setForeground(Color.RED);
                    failExecutionDashboard();
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(frame, ex.getMessage(), "에러", JOptionPane.ERROR_MESSAGE);
                } finally {
                    stopExecutionTimer();
                    btnRun.setEnabled(true);
                }
            }).start();
        });

        frame.add(inputPanel, BorderLayout.NORTH);
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.add(statusPanel, BorderLayout.NORTH);
        centerPanel.add(postProcessPanel, BorderLayout.CENTER);
        centerPanel.add(logScrollPane, BorderLayout.SOUTH);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void pickDate(Component parent, JTextField targetField) {
        LocalDate initialDate = parseDateOrToday(targetField.getText());
        LocalDate selectedDate = showCalendarDialog(parent, initialDate);
        if (selectedDate != null) {
            targetField.setText(selectedDate.toString());
        }
    }

    private static LocalDate parseDateOrToday(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (RuntimeException ignored) {
            return LocalDate.now();
        }
    }

    private static LocalDate showCalendarDialog(Component parent, LocalDate initialDate) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "날짜 선택", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));

        LocalDate[] selectedDate = {null};
        YearMonth[] visibleMonth = {YearMonth.from(initialDate)};

        JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
        JButton prevMonth = new JButton("<");
        JButton nextMonth = new JButton(">");
        JPanel headerPanel = new JPanel(new BorderLayout(5, 0));
        headerPanel.add(prevMonth, BorderLayout.WEST);
        headerPanel.add(monthLabel, BorderLayout.CENTER);
        headerPanel.add(nextMonth, BorderLayout.EAST);

        JPanel calendarPanel = new JPanel(new GridLayout(0, 7, 3, 3));
        Runnable[] refreshCalendar = new Runnable[1];
        refreshCalendar[0] = () -> {
            calendarPanel.removeAll();
            YearMonth month = visibleMonth[0];
            monthLabel.setText(month.getYear() + "-" + String.format("%02d", month.getMonthValue()));

            for (String dayName : List.of("일", "월", "화", "수", "목", "금", "토")) {
                JLabel dayLabel = new JLabel(dayName, SwingConstants.CENTER);
                dayLabel.setFont(dayLabel.getFont().deriveFont(Font.BOLD));
                calendarPanel.add(dayLabel);
            }

            LocalDate firstDay = month.atDay(1);
            int leadingEmptyCells = firstDay.getDayOfWeek().getValue() % 7;
            for (int i = 0; i < leadingEmptyCells; i++) {
                calendarPanel.add(new JLabel(""));
            }

            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                JButton dayButton = new JButton(String.valueOf(day));
                if (date.equals(initialDate)) {
                    dayButton.setBackground(new Color(210, 230, 255));
                    dayButton.setOpaque(true);
                }
                dayButton.addActionListener(e -> {
                    selectedDate[0] = date;
                    dialog.dispose();
                });
                calendarPanel.add(dayButton);
            }

            calendarPanel.revalidate();
            calendarPanel.repaint();
        };

        prevMonth.addActionListener(e -> {
            visibleMonth[0] = visibleMonth[0].minusMonths(1);
            refreshCalendar[0].run();
        });
        nextMonth.addActionListener(e -> {
            visibleMonth[0] = visibleMonth[0].plusMonths(1);
            refreshCalendar[0].run();
        });

        JButton todayButton = new JButton("오늘");
        todayButton.addActionListener(e -> {
            selectedDate[0] = LocalDate.now();
            dialog.dispose();
        });
        JButton cancelButton = new JButton("취소");
        cancelButton.addActionListener(e -> dialog.dispose());
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footerPanel.add(todayButton);
        footerPanel.add(cancelButton);

        refreshCalendar[0].run();
        dialog.add(headerPanel, BorderLayout.NORTH);
        dialog.add(calendarPanel, BorderLayout.CENTER);
        dialog.add(footerPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(360, 300));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return selectedDate[0];
    }

    private static void runPostProcessScripts(Component parent, List<PostProcessScript> scripts) throws Exception {
        for (PostProcessScript script : scripts) {
            List<String> inputLines = script.inputProvider() == null
                    ? List.of()
                    : script.inputProvider().getInput(parent);
            if (inputLines == null) {
                System.out.println("[후가공 취소] " + script.fileName());
                return;
            }
            runPythonScript(script.fileName(), inputLines);
        }
    }

    private static void runPythonScript(String scriptName, List<String> inputLines) throws Exception {
        System.out.println("\n===== 후가공 실행: " + scriptName + " =====");
        Process process = startPythonProcess(scriptName);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            for (String inputLine : inputLines) {
                writer.write(inputLine);
                writer.newLine();
            }
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[" + scriptName + "] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(scriptName + " 실행 실패 (exit code: " + exitCode + ")");
        }
        System.out.println("===== 완료: " + scriptName + " =====");
    }

    private static Process startPythonProcess(String scriptName) throws IOException {
        try {
            return createPythonProcess("python", scriptName).start();
        } catch (IOException pythonException) {
            System.out.println("python 명령 실행 실패, py -3로 재시도합니다.");
            try {
                return createPythonProcess("py", "-3", scriptName).start();
            } catch (IOException pyException) {
                pyException.addSuppressed(pythonException);
                throw pyException;
            }
        }
    }

    private static ProcessBuilder createPythonProcess(String... command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(System.getProperty("user.dir")));
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        return processBuilder;
    }

    private static Path createRunDirectory(String id, String type, int start, int end, boolean includeComments) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String scope = includeComments ? "comments" : "pages";
        String folderName = String.format(
                "%s_%s_%s_p%d-%d_%s",
                timestamp,
                sanitizePathPart(id),
                sanitizePathPart(type),
                start,
                end,
                scope
        );
        Path runDir = Paths.get(System.getProperty("user.dir"), "runs", folderName).toAbsolutePath().normalize();
        Files.createDirectories(runDir);
        return runDir;
    }

    private static Path createDateRunDirectory(String id, String type, LocalDate startDate, LocalDate endDate,
                                               boolean includeComments) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String scope = includeComments ? "comments" : "pages";
        String folderName = String.format(
                "%s_%s_%s_d%s-%s_%s",
                timestamp,
                sanitizePathPart(id),
                sanitizePathPart(type),
                sanitizePathPart(startDate.toString()),
                sanitizePathPart(endDate.toString()),
                scope
        );
        Path runDir = Paths.get(System.getProperty("user.dir"), "runs", folderName).toAbsolutePath().normalize();
        Files.createDirectories(runDir);
        return runDir;
    }

    private static Path createAllRunDirectory(String id, String type, boolean includeComments) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String scope = includeComments ? "comments" : "pages";
        String folderName = String.format(
                "%s_%s_%s_all_%s",
                timestamp,
                sanitizePathPart(id),
                sanitizePathPart(type),
                scope
        );
        Path runDir = Paths.get(System.getProperty("user.dir"), "runs", folderName).toAbsolutePath().normalize();
        Files.createDirectories(runDir);
        return runDir;
    }

    private static String sanitizePathPart(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "unknown" : sanitized;
    }

    private static void writeRunMetadata(Path runDir, String id, String type, String rangeMode,
                                         int start, int end, LocalDate startDate, LocalDate endDate,
                                         int threads, boolean includeComments, CrawlerResult result,
                                         subResult commsub, long startTime) throws IOException {
        List<String> lines = List.of(
                "created_at=" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "id=" + id,
                "type=" + type,
                "range_mode=" + rangeMode,
                "input_start_page=" + ("PAGE".equals(rangeMode) ? start : ""),
                "input_end_page=" + ("PAGE".equals(rangeMode) ? end : ""),
                "input_start_date=" + ("DATE".equals(rangeMode) ? startDate : ""),
                "input_end_date=" + ("DATE".equals(rangeMode) ? endDate : ""),
                "detected_start_page=" + result.startPage,
                "detected_end_page=" + result.endPage,
                "comment_threads=" + threads,
                "include_comments=" + includeComments,
                "posts=" + result.IDBox.size(),
                "comment_targets=" + result.RepleTrueBox.size(),
                "comments=" + commsub.Names.size(),
                "elapsed_seconds=" + ((System.currentTimeMillis() - startTime) / 1000)
        );
        Files.createDirectories(runDir);
        Files.write(runDir.resolve("run-metadata.txt"), lines, StandardCharsets.UTF_8);
    }

    private static void mirrorLatestSourceOutputs(Path runDir) throws IOException {
        String[] outputFiles = {
                "example.txt",
                "date-data.txt",
                "contents_data.txt",
                "daily-data.txt",
                "date-data-comment.txt",
                "daily-data-comment.txt",
                "run-metadata.txt"
        };

        for (String fileName : outputFiles) {
            Path source = runDir.resolve(fileName);
            if (Files.exists(source)) {
                Files.copy(source, Paths.get(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static List<String> askDailyCountInput(Component parent) {
        String start = JOptionPane.showInputDialog(parent, "시작 날짜를 입력하세요. (YYYY-MM-DD)", "daily-count.py", JOptionPane.QUESTION_MESSAGE);
        if (start == null) return null;
        String end = JOptionPane.showInputDialog(parent, "종료 날짜를 입력하세요. (YYYY-MM-DD)", "daily-count.py", JOptionPane.QUESTION_MESSAGE);
        if (end == null) return null;
        return List.of(start.trim(), end.trim());
    }

    private static List<String> askTempModeInput(Component parent) {
        Object selected = JOptionPane.showInputDialog(
                parent,
                "분석 단위를 선택하세요.",
                "temp.py",
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"M", "W", "D"},
                "M");
        if (selected == null) return null;
        return List.of(selected.toString());
    }

    private static List<String> askMoveInput(Component parent) {
        Object mode = JOptionPane.showInputDialog(
                parent,
                "분석 단위를 선택하세요.",
                "move.py",
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"D", "W", "M"},
                "D");
        if (mode == null) return null;

        Object graphType = JOptionPane.showInputDialog(
                parent,
                "그래프 종류를 선택하세요.",
                "move.py",
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"B", "A", "C"},
                "B");
        if (graphType == null) return null;

        String topN = JOptionPane.showInputDialog(parent, "표시할 사용자 수 N을 입력하세요. (최대 28)", "10");
        if (topN == null) return null;

        Object barStyle = JOptionPane.showInputDialog(
                parent,
                "집계 막대 스타일을 선택하세요. (G: 분리, S: 쌓기, P: 비율, R: 순위쌓기)",
                "move.py",
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"G", "S", "P", "R"},
                "G");
        if (barStyle == null) return null;

        return List.of(mode.toString(), graphType.toString(), topN.trim(), barStyle.toString());
    }

    private static void setPostProcessButtonsEnabled(JButton runButton, JButton selectAllButton, JButton clearAllButton, boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            runButton.setEnabled(enabled);
            selectAllButton.setEnabled(enabled);
            clearAllButton.setEnabled(enabled);
        });
    }

    private static JProgressBar createProgressBar(int maximum, String text) {
        JProgressBar progressBar = new JProgressBar(0, Math.max(1, maximum));
        progressBar.setStringPainted(true);
        progressBar.setString(text);
        progressBar.setPreferredSize(new Dimension(0, 22));
        return progressBar;
    }

    private static void resetExecutionDashboard() {
        executionStartedAt = System.currentTimeMillis();
        collectedPosts = 0;
        collectedComments = 0;
        uniqueAuthors = 0;
        overallProgress.setForeground(new Color(194, 120, 3));
        overallProgress.setMaximum(3);
        overallProgress.setValue(0);
        overallProgress.setIndeterminate(false);
        overallProgress.setString("실행 준비 중");
        pageProgress.setForeground(new Color(37, 99, 235));
        pageProgress.setMaximum(1);
        pageProgress.setValue(0);
        pageProgress.setIndeterminate(true);
        pageProgress.setString("페이지 범위 확인 중");
        commentProgress.setForeground(new Color(22, 163, 74));
        commentProgress.setMaximum(1);
        commentProgress.setValue(0);
        commentProgress.setIndeterminate(false);
        commentProgress.setString("댓글 대기");
        lblElapsed.setText("경과 시간: 00:00:00");
        lblCounts.setText("누적: 글 0 | 댓글 0 | 작성자 0");
        lblDateRange.setText("날짜 진행: -");
        speedChart.reset();
        CollectionProgress.reset();
    }

    private static void startExecutionTimer() {
        if (executionTimer != null) {
            executionTimer.stop();
        }
        executionTimer = new javax.swing.Timer(1000, e -> {
            long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - executionStartedAt) / 1000);
            lblElapsed.setText("경과 시간: " + formatDuration(elapsedSeconds));
        });
        executionTimer.start();
    }

    private static void stopExecutionTimer() {
        SwingUtilities.invokeLater(() -> {
            if (executionTimer != null) {
                executionTimer.stop();
            }
            long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - executionStartedAt) / 1000);
            lblElapsed.setText("경과 시간: " + formatDuration(elapsedSeconds));
        });
    }

    private static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static void setExecutionStage(int stage, String text) {
        SwingUtilities.invokeLater(() -> {
            overallProgress.setIndeterminate(false);
            overallProgress.setValue(Math.max(0, Math.min(2, stage - 1)));
            overallProgress.setString(text);
            if (stage == 1 && pageProgress.getValue() == 0) {
                pageProgress.setIndeterminate(true);
                pageProgress.setString("페이지 수집 준비 중");
            }
        });
    }

    private static void completePageDashboard(CrawlerResult result) {
        CollectionProgress.publishNow();
        SwingUtilities.invokeLater(() -> {
            int totalPages = Math.max(1, result.endPage - result.startPage + 1);
            pageProgress.setIndeterminate(false);
            pageProgress.setMaximum(totalPages);
            pageProgress.setValue(totalPages);
            pageProgress.setString("페이지 " + totalPages + "/" + totalPages + " 완료");
        });
    }

    private static void prepareDateDashboard(boolean dateMode, LocalDate startDate, LocalDate endDate) {
        SwingUtilities.invokeLater(() -> {
            if (dateMode) {
                lblDateRange.setText("날짜 진행: 탐색 중 " + startDate + " ~ " + endDate);
            } else {
                lblDateRange.setText("날짜 진행: 해당 없음");
            }
        });
    }

    private static void completeDateDashboard(boolean dateMode, List<String> collectedDays) {
        if (!dateMode) {
            return;
        }

        LocalDate oldest = null;
        LocalDate newest = null;
        for (String value : collectedDays) {
            if (value == null || value.length() < 10) {
                continue;
            }
            try {
                LocalDate date = LocalDate.parse(value.substring(0, 10));
                if (oldest == null || date.isBefore(oldest)) {
                    oldest = date;
                }
                if (newest == null || date.isAfter(newest)) {
                    newest = date;
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed legacy date rows while calculating the final display range.
            }
        }

        LocalDate finalOldest = oldest;
        LocalDate finalNewest = newest;
        SwingUtilities.invokeLater(() -> {
            if (finalOldest == null || finalNewest == null) {
                lblDateRange.setText("날짜 진행: 수집 데이터 없음");
            } else {
                lblDateRange.setText("날짜 진행: " + finalOldest + " ~ " + finalNewest + " (완료)");
            }
        });
    }

    private static void prepareCommentDashboard(int targets, boolean includeComments) {
        SwingUtilities.invokeLater(() -> {
            commentProgress.setIndeterminate(false);
            commentProgress.setMaximum(Math.max(1, targets));
            commentProgress.setValue(0);
            if (!includeComments) {
                commentProgress.setValue(1);
                commentProgress.setString("댓글 수집 건너뜀");
            } else if (targets == 0) {
                commentProgress.setValue(1);
                commentProgress.setString("댓글 대상 없음");
            } else {
                commentProgress.setString("댓글 0/" + targets);
            }
        });
    }

    private static void completeCommentDashboard(boolean parsed) {
        CollectionProgress.publishNow();
        SwingUtilities.invokeLater(() -> {
            if (parsed) {
                commentProgress.setValue(commentProgress.getMaximum());
                commentProgress.setString("댓글 대상 처리 완료");
            }
        });
    }

    private static void completeExecutionDashboard() {
        SwingUtilities.invokeLater(() -> {
            overallProgress.setIndeterminate(false);
            overallProgress.setForeground(new Color(22, 163, 74));
            overallProgress.setValue(overallProgress.getMaximum());
            overallProgress.setString("모든 작업 완료");
            lblStatus.setForeground(new Color(22, 120, 65));
        });
    }

    private static void failExecutionDashboard() {
        SwingUtilities.invokeLater(() -> {
            overallProgress.setIndeterminate(false);
            overallProgress.setForeground(new Color(190, 35, 45));
            overallProgress.setString("실행 실패");
            pageProgress.setIndeterminate(false);
            commentProgress.setIndeterminate(false);
        });
    }

    private static void updateCountLabel() {
        lblCounts.setText("누적: 글 " + collectedPosts + " | 댓글 " + collectedComments + " | 작성자 " + uniqueAuthors);
    }

    private static void updateCollectionDashboard(CollectionProgress.Snapshot snapshot) {
        SwingUtilities.invokeLater(() -> {
            collectedPosts = snapshot.posts();
            collectedComments = snapshot.comments();
            uniqueAuthors = snapshot.uniqueAuthors();
            updateCountLabel();
        });
    }

    private static void redirectSystemStreams() {
        OutputStream out = new OutputStream() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public void write(int b) {
                char c = (char) b;
                if (c == '\r' || c == '\n') {
                    flushBuffer();
                } else {
                    buffer.append(c);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) {
                String s = new String(b, off, len);
                String[] segments = s.split("\r", -1);
                for (int i = 0; i < segments.length; i++) {
                    String seg = segments[i];
                    if (i < segments.length - 1) {
                        buffer.append(seg);
                        flushBuffer();
                    } else {
                        String[] lines = seg.split("\n", -1);
                        for (int j = 0; j < lines.length; j++) {
                            buffer.append(lines[j]);
                            if (j < lines.length - 1) flushBuffer();
                        }
                    }
                }
            }

            private void flushBuffer() {
                String text = buffer.toString();
                buffer.setLength(0);
                if (!text.trim().isEmpty()) updateUI(text);
            }
        };
        PrintStream printStream = new PrintStream(out, true);
        System.setOut(printStream);
        System.setErr(printStream);
    }

    private static void updateUI(String text) {
        SwingUtilities.invokeLater(() -> {
            String clean = text.trim();
            if (clean.isEmpty()) return;

            appendLog(clean);
            if (clean.startsWith("페이지 진행:")) {
                lblPage.setText(clean);
                Matcher fraction = FRACTION_PATTERN.matcher(clean);
                if (fraction.find()) {
                    int done = Integer.parseInt(fraction.group(1));
                    int total = Integer.parseInt(fraction.group(2));
                    lblPage.setText("페이지 진행: " + done + "/" + total);
                    pageProgress.setIndeterminate(false);
                    pageProgress.setMaximum(Math.max(1, total));
                    pageProgress.setValue(Math.min(done, Math.max(1, total)));
                    pageProgress.setString("페이지 " + done + "/" + total);
                }
                Matcher dateRange = DATE_RANGE_PATTERN.matcher(clean);
                if (dateRange.find()) {
                    lblDateRange.setText("날짜 진행: " + dateRange.group(1) + " ~ " + dateRange.group(2));
                }
            } else if (clean.startsWith("진행:")) {
                Matcher fraction = FRACTION_PATTERN.matcher(clean);
                if (fraction.find()) {
                    int done = Integer.parseInt(fraction.group(1));
                    int total = Integer.parseInt(fraction.group(2));
                    lblComment.setText("댓글 진행: " + done + "/" + total);
                    commentProgress.setMaximum(Math.max(1, total));
                    commentProgress.setValue(Math.min(done, Math.max(1, total)));
                    commentProgress.setString("댓글 " + done + "/" + total);
                }

                Matcher speed = SPEED_PATTERN.matcher(clean);
                if (speed.find()) {
                    double rps = Double.parseDouble(speed.group(1));
                    lblSpeed.setText(String.format("현재 속도: %.2f r/s", rps));
                    speedChart.addSample(rps);
                }

                Matcher eta = ETA_PATTERN.matcher(clean);
                if (eta.find()) {
                    lblEta.setText("남은 시간: " + formatDuration(Long.parseLong(eta.group(1))));
                }
            }
        });
    }

    private static void appendLog(String text) {
        txtLog.append(text + System.lineSeparator());
        int excess = txtLog.getDocument().getLength() - MAX_LOG_CHARS;
        if (excess > 0) {
            txtLog.replaceRange("", 0, excess);
        }
        txtLog.setCaretPosition(txtLog.getDocument().getLength());
    }

    private static void clearLog() {
        SwingUtilities.invokeLater(() -> txtLog.setText(""));
    }

    private static void searchAndApplyGallery(Component parent, JTextField txtId, JComboBox<String> typeBox, JButton button) {
        String keyword = txtId.getText().trim();
        if (keyword.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "검색할 갤러리명 또는 ID를 입력하세요.");
            return;
        }

        button.setEnabled(false);
        new Thread(() -> {
            try {
                List<GalleryCandidate> candidates = searchGalleryCandidates(keyword);
                SwingUtilities.invokeLater(() -> {
                    if (candidates.isEmpty()) {
                        JOptionPane.showMessageDialog(parent, "일치하는 갤러리를 찾지 못했습니다.");
                        return;
                    }
                    if (candidates.size() == 1) {
                        GalleryCandidate selected = candidates.get(0);
                        txtId.setText(selected.id());
                        typeBox.setSelectedItem(selected.type());
                        System.out.println("Gallery selected: " + selected);
                        return;
                    }

                    GalleryCandidate selected = (GalleryCandidate) JOptionPane.showInputDialog(
                            parent,
                            "자동입력할 갤러리를 선택하세요.",
                            "갤러리 검색",
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            candidates.toArray(),
                            candidates.get(0)
                    );
                    if (selected != null) {
                        txtId.setText(selected.id());
                        typeBox.setSelectedItem(selected.type());
                        System.out.println("Gallery selected: " + selected);
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(parent, ex.getMessage(), "갤러리 검색 오류", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> button.setEnabled(true));
            }
        }, "gallery-search").start();
    }

    private static List<GalleryCandidate> searchGalleryCandidates(String keyword) throws IOException {
        LinkedHashMap<String, GalleryCandidate> results = new LinkedHashMap<>();

        for (String type : List.of("mini", "m", "main")) {
            tryAddExactGalleryCandidate(results, type, keyword);
        }

        String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        List<String> searchUrls = List.of(
                "https://search.dcinside.com/gall/q/" + encoded,
                "https://search.dcinside.com/gallery/q/" + encoded,
                "https://search.dcinside.com/combine/q/" + encoded
        );

        for (String searchUrl : searchUrls) {
            try {
                Document doc = fetchDcinsideDocument(searchUrl, "https://search.dcinside.com/");
                Elements links = doc.select(".integrate_cont_list a[href*=id=], .gallsch_result_all a.gallname_txt[href*=id=]");
                if (links.isEmpty()) {
                    links = doc.select(".integrate_cont a[href*=id=]");
                }
                for (Element link : links) {
                    parseGalleryLink(link.absUrl("href"), link.text())
                            .ifPresent(candidate -> addGalleryCandidate(results, candidate));
                    if (results.size() >= 30) break;
                }
            } catch (IOException ignored) {
                // Some DCInside search routes intermittently fail. Other routes and exact-id probes are still useful.
            }
            if (results.size() >= 30) break;
        }

        List<GalleryCandidate> candidates = new ArrayList<>(results.values());
        if (candidates.size() > 30) {
            return new ArrayList<>(candidates.subList(0, 30));
        }
        return candidates;
    }

    private static void tryAddExactGalleryCandidate(Map<String, GalleryCandidate> results, String type, String id) {
        try {
            String title = fetchGalleryTitle(type, id);
            if (!title.isBlank()) {
                addGalleryCandidate(results, new GalleryCandidate(type, id, title));
            }
        } catch (IOException ignored) {
            // Not a valid gallery for this type.
        }
    }

    private static Optional<GalleryCandidate> parseGalleryLink(String url, String label) {
        if (url == null || url.isBlank() || !url.contains("gall.dcinside.com")) {
            return Optional.empty();
        }
        if (!url.contains("/lists/")) {
            return Optional.empty();
        }

        String id = queryParam(url, "id");
        String type = typeFromGalleryUrl(url);
        if (id == null || id.isBlank() || type == null) {
            return Optional.empty();
        }

        String name = cleanGalleryTitle(label);
        if (name.isBlank()) {
            name = id;
        }
        return Optional.of(new GalleryCandidate(type, id, name));
    }

    private static void addGalleryCandidate(Map<String, GalleryCandidate> results, GalleryCandidate candidate) {
        results.putIfAbsent(candidate.type() + ":" + candidate.id(), candidate);
    }

    private static String fetchGalleryTitle(String type, String id) throws IOException {
        Document doc = fetchDcinsideDocument(galleryListUrl(type, id), "https://gall.dcinside.com/");
        Element ogTitle = doc.selectFirst("meta[property=og:title]");
        String title = ogTitle != null ? ogTitle.attr("content") : doc.title();
        return cleanGalleryTitle(title);
    }

    private static Document fetchDcinsideDocument(String url, String referer) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cache-Control", "no-cache")
                .header("Referer", referer)
                .timeout(15000)
                .get();
    }

    private static String galleryListUrl(String type, String id) {
        String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
        return switch (type) {
            case "mini" -> "https://gall.dcinside.com/mini/board/lists/?id=" + encodedId;
            case "m" -> "https://gall.dcinside.com/mgallery/board/lists/?id=" + encodedId;
            default -> "https://gall.dcinside.com/board/lists/?id=" + encodedId;
        };
    }

    private static String typeFromGalleryUrl(String url) {
        if (url.contains("/mini/")) return "mini";
        if (url.contains("/mgallery/")) return "m";
        if (url.contains("/board/")) return "main";
        return null;
    }

    private static String queryParam(String url, String name) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart == url.length() - 1) {
            return null;
        }
        String query = url.substring(queryStart + 1);
        int fragmentStart = query.indexOf('#');
        if (fragmentStart >= 0) {
            query = query.substring(0, fragmentStart);
        }

        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            String key = equals >= 0 ? part.substring(0, equals) : part;
            if (!name.equals(key)) continue;
            String value = equals >= 0 ? part.substring(equals + 1) : "";
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String cleanGalleryTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        text = text.replace(" - 커뮤니티 포털 디시인사이드", "");
        text = text.replace(" - dc official App", "");
        text = text.replace("ⓜ", "").replace("ⓝ", "");
        return text.trim();
    }

    private static final class SpeedChartPanel extends JPanel {
        private static final int MAX_SAMPLES = 60;
        private final Deque<Double> samples = new ArrayDeque<>();

        private SpeedChartPanel() {
            setPreferredSize(new Dimension(0, 105));
            setMinimumSize(new Dimension(240, 90));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(new Color(210, 214, 220)));
        }

        private void addSample(double value) {
            if (!Double.isFinite(value) || value < 0) {
                return;
            }
            if (samples.size() >= MAX_SAMPLES) {
                samples.removeFirst();
            }
            samples.addLast(value);
            repaint();
        }

        private void reset() {
            samples.clear();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int left = 10;
                int right = Math.max(left + 1, getWidth() - 10);
                int top = 20;
                int bottom = Math.max(top + 1, getHeight() - 10);

                g2.setColor(new Color(232, 235, 239));
                for (int i = 0; i <= 3; i++) {
                    int y = top + (bottom - top) * i / 3;
                    g2.drawLine(left, y, right, y);
                }

                if (samples.isEmpty()) {
                    g2.setColor(new Color(110, 118, 128));
                    g2.drawString("처리속도 데이터 대기 중", left, top + 18);
                    return;
                }

                List<Double> snapshot = new ArrayList<>(samples);
                double maxValue = Math.max(1.0, snapshot.stream().mapToDouble(Double::doubleValue).max().orElse(1.0));
                g2.setColor(new Color(70, 78, 90));
                g2.drawString(String.format("최대 %.2f r/s", maxValue), left, 14);

                g2.setColor(new Color(14, 116, 144));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int previousX = left;
                int previousY = bottom;
                for (int i = 0; i < snapshot.size(); i++) {
                    int x = snapshot.size() == 1
                            ? right
                            : left + (right - left) * i / (snapshot.size() - 1);
                    int y = bottom - (int) Math.round((bottom - top) * snapshot.get(i) / maxValue);
                    if (i > 0) {
                        g2.drawLine(previousX, previousY, x, y);
                    }
                    g2.fillOval(x - 2, y - 2, 4, 4);
                    previousX = x;
                    previousY = y;
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private record GalleryCandidate(String type, String id, String name) {
        @Override
        public String toString() {
            return String.format("%s  [id=%s, type=%s]", name, id, type);
        }
    }

    private record PostProcessScript(String fileName, ScriptInputProvider inputProvider) {
    }

    @FunctionalInterface
    private interface ScriptInputProvider {
        List<String> getInput(Component parent);
    }
}
