import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import crawler.*;
import counter.CustomAnalyzer;
import java.util.*;
import java.util.List;

public class Main {
    private static JLabel lblStatus  = new JLabel("상태: 대기 중");
    private static JLabel lblPage    = new JLabel("페이지 진행: -");
    private static JLabel lblComment = new JLabel("댓글 진행: -");
    private static JLabel lblSpeed   = new JLabel("현재 속도: -");
    private static JLabel lblEta     = new JLabel("남은 시간: -");
    private static final JTextArea txtLog = new JTextArea();
    private static final int MAX_LOG_CHARS = 200_000;

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
        frame.setSize(760, 720);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(15, 15));

        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 5, 5));

        JTextField txtId = new JTextField("");
        JTextField txtStart = new JTextField("1");
        JTextField txtEnd = new JTextField("1");
        JLabel lblStartInput = new JLabel(" Start page:");
        JLabel lblEndInput = new JLabel(" End page:");

        JComboBox<String> rangeModeBox = new JComboBox<>();
        rangeModeBox.addItem("PAGE");
        rangeModeBox.addItem("DATE");
        rangeModeBox.setSelectedItem("PAGE");
        JTextField txtStartDate = new JTextField(LocalDate.now().minusDays(7).toString());
        JTextField txtEndDate = new JTextField(LocalDate.now().toString());

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

        inputPanel.add(new JLabel(" TYPE:")); inputPanel.add(typeBox);
        inputPanel.add(new JLabel(" Range mode:")); inputPanel.add(rangeModeBox);
        inputPanel.add(new JLabel(" Start date:")); inputPanel.add(txtStartDate);
        inputPanel.add(new JLabel(" End date:")); inputPanel.add(txtEndDate);
        inputPanel.add(new JLabel(" 갤러리 ID:")); inputPanel.add(txtId);
        inputPanel.add(new JLabel(" 시작 페이지:")); inputPanel.add(txtStart);
        inputPanel.add(new JLabel(" 종료 페이지:")); inputPanel.add(txtEnd);
        inputPanel.add(new JLabel(" 실행 범위:")); inputPanel.add(runScopeBox);
        inputPanel.add(new JLabel(" 댓글 스레드 수:")); inputPanel.add(threadBox);
        inputPanel.add(new JLabel(" 작업 실행:")); inputPanel.add(btnRun);

        JPanel statusPanel = new JPanel(new GridLayout(5, 1, 10, 10));
        statusPanel.setBorder(BorderFactory.createTitledBorder("실시간 현황"));

        Font boldFont = new Font("맑은 고딕", Font.BOLD, 15);

        lblStatus.setFont(boldFont);
        lblStatus.setForeground(Color.BLUE);
        lblPage.setFont(boldFont);
        lblComment.setFont(boldFont);
        lblSpeed.setFont(boldFont);
        lblSpeed.setForeground(new Color(0, 120, 0));
        lblEta.setFont(boldFont);
        lblEta.setForeground(new Color(150, 80, 0));

        statusPanel.add(lblStatus);
        statusPanel.add(lblPage);
        statusPanel.add(lblComment);
        statusPanel.add(lblSpeed);
        statusPanel.add(lblEta);

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

        redirectSystemStreams();

        runScopeBox.addActionListener(e -> {
            boolean includeComments = "댓글까지 진행".equals(runScopeBox.getSelectedItem());
            threadBox.setEnabled(includeComments);
        });

        rangeModeBox.addActionListener(e -> {
            boolean dateMode = "DATE".equals(rangeModeBox.getSelectedItem());
            txtStart.setEnabled(!dateMode);
            txtEnd.setEnabled(!dateMode);
            txtStartDate.setEnabled(dateMode);
            txtEndDate.setEnabled(dateMode);
        });
        txtStartDate.setEnabled(false);
        txtEndDate.setEnabled(false);

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
                    int start = 0;
                    int end = 0;
                    LocalDate startDate = null;
                    LocalDate endDate = null;
                    if (dateMode) {
                        startDate = LocalDate.parse(txtStartDate.getText().trim());
                        endDate = LocalDate.parse(txtEndDate.getText().trim());
                    } else {
                        start = Integer.parseInt(txtStart.getText().trim());
                        end = Integer.parseInt(txtEnd.getText().trim());
                    }
                    int threads = (int) threadBox.getSelectedItem();
                    boolean includeComments = "댓글까지 진행".equals(runScopeBox.getSelectedItem());

                    if (id.isEmpty()) {
                        throw new IllegalArgumentException("갤러리 ID를 입력하세요.");
                    }
                    if (!dateMode && (start <= 0 || end < start)) {
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
                    } else {
                        System.out.println("Page range: " + start + " ~ " + end);
                    }
                    System.out.println("Threads: " + threads);
                    System.out.println("Scope: " + (includeComments ? "페이지+댓글" : "페이지만"));
                    Path runDir = dateMode
                            ? createDateRunDirectory(id, type, startDate, endDate, includeComments)
                            : createRunDirectory(id, type, start, end, includeComments);
                    System.out.println("Output: " + runDir.toAbsolutePath());

                    lblStatus.setText("상태: [1/3] 페이지 목록 수집 중...");
                    CrawlerResult result = dateMode
                            ? page_parser.CrawlerByDate(id, type, startDate, endDate, 60)
                            : page_parser.Crawler(id, type, start, end, 60);
                    System.out.println("Detected pages: " + result.startPage + " ~ " + result.endPage);
                    lblPage.setText("페이지 진행: 수집 완료");

                    if (!result.gallType.isBlank() && !result.gallType.equals(type)) {
                        type = result.gallType;
                        System.out.println("댓글 요청 TYPE도 보정된 값으로 변경: " + type);
                    }

                    lblStatus.setText("상태: [2/3] 댓글 데이터 파싱 중...");
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

                    lblStatus.setText("상태: [3/3] 최종 결과 분석 중...");
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
                    JOptionPane.showMessageDialog(frame, "분석 완료! 결과 파일을 확인하세요.");
                } catch (Exception ex) {
                    lblStatus.setText("상태: 에러 발생!");
                    lblStatus.setForeground(Color.RED);
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(frame, ex.getMessage(), "에러", JOptionPane.ERROR_MESSAGE);
                } finally {
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
                "input_start_page=" + ("DATE".equals(rangeMode) ? "" : start),
                "input_end_page=" + ("DATE".equals(rangeMode) ? "" : end),
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
            } else if (clean.startsWith("진행:")) {
                String[] parts = clean.split(", ");
                lblComment.setText(parts[0].trim());
                if (parts.length > 1) {
                    lblSpeed.setText(parts[1].trim());
                }
                if (parts.length > 2) {
                    lblEta.setText(parts[2].trim());
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

    private record PostProcessScript(String fileName, ScriptInputProvider inputProvider) {
    }

    @FunctionalInterface
    private interface ScriptInputProvider {
        List<String> getInput(Component parent);
    }
}
