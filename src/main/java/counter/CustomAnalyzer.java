package counter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CustomAnalyzer {
    private ArrayList<CustomClass> classes = new ArrayList<>();

    public void analyzeData(ArrayList<String> names, ArrayList<Integer> views,
                            ArrayList<Integer> recoms, ArrayList<Integer> reples) {
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int v = views.get(i);
            int r = recoms.get(i);
            int rp = reples.get(i);

            boolean found = false;
            for (CustomClass cc : classes) {
                if (cc.name.equals(name)) {
                    cc.addMember(v, r, rp);
                    found = true;
                    break;
                }
            }
            if (!found) {
                CustomClass newClass = new CustomClass(name);
                newClass.view = v;
                newClass.recom = r;
                newClass.reple = rp;
                classes.add(newClass);
            }
        }
    }

    public void applyCommList(ArrayList<String> commNames) {
        for (String name : commNames) {
            boolean found = false;
            for (CustomClass cc : classes) {
                if (cc.name.equals(name)) {
                    cc.comm += 1;
                    found = true;
                    break;
                }
            }
            if (!found) {
                CustomClass newClass = new CustomClass(name);
                newClass.num = 0;
                newClass.comm = 1;
                classes.add(newClass);
            }
        }
    }

    public ArrayList<CustomClass> getClassesSortedByNum() {
        ArrayList<CustomClass> sorted = new ArrayList<>(classes);
        sorted.sort(Comparator.comparingInt(c -> -c.num));  // 내림차순
        return sorted;
    }

    public void printSummary() {
        try{
            FileWriter writer = new FileWriter("user-data.txt");
            for (CustomClass c : getClassesSortedByNum()){
                writer.write(c.name + "SPLIT" + c.num + "SPLIT" + c.view + "SPLIT" + c.recom + "SPLIT" + c.reple + "SPLIT" + c.comm + "\n");
            }
            writer.close();
            System.out.println("데이터 저장 완료: data.txt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void dayprinter(List<String> days) {
        // 예시 날짜 리스트 (Java에서 파싱한 결과라고 가정)
        // 파일로 저장
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("date-data.txt"))) {
            for (String day : days) {
                writer.write(day);
                writer.newLine(); // 줄바꿈
            }
            System.out.println("✅ dates-data.txt 저장 완료");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void timeprinter(List<String> days) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("time-data.txt"))) {
            for (String day : days) {
                writer.write(day);
                writer.newLine();
            }
            System.out.println("✅ time-data.txt 저장 완료");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

