package crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class page_parser {

    public static CrawlerResult Crawler(String ID, String Gall, int start, int end, int concurrency) throws InterruptedException {
        ArrayList<String> skipSubjects = new ArrayList<>(Arrays.asList("고정", "공지", "설문", "AD"));

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

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<Void>> futures = new ArrayList<>();
        AtomicInteger completedPages = new AtomicInteger(0);

        // 쿨다운 상태 관리 변수
        AtomicBoolean isPaused = new AtomicBoolean(false);

        String baseurl;
        String subtag;
        if ("mini".equals(Gall)) {
            subtag = "td.gall_subject";
            baseurl = "https://gall.dcinside.com/mini/board/lists/?id=" + ID;
        } else if ("m".equals(Gall)) {
            subtag = "td.gall_subject";
            baseurl = "https://gall.dcinside.com/mgallery/board/lists/?id=" + ID;
        } else {
            subtag = "td.gall_num";
            baseurl = "https://gall.dcinside.com/board/lists/?id=" + ID;
        }

        for (int page = start; page <= end; page++) {
            final int currentPage = page;
            futures.add(executor.submit(() -> {
                int retries = 3;
                while (retries-- > 0) {
                    try {
                        // 1. [암묵적 쿨다운] 페이지 요청 시작 전 0.2초 대기
                        // 스레드들이 몰리는 것을 방지하기 위해 각 작업 시작 시점에 배치
                        Thread.sleep(200);

                        // 다른 스레드에 의해 글로벌 쿨다운(에러 발생 등) 중인지 체크
                        while (isPaused.get()) {
                            Thread.sleep(100);
                        }

                        String key = baseurl + "&page=" + currentPage;
                        Document doc = Jsoup.connect(key)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .header("Referer", "https://www.google.com")
                                .timeout(5000)
                                .get();

                        Elements trList = doc.select("tr.ub-content");

                        for (Element tag : trList) {
                            Element subjectTd = tag.selectFirst(subtag);
                            if (subjectTd != null && !skipSubjects.contains(subjectTd.text().trim())) {

                                Element writerTd = tag.selectFirst("td.gall_writer");
                                if (writerTd == null) continue;

                                String imgSrc = "";
                                Element img = writerTd.selectFirst("a.writer_nikcon img");
                                if (img != null) imgSrc = img.attr("src");

                                String subnik = determineSubnik(imgSrc);
                                String uid = writerTd.attr("data-uid");
                                String nick = writerTd.attr("data-nick");
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
                                ViewBox.add(parseSafeInt(view.text()));
                                RecomBox.add(parseSafeInt(recommend.text()));

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
                        if (retries == 0) {
                            System.err.println("\n[오류] 페이지 " + currentPage + " 실패: " + e.getMessage());
                        } else {
                            // 에러 발생 시 1초간 전체 스레드 흐름을 제어 (쿨다운 게이트)
                            if (isPaused.compareAndSet(false, true)) {
                                Thread.sleep(1000);
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
                new ArrayList<>(RepleTrueBox), new ArrayList<>(days), total_geul
        );
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

    private static int parseSafeInt(String text) {
        try {
            return Integer.parseInt(text.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}