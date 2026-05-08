import javax.swing.*;
import java.awt.*;
import java.io.OutputStream;
import java.io.PrintStream;
import crawler.*;
import counter.CustomAnalyzer;
import java.util.*;

public class Main {
    private static JLabel lblStatus  = new JLabel("상태: 대기 중");
    private static JLabel lblPage    = new JLabel("페이지 진행: -");
    private static JLabel lblComment = new JLabel("댓글 진행: -");
    private static JLabel lblSpeed   = new JLabel("현재 속도: -");
    private static JLabel lblEta     = new JLabel("남은 시간: -");

    public static void main(String[] args) {
        JFrame frame = new JFrame("Readersen");
        frame.setSize(450, 560);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(15, 15));

        JPanel inputPanel = new JPanel(new GridLayout(7, 2, 5, 5));

        JTextField txtId = new JTextField("");
        JTextField txtStart = new JTextField("1");
        JTextField txtEnd = new JTextField("1");

        JComboBox<String> typeBox = new JComboBox<>();
        typeBox.addItem("main");
        typeBox.addItem("m");
        typeBox.addItem("mini");
        typeBox.setSelectedItem("m");

        JComboBox<Integer> threadBox = new JComboBox<>();
        threadBox.addItem(1);
        for (int i = 2; i <= 120; i += 2) threadBox.addItem(i);
        threadBox.setSelectedItem(30);

        JButton btnRun = new JButton("분석 시작");

        inputPanel.add(new JLabel(" TYPE:")); inputPanel.add(typeBox);
        inputPanel.add(new JLabel(" 갤러리 ID:")); inputPanel.add(txtId);
        inputPanel.add(new JLabel(" 시작 페이지:")); inputPanel.add(txtStart);
        inputPanel.add(new JLabel(" 종료 페이지:")); inputPanel.add(txtEnd);
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

        redirectSystemStreams();

        btnRun.addActionListener(e -> {
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
                    int start = Integer.parseInt(txtStart.getText().trim());
                    int end = Integer.parseInt(txtEnd.getText().trim());
                    int threads = (int) threadBox.getSelectedItem();

                    if (id.isEmpty()) {
                        throw new IllegalArgumentException("갤러리 ID를 입력하세요.");
                    }
                    if (start <= 0 || end < start) {
                        throw new IllegalArgumentException("페이지 범위가 올바르지 않습니다.");
                    }

                    long startTime = System.currentTimeMillis();

                    System.out.println("===== 실행 설정 =====");
                    System.out.println("TYPE: " + type);
                    System.out.println("ID: " + id);
                    System.out.println("Threads: " + threads);

                    lblStatus.setText("상태: [1/3] 페이지 목록 수집 중...");
                    CrawlerResult result = page_parser.Crawler(id, type, start, end, 60);
                    lblPage.setText("페이지 진행: 수집 완료");

                    lblStatus.setText("상태: [2/3] 댓글 데이터 파싱 중...");
                    subResult commsub = comment_Parser.geulp(id, type, result.RepleTrueBox, threads);
                    lblComment.setText("댓글 진행: 파싱 완료");
                    lblSpeed.setText("현재 속도: -");
                    lblEta.setText("남은 시간: -");

                    lblStatus.setText("상태: [3/3] 최종 결과 분석 중...");
                    CustomAnalyzer analyzer = new CustomAnalyzer();
                    analyzer.analyzeData(
                            result.authorBox, result.IDBox, result.IpBox,
                            result.viewBox, result.recomBox, result.repleBox);
                    analyzer.applyCommList(commsub.Names, commsub.IDs, commsub.Ips);
                    analyzer.printSummary("example.txt");
                    analyzer.textprinter(result.DayBox, "date-data.txt");
                    analyzer.textprinter(commsub.Contents, "contents_data.txt");
                    analyzer.saveUserLog(result.authorBox, result.IDBox, result.IpBox, result.DayBox, "daily-data.txt");
                    analyzer.textprinter(commsub.Days, "date-data-comment.txt");
                    analyzer.saveUserLog(commsub.Names, commsub.IDs, commsub.Ips, commsub.Days, "daily-data-comment.txt");

                    System.out.println("총소요: " + (System.currentTimeMillis() - startTime) / 1000 + "초");
                    System.out.println("대상 타겟: " + result.RepleTrueBox.size() + "/" + result.IDBox.size() + "개");

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
        frame.add(statusPanel, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
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
        System.setOut(new PrintStream(out, true));
    }

    private static void updateUI(String text) {
        SwingUtilities.invokeLater(() -> {
            String clean = text.trim();
            if (clean.isEmpty()) return;

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
}
