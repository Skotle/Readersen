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

    // 스케줄러: sleep 대신 논블로킹 지연에 사용
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(1);

    public static subResult geulp(String ID, String TYPE, ArrayList<String> targets, int concurrency) {

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(Executors.newFixedThreadPool(concurrency))
                .build();

        ConcurrentLinkedQueue<String> allNames = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> allIps   = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> allIDs   = new ConcurrentLinkedQueue<>();

        List<Double> pers = Collections.synchronizedList(new ArrayList<>());

        long startTime = System.currentTimeMillis();

        AtomicInteger completed      = new AtomicInteger(0);
        AtomicInteger totalRetries   = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);

        int  batchSize        = 50;
        long batchSleepMillis = 300;

        // Semaphore 제거 → 메인 루프가 블로킹 없이 모든 future 즉시 등록
        // 동시 요청 수 제한은 HttpClient executor 스레드 수(concurrency)로만 관리
        List<CompletableFuture<Void>> futures = new ArrayList<>(targets.size());

        for (int x = 0; x < targets.size(); x++) {
            final int articleNo = Integer.parseInt(targets.get(x));

            CompletableFuture<Void> future =
                    sendWithRetry(client, ID, TYPE, articleNo, 3, totalRetries, failedRequests)
                            .thenAccept(response -> {

                                List<Map<String, String>> commentData = extractNamesWithInfo(response);
                                Set<String> excludeNames = new HashSet<>(Arrays.asList("댓글돌이", "추천제외1", "추천제외2"));

                                for (Map<String, String> c : commentData) {
                                    String name = c.get("name");
                                    String uid  = c.get("user_id");
                                    String type = c.get("nicktype");
                                    String ip   = c.get("ip");

                                    if (excludeNames.contains(name)) continue;

                                    String nt = switch (type) {
                                        case "20" -> "고정";
                                        case "00" -> "비고정";
                                        default   -> "유동";
                                    };

                                    allNames.add(nt + name);

                                    if (ip.isEmpty()) {
                                        allIps.add("");
                                        allIDs.add(uid);
                                    } else {
                                        allIDs.add("");
                                        allIps.add(ip);
                                    }
                                }

                                int done = completed.incrementAndGet();

                                if (done % batchSize == 0 || done == targets.size()) {
                                    long   elapsed  = System.currentTimeMillis() - startTime;
                                    double rpPerSec = done / (elapsed / 1000.0);
                                    double pct      = (double) done / targets.size() * 100;

                                    if (pct > 20) pers.add(rpPerSec);

                                    double remaining = (targets.size() - done) / rpPerSec;
                                    System.out.printf("\r진행: %d/%d 글(%.2f%s), 평균 속도: %.2f r/s, 남은 시간 %.2f초",
                                            done, targets.size(), pct, "%", rpPerSec, remaining);
                                }

                            }).exceptionally(e -> null);

            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 출력 안정화용 슬립 (스레드 블로킹 아닌 메인 스레드에서 한 번만)
        try { Thread.sleep(batchSleepMillis); } catch (InterruptedException ignored) {}

        long   totalElapsed = System.currentTimeMillis() - startTime;
        double finalRpSec   = targets.size() / (totalElapsed / 1000.0);

        System.out.println("\n소요: " + (double) totalElapsed / 1000 + "초");
        System.out.println("모든 요청 완료, 총 집계: " + allNames.size());
        System.out.println("\n최대 속도: " + (pers.isEmpty() ? "N/A" : Collections.max(pers)) + " r/s");
        System.out.println("평균 속도: " + finalRpSec + " r/s");
        System.out.println("최저 속도: " + (pers.isEmpty() ? "N/A" : Collections.min(pers)) + " r/s\n");
        System.out.println("총 재시도 횟수: " + totalRetries.get());
        System.out.println("총 실패 요청 수: " + failedRequests.get());

        ArrayList<String> resultIDs = new ArrayList<>();
        ArrayList<String> resultIps = new ArrayList<>();
        for (String s : allIDs) resultIDs.add(s.isEmpty() ? null : s);
        for (String s : allIps) resultIps.add(s.isEmpty() ? null : s);

        return new subResult(new ArrayList<>(allNames), resultIDs, resultIps);
    }

    // Thread.sleep 대신 논블로킹 지연
    private static CompletableFuture<Void> delayAsync(long millis) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        SCHEDULER.schedule(() -> f.complete(null), millis, TimeUnit.MILLISECONDS);
        return f;
    }

    private static CompletableFuture<String> sendWithRetry(HttpClient client, String ID, String TYPE,
                                                           int articleNo, int maxRetry,
                                                           AtomicInteger totalRetries,
                                                           AtomicInteger failedRequests) {
        String payload, referer;

        if ("mini".equals(TYPE)) {
            payload = "id=" + ID + "&no=" + articleNo + "&cmt_id=" + ID + "&cmt_no=" + articleNo +
                    "&focus_cno=&focus_pno=&e_s_n_o=2fepbec219ebdd65ff3eef86e54781" +
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

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .exceptionallyCompose(ex -> {
                    totalRetries.incrementAndGet();
                    if (maxRetry > 0) {
                        // Thread.sleep 대신 논블로킹 지연 (500 / 1000 / 1500ms 점진적 증가)
                        long delay = 500L * (4 - maxRetry);
                        return delayAsync(delay)
                                .thenCompose(ignored ->
                                        sendWithRetry(client, ID, TYPE, articleNo,
                                                maxRetry - 1, totalRetries, failedRequests));
                    } else {
                        failedRequests.incrementAndGet();
                        System.out.println("\n글 번호: " + articleNo + " 요청 실패, 스킵됨");
                        return CompletableFuture.completedFuture("");
                    }
                });
    }

    private static List<Map<String, String>> extractNamesWithInfo(String json) {
        List<Map<String, String>> result = new ArrayList<>();
        int idx = 0;

        while ((idx = json.indexOf("{\"no\":", idx)) != -1) {
            int endIdx = json.indexOf("}", idx);
            if (endIdx == -1) break;

            String commentJson = json.substring(idx, endIdx + 1);

            String name       = extractValue(commentJson, "\"name\":\"");
            String userId     = extractValue(commentJson, "\"user_id\":\"");
            String ip         = extractValue(commentJson, "\"ip\":\"");
            String nicktype   = extractValue(commentJson, "\"nicktype\":\"");
            String gallogIcon = extractValue(commentJson, "\"gallog_icon\":\"");

            if (name       != null) name = decodeUnicode(name);
            if (userId     == null) userId     = "";
            if (ip         == null) ip         = "";
            if (nicktype   == null) nicktype   = "";
            if (gallogIcon == null) gallogIcon = "";

            Map<String, String> data = new HashMap<>();
            data.put("name",            name);
            data.put("user_id",         userId);
            data.put("ip",              ip);
            data.put("nicktype",        nicktype);
            data.put("gallog_icon_src", extractImgSrc(gallogIcon));

            result.add(data);
            idx = endIdx + 1;
        }

        return result;
    }

    private static String extractImgSrc(String gallogIcon) {
        if (gallogIcon == null) return "";
        Matcher m = Pattern.compile("img\\s+src=['\"]([^'\"]+)['\"]").matcher(gallogIcon);
        return m.find() ? m.group(1) : "";
    }

    private static String extractValue(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;
        int start = keyIdx + key.length();
        int end   = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

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