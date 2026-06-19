package crawler;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

public class comment_Parser {
    private record CommentEntry(String name, String uid, String ip, String date, String memo) {}

    public static subResult geulp(String ID, String TYPE, ArrayList<String> targets, int concurrency) {

        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        ConcurrentLinkedQueue<CommentEntry> allComments = new ConcurrentLinkedQueue<>();
        List<Double> pers = Collections.synchronizedList(new ArrayList<>());

        long startTime = System.currentTimeMillis();

        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger totalRetries = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);

        int effectiveConcurrency = Math.max(1, concurrency);
        Semaphore semaphore = new Semaphore(effectiveConcurrency);
        ExecutorService taskExecutor = Executors.newFixedThreadPool(effectiveConcurrency);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String target : targets) {
            int articleNo = Integer.parseInt(target);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                boolean acquired = false;

                try {
                    semaphore.acquire();
                    acquired = true;

                    sendWithRetry(client, ID, TYPE, articleNo, 3,
                            totalRetries, failedRequests)
                            .thenAccept(response -> {

                                        List<Map<String, String>> commentData =
                                                extractNamesWithInfo(response);
                                        Set<String> excludeNames = new HashSet<>(
                                                Arrays.asList("댓글돌이", "추천제외1", "추천제외2")
                                        );

                                        for (Map<String, String> c : commentData) {

                                            String name = c.get("name");
                                            String uid = c.get("user_id");
                                            String type = c.get("nicktype");
                                            String ip = c.get("ip");
                                            String date = c.get("reg_date");
                                            String content = c.get("content");

                                            if (name == null) name = "";
                                            if (date == null) date = "";
                                            if (content == null) content = "";

                                            if (excludeNames.contains(name)) continue;

                                            String nt = switch (type) {
                                                case "20" -> "고정";
                                                case "00" -> "비고정";
                                                default -> "유동";
                                            };

                                            String displayName = nt + name;
                                            allComments.add(new CommentEntry(
                                                    displayName,
                                                    uid,
                                                    ip,
                                                    normalizeDate(date),
                                                    content
                                            ));
                                            CollectionProgress.recordComment(displayName, uid, ip);
                                        }

                                        int done = completed.incrementAndGet();

                                        if (done > 0) {
                                            long elapsed = System.currentTimeMillis() - startTime;
                                            double rps = done / Math.max(0.001, elapsed / 1000.0);
                                            double pct = (double) done / targets.size() * 100;

                                            if (pct > 20) pers.add(rps);

                                            double remaining = (targets.size() - done) / rps;
                                            if (done%10==0) {
                                                System.out.printf(
                                                        "\r진행: %d/%d (%.2f%%), %.2f r/s, 남은 %d초",
                                                        done, targets.size(), pct, rps, (int) remaining
                                                );
                                            }
                                        }

                            }).join();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (CompletionException e) {
                    failedRequests.incrementAndGet();
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    System.out.println("\n글 번호: " + articleNo + " 처리 실패: " + cause.getMessage());
                } catch (Exception e) {
                    failedRequests.incrementAndGet();
                    System.out.println("\n글 번호: " + articleNo + " 처리 실패: " + e.getMessage());
                } finally {
                    if (acquired) {
                        semaphore.release();
                    }
                }

            }, taskExecutor);

            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            taskExecutor.shutdownNow();
        }

        long totalElapsed = System.currentTimeMillis() - startTime;
        double finalRpSec = targets.size() / (totalElapsed / 1000.0);

        System.out.println("\n소요: " + (double) totalElapsed / 1000 + "초");
        System.out.println("모든 요청 완료, 총 집계: " + allComments.size());

        List<Double> snapshot;
        synchronized (pers) {
            snapshot = new ArrayList<>(pers);
        }

        double max = snapshot.isEmpty() ? Double.NaN : Collections.max(snapshot);
        double min = snapshot.isEmpty() ? Double.NaN : Collections.min(snapshot);

        System.out.println("\n최대 속도: " + (Double.isNaN(max) ? "N/A" : max) + " r/s");
        System.out.println("평균 속도: " + finalRpSec + " r/s");
        System.out.println("최저 속도: " + (Double.isNaN(min) ? "N/A" : min) + " r/s\n");
        System.out.println("총 재시도 횟수: " + totalRetries.get());
        System.out.println("총 실패 요청 수: " + failedRequests.get());

        ArrayList<String> resultNames = new ArrayList<>();
        ArrayList<String> resultIDs = new ArrayList<>();
        ArrayList<String> resultIps = new ArrayList<>();
        ArrayList<String> resultDays = new ArrayList<>();
        ArrayList<String> resultContents = new ArrayList<>();

        for (CommentEntry e : allComments) {
            resultNames.add(e.name());
            resultIDs.add(e.uid().isEmpty() ? null : e.uid());
            resultIps.add(e.ip().isEmpty() ? null : e.ip());
            resultDays.add(e.date());
            resultContents.add(e.memo());
        }

        CollectionProgress.publishNow();
        return new subResult(resultNames, resultIDs, resultIps, resultDays, resultContents);
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
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", "https://gall.dcinside.com")
                .header("Referer", referer)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    int status = response.statusCode();
                    if (status == 403 || status == 429 || status == 503) {
                        totalRetries.incrementAndGet();
                        if (maxRetry > 0) {
                            return sendWithRetry(client, ID, TYPE, articleNo,
                                    maxRetry - 1, totalRetries, failedRequests);
                        }

                        failedRequests.incrementAndGet();
                        System.out.println("\n글 번호: " + articleNo + " 차단성 응답(" + status + "), 스킵됨");
                        return CompletableFuture.completedFuture("");
                    }

                    if (status < 200 || status >= 300) {
                        totalRetries.incrementAndGet();
                        if (maxRetry > 0) {
                            return sendWithRetry(client, ID, TYPE, articleNo,
                                    maxRetry - 1, totalRetries, failedRequests);
                        }

                        failedRequests.incrementAndGet();
                        System.out.println("\n글 번호: " + articleNo + " HTTP " + status + ", 스킵됨");
                        return CompletableFuture.completedFuture("");
                    }

                    return CompletableFuture.completedFuture(response.body());
                })
                .exceptionallyCompose(ex -> {
                    totalRetries.incrementAndGet();
                    if (maxRetry > 0) {
                        return sendWithRetry(client, ID, TYPE, articleNo,
                                maxRetry - 1, totalRetries, failedRequests);
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

            String name = extractValue(commentJson, "\"name\":\"");
            String userId = extractValue(commentJson, "\"user_id\":\"");
            String ip = extractValue(commentJson, "\"ip\":\"");
            String nicktype = extractValue(commentJson, "\"nicktype\":\"");
            String datedata = extractValue(commentJson, "\"reg_date\":\"");
            String content = normalizeContent(extractValue(commentJson, "\"memo\":\""));

            if (name != null) name = decodeUnicode(name);
            if (userId == null) userId = "";
            if (ip == null) ip = "";
            if (nicktype == null) nicktype = "";
            if (content == null) content = "";

            Map<String, String> data = new HashMap<>();
            data.put("name", name);
            data.put("user_id", userId);
            data.put("ip", ip);
            data.put("nicktype", nicktype);
            data.put("reg_date", datedata);
            data.put("content", content);

            result.add(data);
            idx = endIdx + 1;
        }

        return result;
    }

    private static String extractValue(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;
        int start = keyIdx + key.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private static String normalizeContent(String content) {
        if (content == null || content.isEmpty()) return "";

        content = decodeUnicode(content);
        content = content.replaceAll("<[^>]*>?|&nbsp;", "");
        content = content.replace("- dc App", "");
        content = content.replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
        return content.trim().replaceAll("\\s+", " ");
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

    private static String normalizeDate(String date) {
        if (date == null || date.isEmpty()) return null;

        if (date.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) return date;

        try {
            if (date.matches("\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                int currentYear = java.time.LocalDate.now().getYear();
                int currentMonth = java.time.LocalDate.now().getMonthValue();
                int month = Integer.parseInt(date.substring(0, 2));
                int year = (month > currentMonth) ? currentYear - 1 : currentYear;
                String normalized = year + "-" + date.replace(".", "-").replaceFirst("-", "-");
                return java.time.LocalDateTime.parse(
                        normalized.trim(),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                ).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }

            List<String> patterns = List.of(
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy.MM.dd HH:mm:ss",
                    "yyyy/MM/dd HH:mm:ss",
                    "yyyy-MM-dd HH:mm",
                    "yyyy.MM.dd HH:mm"
            );

            for (String pattern : patterns) {
                try {
                    return java.time.LocalDateTime.parse(
                            date.trim(),
                            java.time.format.DateTimeFormatter.ofPattern(pattern)
                    ).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } catch (Exception ignored) {
                }
            }

        } catch (Exception e) {
            System.out.println("날짜 파싱 실패: " + date);
        }

        return null;
    }
}
