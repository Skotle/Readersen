package crawler;

import java.util.Arrays;

import java.net.URI;
import java.net.http.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class comment_Parser {

    public static subResult geulp(String ID, String TYPE, ArrayList<String> targets, int concurrency) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(Executors.newFixedThreadPool(concurrency))
                .build();

        ArrayList<String> allNames = new ArrayList<>();
        ArrayList<String> allIps = new ArrayList<>();
        ArrayList<String> allIDs = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ArrayList<Double> pers = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger totalRetries = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);

        int batchSize = 50; // N개 요청마다 슬립
        long batchSleepMillis = 300; // 0.3초

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        for (int x = 0; x < targets.size(); x++) {
            final int articleNo = Integer.parseInt(targets.get(x));

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                int maxRetry = 3;

                for (int attempt = 1; attempt <= maxRetry; attempt++) {
                    try {
                        String payload;
                        String referer;

                        if ("mini".equals(TYPE)) {
                            payload = "id=" + ID + "&no=" + articleNo + "&cmt_id=" + ID + "&cmt_no=" + articleNo +
                                    "&focus_cno=&focus_pno=&e_s_n_o=3eabc219ebdd65ff3eef86e54781" +
                                    "&comment_page=1&sort=R&prevCnt=&board_type=&_GALLTYPE_=MI&secret_article_key=";
                            referer = "https://gall.dcinside.com/mini/board/view/?id=" + ID + "&no=" + articleNo;
                        } else if ("m".equals(TYPE)) {
                            payload = "id=" + ID + "&no=" + articleNo + "&cmt_id=" + ID + "&cmt_no=" + articleNo +
                                    "&focus_cno=&focus_pno=&e_s_n_o=3eabc219ebdd65ff3eef86e54781" +
                                    "&comment_page=1&sort=R&prevCnt=&board_type=&_GALLTYPE_=M&secret_article_key=";
                            referer = "https://gall.dcinside.com/mgallery/board/view/?id=" + ID + "&no=" + articleNo;
                        } else {
                            payload = "id=" + ID + "&no=" + articleNo + "&cmt_id=" + ID + "&cmt_no=" + articleNo +
                                    "&focus_cno=&focus_pno=&e_s_n_o=3eabc219ebdd65ff3eef86e54781" +
                                    "&comment_page=1&sort=R&prevCnt=&board_type=&_GALLTYPE_=G&secret_article_key=";
                            referer = "https://gall.dcinside.com/board/view/?id=" + ID + "&no=" + articleNo;
                        }

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("https://gall.dcinside.com/board/comment/"))
                                .timeout(Duration.ofSeconds(5))
                                .header("User-Agent", "Mozilla/5.0")
                                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                                .header("X-Requested-With", "XMLHttpRequest")
                                .header("Referer", referer)
                                .POST(HttpRequest.BodyPublishers.ofString(payload))
                                .build();

                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        List<Map<String, String>> commentData = extractNamesWithInfo(response.body());

                        // 예시: 제외할 이름 배열
                        Set<String> excludeNames = new HashSet<>(Arrays.asList("댓글돌이", "추천제외1", "추천제외2"));

                        synchronized (allNames) {
                            for (Map<String, String> c : commentData) {
                                String name = c.get("name");
                                String uid = c.get("user_id");
                                String type = c.get("nicktype");
                                String ip = c.get("ip");
                                String gallog_icon = c.get("gallog_icon");
                                // 이름 끝에 숫자가 있는 경우 제거 (uid 앞 숫자는 그대로)
                                int parenIndex = name.indexOf("(");
                                String baseName = parenIndex >= 0 ? name.substring(0, parenIndex) : name;
                                baseName = baseName.replaceAll("\\d+$", ""); // 끝 숫자 제거
                                String finalName = parenIndex >= 0 ? baseName + name.substring(parenIndex) : baseName;
                                if (excludeNames.contains(finalName)) continue;

                                allNames.add(finalName);
                                if (ip.equals("")) {
                                    allIps.add(null);
                                    allIDs.add(uid);
                                }
                                if (uid.equals("")) {
                                    allIDs.add(null);
                                    allIps.add(ip);
                                }
                            }
                        }




                        break; // 성공하면 재시도 종료

                    } catch (Exception e) {
                        totalRetries.incrementAndGet();
                        if (attempt == maxRetry) {
                            failedRequests.incrementAndGet();
                            System.out.println("글 번호: " + articleNo + " 요청 실패, 스킵됨");
                        } else {
                            try { Thread.sleep(1000L * attempt); } catch (InterruptedException ex) { ex.printStackTrace(); }
                        }
                    }
                }

                int done = completed.incrementAndGet();
                if (done % batchSize == 0 || done == targets.size()) {
                    long elapsedMillis = System.currentTimeMillis() - startTime;
                    double rpPerSec = done / (elapsedMillis / 1000.0);
                    if ((double)done/(double)targets.size()*100>20) {
                        pers.add(rpPerSec);
                    }
                    System.out.printf("진행: %d/%d 글(%.2f%s), 평균 속도: %.2f r/s, 남은 시간 %.2f초\n", done, targets.size(),(double)done/(double)targets.size()*100,'%', rpPerSec,(targets.size()-done)/rpPerSec);
                    // 배치별 잠시 쉬기
                    try { Thread.sleep(batchSleepMillis); } catch (InterruptedException ignored) {}
                }

            }, executor);

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        long totalElapsed = System.currentTimeMillis() - startTime;
        double finalRpSec = targets.size() / (totalElapsed / 1000.0);
        System.out.println("소요: "+(double)totalElapsed/1000+"초");
        System.out.println("모든 요청 완료, 총 집계: " + allNames.size());
        System.out.println("\n최대 속도: "+Collections.max(pers)+" r/s");
        System.out.println("평균 속도: " + finalRpSec + " r/s");
        System.out.println("최저 속도: "+Collections.min(pers)+" r/s\n");
        System.out.println("총 재시도 횟수: " + totalRetries.get());
        System.out.println("총 실패 요청 수: " + failedRequests.get());

        return new subResult(allNames,allIDs,allIps);
    }

    private static List<Map<String, String>> extractNamesWithInfo(String json) {
        List<Map<String, String>> result = new ArrayList<>();
        int idx = 0;

        while ((idx = json.indexOf("{\"no\":", idx)) != -1) {
            int endIdx = json.indexOf("}", idx);
            if (endIdx == -1) break;

            String commentJson = json.substring(idx, endIdx + 1);

            String name = extractValue(commentJson, "\"name\":\"");
            String userId = extractValue(commentJson, "\"user_id\":\"");
            String ip = extractValue(commentJson, "\"ip\":\"");
            String nicktype = extractValue(commentJson, "\"nicktype\":\"");
            String gallogIcon = extractValue(commentJson, "\"gallog_icon\":\"");

            if (name != null) name = decodeUnicode(name);
            if (userId == null) userId = "";
            if (ip == null) ip = "";
            if (nicktype == null) nicktype = "";
            if (gallogIcon == null) gallogIcon = "";

            String imgSrc = extractImgSrc(gallogIcon); // img src 추출

            Map<String, String> data = new HashMap<>();
            data.put("name", name);
            data.put("user_id", userId);
            data.put("ip", ip);
            data.put("nicktype", nicktype);
            data.put("gallog_icon_src", imgSrc); // src값 저장

            result.add(data);
            idx = endIdx + 1;
        }

        return result;
    }
    private static String extractImgSrc(String gallogIcon) {
        if (gallogIcon == null) return "";
        Matcher m = Pattern.compile("img\\s+src=['\"]([^'\"]+)['\"]").matcher(gallogIcon);
        if (m.find()) {
            return m.group(1); // src 부분만 반환
        }
        return "";
    }


    // key 이후 "..." 형태의 값을 추출
    private static String extractValue(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;
        int start = keyIdx + key.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }


    // 유니코드 디코딩
    private static String decodeUnicode(String input) {
        try {
            Properties p = new Properties();
            p.load(new java.io.StringReader("a=" + input));
            return p.getProperty("a");
        } catch (Exception e) {
            return input;
        }
    }
}

