import javax.swing.*;
import java.awt.*;
import java.io.OutputStream;
import java.io.PrintStream;
import crawler.*;
import counter.CustomAnalyzer;

public class Main {
    private static JLabel lblStatus = new JLabel("상태: 대기 중");
    private static JLabel lblPage = new JLabel("페이지 진행: -");
    private static JLabel lblComment = new JLabel("댓글 진행: -");
    private static JLabel lblSpeed = new JLabel("현재 속도: -");

    public static void main(String[] args) {
        JFrame frame = new JFrame("Readersen");
        frame.setSize(450, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(15, 15));

        // 입력 패널
        JPanel inputPanel = new JPanel(new GridLayout(6, 2, 5, 5));
        JTextField txtType = new JTextField("m");
        JTextField txtId = new JTextField("");
        JTextField txtStart = new JTextField("1");
        JTextField txtEnd = new JTextField("1");
        JTextField txtSpeedInput = new JTextField("120");
        JButton btnRun = new JButton("분석 시작");

        inputPanel.add(new JLabel(" TYPE (gall/m/mini):")); inputPanel.add(txtType);
        inputPanel.add(new JLabel(" 갤러리 ID:")); inputPanel.add(txtId);
        inputPanel.add(new JLabel(" 시작 페이지:")); inputPanel.add(txtStart);
        inputPanel.add(new JLabel(" 종료 페이지:")); inputPanel.add(txtEnd);
        inputPanel.add(new JLabel(" 파싱 속도:")); inputPanel.add(txtSpeedInput);
        inputPanel.add(new JLabel(" 작업 실행:")); inputPanel.add(btnRun);

        // 상태 표시 패널
        JPanel statusPanel = new JPanel(new GridLayout(4, 1, 10, 10));
        statusPanel.setBorder(BorderFactory.createTitledBorder("실시간 현황"));
        Font boldFont = new Font("맑은 고딕", Font.BOLD, 15);
        lblStatus.setFont(boldFont); lblStatus.setForeground(Color.BLUE);
        lblPage.setFont(boldFont); lblComment.setFont(boldFont);
        lblSpeed.setFont(boldFont); lblSpeed.setForeground(new Color(0, 120, 0));

        statusPanel.add(lblStatus); statusPanel.add(lblPage);
        statusPanel.add(lblComment); statusPanel.add(lblSpeed);

        redirectSystemStreams();

        btnRun.addActionListener(e -> {
            new Thread(() -> {
                try {
                    btnRun.setEnabled(false);
                    lblPage.setText("페이지 진행: 대기 중...");
                    lblComment.setText("댓글 진행: 대기 중...");

                    String type = txtType.getText().trim();
                    String id = txtId.getText().trim();
                    int start = Integer.parseInt(txtStart.getText());
                    int end = Integer.parseInt(txtEnd.getText());
                    int speed = Integer.parseInt(txtSpeedInput.getText());

                    // --- 1구간: 페이지 목록 수집 ---
                    lblStatus.setText("상태: [1/3] 페이지 목록 수집 중...");
                    CrawlerResult result = page_parser.Crawler(id, type, start, end, 60);
                    lblPage.setText("페이지 진행: 수집 완료 ✔");

                    // --- 2구간: 댓글 수집 ---
                    lblStatus.setText("상태: [2/3] 댓글 데이터 파싱 중...");
                    subResult commsub = comment_Parser.geulp(id, type, result.RepleTrueBox, speed);
                    lblComment.setText("댓글 진행: 파싱 완료 ✔");

                    // --- 3구간: 분석 및 저장 ---
                    lblStatus.setText("상태: [3/3] 최종 결과 분석 중...");
                    CustomAnalyzer analyzer = new CustomAnalyzer();
                    analyzer.analyzeData(result.authorBox, result.IDBox, result.IpBox,
                            result.viewBox, result.recomBox, result.repleBox);
                    analyzer.applyCommList(commsub.Names, commsub.IDs, commsub.Ips);
                    analyzer.printSummary("result.json");

                    lblStatus.setText("상태: 모든 작업 완료");
                    JOptionPane.showMessageDialog(frame, "분석 완료! result.txt를 확인하세요.");
                } catch (Exception ex) {
                    lblStatus.setText("상태: 에러 발생!");
                    lblStatus.setForeground(Color.RED);
                    ex.printStackTrace();
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
                buffer.append(c);
                if (c == '\n' || c == '\r') {
                    updateUI(buffer.toString());
                    buffer.setLength(0);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) {
                String s = new String(b, off, len);
                buffer.append(s);
                if (s.contains("\n") || s.contains("\r")) {
                    updateUI(buffer.toString());
                    buffer.setLength(0);
                }
            }
        };
        System.setOut(new PrintStream(out, true));
    }

    private static void updateUI(String text) {
        SwingUtilities.invokeLater(() -> {
            String cleanText = text.replace("\r", "").trim();
            if (cleanText.isEmpty()) return;

            if (cleanText.startsWith("페이지 진행:")) {
                lblPage.setText(cleanText);
            } else if (cleanText.startsWith("진행:")) {
                // "진행: 10/100 글(10.00%), 평균 속도: 5.00 r/s, 남은 시간 12.34초"
                String[] parts = cleanText.split(", ");
                lblComment.setText(parts[0]); // "진행: 10/100 글(10.00%)"
                if (parts.length > 1) {
                    lblSpeed.setText(parts[1]); // "평균 속도: 5.00 r/s"
                    // parts[2]는 남은 시간 — 필요하면 별도 라벨로 표시 가능
                }
            }
        });
    }
}