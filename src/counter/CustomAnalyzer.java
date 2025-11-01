package counter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CustomAnalyzer {
    private ArrayList<CustomClass> classes = new ArrayList<>();

    // 글/조회/추천/리플 데이터 집계
    public void analyzeData(ArrayList<String> names, ArrayList<String> userIDs, ArrayList<String> ips,
                            ArrayList<Integer> views, ArrayList<Integer> recoms, ArrayList<Integer> reples) {
        int size = names.size();
        for (int i = 0; i < size; i++) {
            String name = names.get(i);
            String uid  = (i < userIDs.size() && userIDs.get(i) != null) ? userIDs.get(i) : "";
            String ip   = (i < ips.size() && ips.get(i) != null) ? ips.get(i) : "";
            int v  = (i < views.size()) ? views.get(i) : 0;
            int r  = (i < recoms.size()) ? recoms.get(i) : 0;
            int rp = (i < reples.size()) ? reples.get(i) : 0;

            boolean found = false;
            for (CustomClass cc : classes) {
                if (!uid.isEmpty()) { // UID가 있으면 UID만 체크
                    if (!cc.userID.isEmpty() && cc.userID.equals(uid)) {
                        cc.addMember(v, r, rp);
                        found = true;
                        break;
                    }
                } else { // UID 없으면 name + IP 체크
                    if (cc.userID.isEmpty() && cc.name.equals(name) && cc.ip.equals(ip)) {
                        cc.addMember(v, r, rp);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                CustomClass newClass = new CustomClass(name, uid, ip);
                newClass.view  = v;
                newClass.recom = r;
                newClass.reple = rp;
                classes.add(newClass);
            }
        }
    }

    // 댓글 집계
    public void applyCommList(ArrayList<String> names, ArrayList<String> userIDs, ArrayList<String> ips) {
        int max = names.size();
        for (int i = 0; i < max; i++) {
            String name = names.get(i);
            String uid  = (i < userIDs.size() && userIDs.get(i) != null) ? userIDs.get(i) : "";
            String ip   = (i < ips.size() && ips.get(i) != null) ? ips.get(i) : "";

            if (name == null || name.isEmpty()) continue;

            // 글/댓글 통합 키: UID 있으면 UID, 없으면 [아이디 포함 이름
            String key = !uid.isEmpty() ? uid : name;

            boolean found = false;
            for (CustomClass cc : classes) {
                // 키로 매칭
                String ccKey = !cc.userID.isEmpty() ? cc.userID : cc.name;
                if (ccKey.equals(key)) {
                    cc.comm += 1;
                    found = true;
                    break;
                }
            }

            if (!found) { // 새 댓글-only 사용자
                CustomClass newClass = new CustomClass(name, uid, ip);
                newClass.num = 0;  // 글 없음
                newClass.comm = 1; // 댓글 1
                classes.add(newClass);
            }
        }
    }


    // 글 수 기준 정렬
    public ArrayList<CustomClass> getClassesSortedByNum() {
        ArrayList<CustomClass> sorted = new ArrayList<>(classes);
        sorted.sort(Comparator.comparingInt(c -> -c.num));
        return sorted;
    }

    // 글/댓글 통계 파일 출력
    public void printSummary(String filename) {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("이름,ID/IP,글,조회수,추천,리플,댓글\n");
            for (CustomClass c : getClassesSortedByNum()) {
                String auts=c.userID;
                if ("".equals(auts)) {
                    auts = c.ip;
                }

                writer.write(c.name + "SPLIT" +auts + "SPLIT"
                        + c.num + "SPLIT" + c.view + "SPLIT" + c.recom + "SPLIT" + c.reple + "SPLIT" + c.comm+"\n");
            }
            System.out.println("데이터 저장 완료: " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 날짜 저장
    public void dayprinter(List<String> days) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("date-data.txt"))) {
            for (String day : days) {
                writer.write(day);
                writer.newLine();
            }
            System.out.println("✅ date-data.txt 저장 완료");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 시간 저장
    public void timeprinter(List<String> times) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("time-data.txt"))) {
            for (String t : times) {
                writer.write(t);
                writer.newLine();
            }
            System.out.println("✅ time-data.txt 저장 완료");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


