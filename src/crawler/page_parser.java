package crawler;

import org.jsoup.Jsoup;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class page_parser {
    private static final int BLOCK_COOLDOWN_BASE_MS = 5000;
    private static volatile long globalCooldownUntil = 0L;

    public static CrawlerResult Crawler(String ID, String Gall, int start, int end, int concurrency) throws InterruptedException {
        ArrayList<String> skipSubjects = new ArrayList<>(Arrays.asList("고정", "공지", "설문", "AD"));
        ArrayList<String> skipAuthor = new ArrayList<>(Arrays.asList("운영자","김유식"));

        // Thread-safe 데이터 저장소
        List<String> AuthorBox = Collections.synchronizedList(new ArrayList<>());
        List<String> IpBox = Collections.synchronizedList(new ArrayList<>());
        List<String> IDBox = Collections.synchronizedList(new ArrayList<>());
        List<Integer> ViewBox = Collections.synchronizedList(new ArrayList<>());
        List<Integer> RecomBox = Collections.synchronizedList(new ArrayList<>());
        List<Integer> RepleBox = Collections.synchronizedList(new ArrayList<>());
        List<String> RepleTrueBox = Collections.synchronizedList(new ArrayList<>());
        List<String> days = Collections.synchronizedList(new ArrayList<>());

        int total_geul = 0;

        int effectiveConcurrency = Math.max(1, concurrency);
        ExecutorService executor = Executors.newFixedThreadPool(effectiveConcurrency);
        List<Future<Void>> futures = new ArrayList<>();
        AtomicInteger completedPages = new AtomicInteger(0);

        // 쿨다운 상태 관리 변수
        AtomicBoolean isPaused = new AtomicBoolean(false);

        GalleryConfig galleryConfig = resolveGalleryConfig(ID, Gall);
        String baseurl = galleryConfig.baseUrl();
        String subtag = galleryConfig.subjectSelector();
        String actualGallType = galleryConfig.type();

        if (!actualGallType.equals(Gall)) {
            System.out.println("갤러리 타입 자동 보정: " + Gall + " -> " + actualGallType);
        }

        for (int page = start; page <= end; page++) {
            final int currentPage = page;
            futures.add(executor.submit(() -> {
                int retries = 3;
                while (retries-- > 0) {
                    try {
                        waitGlobalCooldown();

                        // 스레드들이 같은 순간에 몰리지 않게 요청 전 지터를 둔다.
                        Thread.sleep(ThreadLocalRandom.current().nextInt(300, 901));

                        // 다른 스레드에 의해 글로벌 쿨다운(에러 발생 등) 중인지 체크
                        while (isPaused.get()) {
                            Thread.sleep(100);
                        }

                        String key = baseurl + "&page=" + currentPage;
                        Document doc = fetchListDocument(key, actualGallType, ID);

                        Elements trList = doc.select("tr.ub-content");

                        for (Element tag : trList) {
                            Element subjectTd = tag.selectFirst(subtag);
                            if (subjectTd != null && !skipSubjects.contains(subjectTd.text().trim())) {

                                Element writerTd = tag.selectFirst("td.gall_writer");

                                String imgSrc = "";
                                Element img = writerTd.selectFirst("a.writer_nikcon img");
                                if (img != null) imgSrc = img.attr("src");

                                String subnik = determineSubnik(imgSrc);
                                String uid = writerTd.attr("data-uid");
                                String nick = writerTd.attr("data-nick");
                                if (skipAuthor.contains((nick))) continue;
                                String ip = writerTd.attr("data-ip");
                                String displayName = subnik + nick;

                                Element view = tag.selectFirst("td.gall_count");
                                Element recommend = tag.selectFirst("td.gall_recommend");
                                Element reple = tag.selectFirst("span.reply_num");
                                Element geulnum = tag.selectFirst("td.gall_num");
                                String geulnumText = geulnum != null ? geulnum.text().trim() : "";

                                if (geulnumText.isEmpty() || geulnumText.equals("-")) continue;

                                Element day = tag.selectFirst("td.gall_date");

                                AuthorBox.add(displayName);
                                IpBox.add(ip);
                                IDBox.add(uid);
                                days.add(day != null ? day.attr("title") : "");
                                ViewBox.add(parseSafeInt(view != null ? view.text() : ""));
                                RecomBox.add(parseSafeInt(recommend != null ? recommend.text() : ""));

                                if (reple != null) {
                                    RepleTrueBox.add(geulnumText);
                                    String ra = reple.text();
                                    String repl = ra.contains("/") ? ra.substring(1, ra.length() - 1).split("/")[0] : ra.replaceAll("[\\[\\]]", "");
                                    RepleBox.add(parseSafeInt(repl));
                                } else {
                                    RepleBox.add(0);
                                }
                            }
                        }

                        int done = completedPages.incrementAndGet();
                        System.out.print("\r페이지 진행: " + done + "/" + (end - start + 1));
                        break;

                    } catch (Exception e) {
                        if (isNotFound(e)) {
                            System.out.println("\n[건너뜀] 페이지 " + currentPage + " 없음(404)");
                            completedPages.incrementAndGet();
                            break;
                        }
                        if (retries == 0) {
                            System.err.println("\n[오류] 페이지 " + currentPage + " 실패: " + e.getMessage());
                        } else {
                            if (isBlockingResponse(e)) {
                                applyGlobalCooldown(BLOCK_COOLDOWN_BASE_MS * (4 - retries));
                            }

                            // 에러 발생 시 전체 스레드 흐름을 제어 (쿨다운 게이트)
                            if (isPaused.compareAndSet(false, true)) {
                                Thread.sleep(ThreadLocalRandom.current().nextInt(1500, 3501));
                                isPaused.set(false);
                            }
                        }
                    }
                }
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        executor.shutdown();
        System.out.println("\n크롤링 완료.");

        return new CrawlerResult(
                new ArrayList<>(AuthorBox), new ArrayList<>(IDBox), new ArrayList<>(IpBox),
                new ArrayList<>(ViewBox), new ArrayList<>(RecomBox), new ArrayList<>(RepleBox),
                new ArrayList<>(RepleTrueBox), new ArrayList<>(days), total_geul, actualGallType
        );
    }

    private static GalleryConfig resolveGalleryConfig(String id, String preferredType) throws InterruptedException {
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, preferredType);
        addCandidate(candidates, "m");
        addCandidate(candidates, "main");
        addCandidate(candidates, "mini");

        Exception lastException = null;
        for (String type : candidates) {
            GalleryConfig config = galleryConfigFor(type, id);
            try {
                fetchListDocument(config.baseUrl() + "&page=1", config.type(), id);
                return config;
            } catch (Exception e) {
                lastException = e;
                if (isBlockingResponse(e)) {
                    applyGlobalCooldown(BLOCK_COOLDOWN_BASE_MS);
                    waitGlobalCooldown();
                }
            }
        }

        String message = lastException == null ? "알 수 없는 오류" : lastException.getMessage();
        throw new IllegalArgumentException("갤러리 주소를 찾지 못했습니다. TYPE과 갤러리 ID를 확인하세요. (" + message + ")");
    }

    private static void addCandidate(List<String> candidates, String type) {
        String normalized = normalizeType(type);
        if (!candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    private static String normalizeType(String type) {
        if ("mini".equals(type)) return "mini";
        if ("m".equals(type)) return "m";
        return "main";
    }

    private static GalleryConfig galleryConfigFor(String type, String id) {
        String normalized = normalizeType(type);
        if ("mini".equals(normalized)) {
            return new GalleryConfig("mini", "https://gall.dcinside.com/mini/board/lists/?id=" + id, "td.gall_subject");
        }
        if ("m".equals(normalized)) {
            return new GalleryConfig("m", "https://gall.dcinside.com/mgallery/board/lists/?id=" + id, "td.gall_subject");
        }
        return new GalleryConfig("main", "https://gall.dcinside.com/board/lists/?id=" + id, "td.gall_num");
    }

    private static Document fetchListDocument(String url, String gallType, String id) throws java.io.IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cache-Control", "no-cache")
                .header("Referer", refererFor(gallType, id))
                .timeout(15000)
                .get();
    }

    private static String determineSubnik(String imgSrc) {
        if (imgSrc.contains("fix_nik.gif") || imgSrc.contains("fix_sub_managernik.gif") ||
                imgSrc.contains("fix_managernik.gif") || imgSrc.contains("sub_managernik.gif")) {
            return "고정";
        } else if (imgSrc.contains("nik.gif") || imgSrc.contains("managernik.gif")) {
            return "비고정";
        }
        return "유동";
    }

    private static String refererFor(String gallType, String id) {
        if ("mini".equals(gallType)) {
            return "https://gall.dcinside.com/mini/board/lists/?id=" + id;
        }
        if ("m".equals(gallType)) {
            return "https://gall.dcinside.com/mgallery/board/lists/?id=" + id;
        }
        return "https://gall.dcinside.com/board/lists/?id=" + id;
    }

    private record GalleryConfig(String type, String baseUrl, String subjectSelector) {
    }

    private static int parseSafeInt(String text) {
        try {
            return Integer.parseInt(text.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isBlockingResponse(Exception e) {
        if (e instanceof HttpStatusException statusException) {
            int status = statusException.getStatusCode();
            return status == 403 || status == 429 || status == 503;
        }
        return false;
    }

    private static boolean isNotFound(Exception e) {
        return e instanceof HttpStatusException statusException && statusException.getStatusCode() == 404;
    }

    private static void applyGlobalCooldown(long millis) {
        long jitter = ThreadLocalRandom.current().nextLong(1000, 4001);
        long until = System.currentTimeMillis() + millis + jitter;
        globalCooldownUntil = Math.max(globalCooldownUntil, until);
        System.err.println("\n[차단 방지] 페이지 요청 쿨다운 " + ((millis + jitter) / 1000) + "초");
    }

    private static void waitGlobalCooldown() throws InterruptedException {
        long wait = globalCooldownUntil - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
    }
}
