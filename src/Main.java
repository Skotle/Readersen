import javax.swing.*;
import java.awt.*;
import java.io.OutputStream;
import java.io.PrintStream;
import crawler.*;
import counter.CustomAnalyzer;
import java.util.*;

public class Main{
    public static void main(String[] args) throws InterruptedException{
        Scanner sc = new Scanner(System.in);
        System.out.print("TYPE:");
        String TYPE = sc.next();
        System.out.print("ID:");
        String ID = sc.next();
        System.out.print("시작 페이지:");
        int start_page = sc.nextInt();
        System.out.print("종료 페이지:");
        int end_page = sc.nextInt();
        long start = System.currentTimeMillis();
        CrawlerResult result = page_parser.Crawler(ID, TYPE,start_page,end_page,60);
        ArrayList<String> gall_nums = result.RepleTrueBox;
        long breakstart = System.currentTimeMillis();
        Scanner scanner = new Scanner(System.in);
        System.out.println("대상 타겟 : "+gall_nums.size());
        System.out.println("잠시 대기 . . .  실행하려면 속도 입력");
        int setspeed = scanner.nextInt();
        long breakend = System.currentTimeMillis();
        subResult commsub = comment_Parser.geulp(ID, TYPE, gall_nums, setspeed); //120 권장
        ArrayList<String> comment_names = commsub.Names;
        ArrayList<String> comment_uids = commsub.IDs;
        ArrayList<String> comment_ips = commsub.Ips;
        ArrayList<String> comment_days = commsub.Days;

        System.out.println(comment_days);
        ArrayList<String> Authors = result.authorBox;
        // System.out.println(Authors);
        // System.out.println(comment_name);
        ArrayList<String> UIDs = result.IDBox;
        ArrayList<String> Ips = result.IpBox;
        ArrayList<Integer> views = result.viewBox;
        ArrayList<Integer> recoms = result.recomBox;
        ArrayList<Integer> reples = result.repleBox;
        ArrayList<String> days = result.DayBox;
        CustomAnalyzer analyzer = new CustomAnalyzer();
        analyzer.analyzeData(Authors,UIDs,Ips, views, recoms, reples);
        analyzer.applyCommList(comment_names,comment_uids,comment_ips);
        analyzer.printSummary("example.txt");
        analyzer.dayprinter(days,"date-data.txt");
        analyzer.saveUserLog(Authors,UIDs,Ips,days,"daily-data.txt");
        System.out.println(comment_days);
        analyzer.dayprinter(comment_days,"date-data-comment.txt");
        analyzer.saveUserLog(comment_names,comment_uids,comment_ips,comment_days,"daily-data-comment.txt");
        System.out.println("총소요: "+(System.currentTimeMillis()-start-(breakend-breakstart))/1000+"초");
        System.out.println("대상 타겟: "+gall_nums.size()+"/"+UIDs.size() +"개");
    }
    private static void checkMissing(String label, ArrayList<String> list) {
        for (int i = 0; i < list.size(); i++) {
            String val = list.get(i);
            if (val == null || val.isEmpty()) {
                System.out.printf("[%s] 인덱스 %d : 결측값%n", label, i);
            }
        }
    }
}

//public class Main {
//    private static JLabel lblStatus  = new JLabel("상태: 대기 중");
//    private static JLabel lblPage    = new JLabel("페이지 진행: -");
//    private static JLabel lblComment = new JLabel("댓글 진행: -");
//    private static JLabel lblSpeed   = new JLabel("현재 속도: -");
//    private static JLabel lblEta     = new JLabel("남은 시간: -");   // 남은 시간 전용 라벨 추가
//
//    public static void main(String[] args) {
//        JFrame frame = new JFrame("Readersen");
//        frame.setSize(450, 560);
//        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        frame.setLayout(new BorderLayout(15, 15));
//
//        // ===== 입력 패널 =====
//        JPanel inputPanel = new JPanel(new GridLayout(7, 2, 5, 5));
//
//        JTextField txtType       = new JTextField("m");
//        JTextField txtId         = new JTextField("");
//        JTextField txtStart      = new JTextField("1");
//        JTextField txtEnd        = new JTextField("1");
//        JTextField txtSpeedInput = new JTextField("120");
//
//        JComboBox<String> typeBox = new JComboBox<>();
//        typeBox.addItem("main");
//        typeBox.addItem("m");
//        typeBox.addItem("mini");
//        JComboBox<Integer> threadBox = new JComboBox<>();
//        threadBox.addItem(1);
//        for (int i =2; i <= 16; i += 2) threadBox.addItem(i);
//        threadBox.setSelectedItem(30);
//
//        JButton btnRun = new JButton("분석 시작");
//
//        inputPanel.add(new JLabel(" TYPE:")); inputPanel.add(typeBox);
//        inputPanel.add(new JLabel(" 갤러리 ID:"));          inputPanel.add(txtId);
//        inputPanel.add(new JLabel(" 시작 페이지:"));        inputPanel.add(txtStart);
//        inputPanel.add(new JLabel(" 종료 페이지:"));        inputPanel.add(txtEnd);
//        inputPanel.add(new JLabel(" 스레드 수:"));          inputPanel.add(threadBox);
//        inputPanel.add(new JLabel(" 작업 실행:"));          inputPanel.add(btnRun);
//
//        // ===== 상태 패널 (5행으로 확장) =====
//        JPanel statusPanel = new JPanel(new GridLayout(5, 1, 10, 10));
//        statusPanel.setBorder(BorderFactory.createTitledBorder("실시간 현황"));
//
//        Font boldFont = new Font("맑은 고딕", Font.BOLD, 15);
//
//        lblStatus.setFont(boldFont);
//        lblStatus.setForeground(Color.BLUE);
//
//        lblPage.setFont(boldFont);
//        lblComment.setFont(boldFont);
//
//        lblSpeed.setFont(boldFont);
//        lblSpeed.setForeground(new Color(0, 120, 0));
//
//        lblEta.setFont(boldFont);
//        lblEta.setForeground(new Color(150, 80, 0));
//
//        statusPanel.add(lblStatus);
//        statusPanel.add(lblPage);
//        statusPanel.add(lblComment);
//        statusPanel.add(lblSpeed);
//        statusPanel.add(lblEta);
//
//        redirectSystemStreams();
//
//        // ===== 실행 버튼 =====
//        btnRun.addActionListener(e -> {
//            new Thread(() -> {
//                try {
//                    btnRun.setEnabled(false);
//
//                    lblPage.setText("페이지 진행: 대기 중...");
//                    lblComment.setText("댓글 진행: 대기 중...");
//                    lblSpeed.setText("현재 속도: -");
//                    lblEta.setText("남은 시간: -");
//
//                    String type   = (String)typeBox.getSelectedItem();
//                    String id     = txtId.getText().trim();
//                    int    start  = Integer.parseInt(txtStart.getText());
//                    int    end    = Integer.parseInt(txtEnd.getText());
//                    int    speed  = Integer.parseInt(txtSpeedInput.getText());
//                    int    threads = (int) threadBox.getSelectedItem();
//
//                    System.out.println("===== 실행 설정 =====");
//                    System.out.println("Threads: " + threads);
//                    System.out.println("Speed: " + speed);
//
//                    lblStatus.setText("상태: [1/3] 페이지 목록 수집 중...");
//                    CrawlerResult result = page_parser.Crawler(id, type, start, end, 60);
//                    lblPage.setText("페이지 진행: 수집 완료 ✔");
//
//                    lblStatus.setText("상태: [2/3] 댓글 데이터 파싱 중...");
//                    subResult commsub = comment_Parser.geulp(id, type, result.RepleTrueBox, threads);
//                    lblComment.setText("댓글 진행: 파싱 완료 ✔");
//                    lblSpeed.setText("현재 속도: -");
//                    lblEta.setText("남은 시간: -");
//
//                    lblStatus.setText("상태: [3/3] 최종 결과 분석 중...");
//                    CustomAnalyzer analyzer = new CustomAnalyzer();
//                    analyzer.analyzeData(
//                            result.authorBox, result.IDBox, result.IpBox,
//                            result.viewBox,   result.recomBox, result.repleBox);
//                    analyzer.applyCommList(commsub.Names, commsub.IDs, commsub.Ips);
//                    analyzer.printSummary("result.json");
//
//                    lblStatus.setText("상태: 모든 작업 완료");
//                    lblStatus.setText("");
//                    JOptionPane.showMessageDialog(frame, "분석 완료! result.json 확인");
//                    ArrayList<String> days = result.DayBox;
//                    analyzer.dayprinter(days);
//
//                } catch (Exception ex) {
//                    lblStatus.setText("상태: 에러 발생!");
//                    lblStatus.setForeground(Color.RED);
//                    ex.printStackTrace();
//                } finally {
//                    btnRun.setEnabled(true);
//                }
//
//            }).start();
//        });
//
//        frame.add(inputPanel,  BorderLayout.NORTH);
//        frame.add(statusPanel, BorderLayout.CENTER);
//        frame.setLocationRelativeTo(null);
//        frame.setVisible(true);
//
//    }
//
//    // ===== 콘솔 출력 → UI =====
//    private static void redirectSystemStreams() {
//        OutputStream out = new OutputStream() {
//            private final StringBuilder buffer = new StringBuilder();
//
//            @Override
//            public void write(int b) {
//                char c = (char) b;
//                if (c == '\r') {
//                    // \r 수신 시 즉시 파싱 (덮어쓰기 방식 진행 출력)
//                    flushBuffer();
//                } else if (c == '\n') {
//                    flushBuffer();
//                } else {
//                    buffer.append(c);
//                }
//            }
//
//            @Override
//            public void write(byte[] b, int off, int len) {
//                String s = new String(b, off, len);
//                // \r 기준으로 분리해서 마지막 세그먼트만 유효한 내용으로 처리
//                String[] segments = s.split("\r", -1);
//                for (int i = 0; i < segments.length; i++) {
//                    String seg = segments[i];
//                    if (i < segments.length - 1) {
//                        // \r 앞 세그먼트: 버퍼에 붙이고 즉시 flush (덮어쓰기)
//                        buffer.append(seg);
//                        flushBuffer();
//                    } else {
//                        // 마지막 세그먼트: \n 확인 후 처리
//                        String[] lines = seg.split("\n", -1);
//                        for (int j = 0; j < lines.length; j++) {
//                            buffer.append(lines[j]);
//                            if (j < lines.length - 1) flushBuffer();
//                        }
//                    }
//                }
//            }
//
//            private void flushBuffer() {
//                String text = buffer.toString();
//                buffer.setLength(0);
//                if (!text.trim().isEmpty()) updateUI(text);
//            }
//        };
//        System.setOut(new PrintStream(out, true));
//    }
//
//    // 출력 형식: "\r진행: %d/%d 글(%.2f%s), 평균 속도: %.2f r/s, 남은 시간 %.2f초"
//    private static void updateUI(String text) {
//        SwingUtilities.invokeLater(() -> {
//            String clean = text.trim();
//            if (clean.isEmpty()) return;
//
//            if (clean.startsWith("페이지 진행:")) {
//                lblPage.setText(clean);
//
//            } else if (clean.startsWith("진행:")) {
//                // 예: "진행: 50/200 글(25.00%), 평균 속도: 18.50 r/s, 남은 시간 8.11초"
//                // 쉼표 기준 분리
//                String[] parts = clean.split(", ");
//
//                // parts[0] = "진행: 50/200 글(25.00%)"
//                lblComment.setText(parts[0].trim());
//
//                // parts[1] = "평균 속도: 18.50 r/s"
//                if (parts.length > 1) {
//                    lblSpeed.setText(parts[1].trim());
//                }
//
//                // parts[2] = "남은 시간 8.11초"
//                if (parts.length > 2) {
//                    lblEta.setText(parts[2].trim());
//                }
//            }
//        });
//    }
//}