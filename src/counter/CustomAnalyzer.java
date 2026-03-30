package counter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CustomAnalyzer {
    private ArrayList<CustomClass> classes = new ArrayList<>();

    // 이름에서 타입(고정/비고정/유동)을 분리하는 보조 메서드
    private String[] parseNick(String rawName) {
        if (rawName == null) return new String[]{"", ""};
        if (rawName.startsWith("고정")) return new String[]{rawName.substring(2), "고정"};
        if (rawName.startsWith("비고정")) return new String[]{rawName.substring(3), "비고정"};
        if (rawName.startsWith("유동")) return new String[]{rawName.substring(2), "비회원"};
        return new String[]{rawName, ""};
    }

    // 글 데이터 분석 (기존 로직 유지)
    public void analyzeData(ArrayList<String> names, ArrayList<String> userIDs, ArrayList<String> ips,
                            ArrayList<Integer> views, ArrayList<Integer> recoms, ArrayList<Integer> reples) {
        int size = names.size();
        for (int i = 0; i < size; i++) {
            String[] parsed = parseNick(names.get(i));
            String name = parsed[0];
            String nType = parsed[1];

            String uid = (i < userIDs.size() && userIDs.get(i) != null) ? userIDs.get(i) : "";
            String ip = (i < ips.size() && ips.get(i) != null) ? ips.get(i) : "";
            int v = (i < views.size()) ? views.get(i) : 0;
            int r = (i < recoms.size()) ? recoms.get(i) : 0;
            int rp = (i < reples.size()) ? reples.get(i) : 0;

            boolean found = false;
            for (CustomClass cc : classes) {
                if (!uid.isEmpty()) {
                    if (!cc.userID.isEmpty() && cc.userID.equals(uid)) {
                        cc.addMember(v, r, rp);
                        found = true;
                        break;
                    }
                } else if (cc.userID.isEmpty()) {
                    if (("ㅇㅇ".equals(name) && cc.ip.equals(ip)) || (cc.name.equals(name) && cc.ip.equals(ip))) {
                        cc.addMember(v, r, rp);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                CustomClass newClass = new CustomClass(name, uid, ip, nType);
                newClass.view = v; newClass.recom = r; newClass.reple = rp;
                classes.add(newClass);
            }
        }
    }

    // 댓글 데이터 분석 (NullPointerException 해결 지점)
    public void applyCommList(ArrayList<String> names, ArrayList<String> userIDs, ArrayList<String> ips) {
        if (names == null) return;
        for (int i = 0; i < names.size(); i++) {
            String[] parsed = parseNick(names.get(i));
            String name = parsed[0];
            String nType = parsed[1];

            // [에러 해결] 리스트 인덱스 범위 체크 및 null 체크 추가
            String uid = (userIDs != null && i < userIDs.size() && userIDs.get(i) != null) ? userIDs.get(i) : "";
            String ip = (ips != null && i < ips.size() && ips.get(i) != null) ? ips.get(i) : "";

            boolean found = false;
            for (CustomClass cc : classes) {
                boolean match = false;
                if (!uid.isEmpty() && !cc.userID.isEmpty() && cc.userID.equals(uid)) {
                    match = true;
                } else if (uid.isEmpty() && cc.userID.isEmpty()) {
                    if (("ㅇㅇ".equals(name) && cc.ip.equals(ip)) || (cc.name.equals(name) && cc.ip.equals(ip))) {
                        match = true;
                    }
                }

                if (match) {
                    cc.comm += 1;
                    found = true;
                    break;
                }
            }
            if (!found) {
                CustomClass newClass = new CustomClass(name, uid, ip, nType);
                newClass.num = 0;
                newClass.comm = 1;
                classes.add(newClass);
            }
        }
    }

    public void printSummary(String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            StringBuilder json = new StringBuilder();
            json.append("[\n");

            List<CustomClass> sorted = getClassesSortedByNum();
            for (int i = 0; i < sorted.size(); i++) {
                CustomClass c = sorted.get(i);
                String idOrIp = c.userID.isEmpty() ? c.ip : c.userID;
                json.append("  {\n")
                        .append("    \"name\": \"").append(escape(c.name)).append("\",\n")
                        .append("    \"id_or_ip\": \"").append(escape(idOrIp)).append("\",\n")
                        .append("    \"nicktype\": \"").append(escape(c.nicktype)).append("\",\n")
                        .append("    \"num\": ").append(c.num).append(",\n")
                        .append("    \"view\": ").append(c.view).append(",\n")
                        .append("    \"recom\": ").append(c.recom).append(",\n")
                        .append("    \"reple\": ").append(c.reple).append(",\n")
                        .append("    \"comm\": ").append(c.comm).append("\n")
                        .append("  }").append(i < sorted.size() - 1 ? "," : "").append("\n");
            }

            json.append("]");
            writer.write(json.toString());
            System.out.println("데이터 저장 완료: " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private ArrayList<CustomClass> getClassesSortedByNum() {
        ArrayList<CustomClass> sorted = new ArrayList<>(classes);
        sorted.sort(Comparator.comparingInt(c -> -c.num));
        return sorted;
    }

    // 기존의 날짜/시간 저장 기능 유지
    public void dayprinter(List<String> days) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("date-data.txt"))) {
            for (String day : days) { writer.write(day); writer.newLine(); }
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void timeprinter(List<String> times) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("time-data.txt"))) {
            for (String t : times) { writer.write(t); writer.newLine(); }
        } catch (IOException e) { e.printStackTrace(); }
    }
    public void saveUserLog(ArrayList<String> names, ArrayList<String> userIDs, ArrayList<String> ips, List<String> days, String filename) {
        if (names == null || days == null || names.size() != days.size()) {
            System.out.println("데이터의 길이가 일치하지 않거나 비어있습니다.");
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            int size = names.size();
            for (int i = 0; i < size; i++) {
                // 1. 이름 파싱 (고정/비고정 등 접두사 제거 및 정제)
                String[] parsed = parseNick(names.get(i));
                String cleanName = parsed[0];

                // 2. ID 또는 IP 결정 (ID가 없으면 IP 사용)
                String uid = (userIDs != null && i < userIDs.size() && userIDs.get(i) != null) ? userIDs.get(i) : "";
                String ip = (ips != null && i < ips.size() && ips.get(i) != null) ? ips.get(i) : "";
                String idOrIp = uid.isEmpty() ? ip : uid;

                // 3. 날짜 가져오기
                String day = days.get(i);

                // 4. 형식에 맞춰 쓰기: [이름] [ID/IP] [날짜]
                writer.write(String.format("[%s] [%s] [%s]", cleanName, idOrIp, day));
                writer.newLine();
            }
            System.out.println("로그 저장 완료: " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}