package crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class page_parser {

    public static CrawlerResult Crawler(String ID, String Gall, int start, int end, int concurrency) throws InterruptedException {
        ArrayList<String> skipSubjects = new ArrayList<>(Arrays.asList("고정", "공지", "설문", "AD"));

        ArrayList<String> AuthorBox = new ArrayList<>();
        ArrayList<String> IpBox = new ArrayList<>();
        ArrayList<String> IDBox = new ArrayList<>();
        ArrayList<Integer> ViewBox = new ArrayList<>();
        ArrayList<Integer> RecomBox = new ArrayList<>();
        ArrayList<Integer> RepleBox = new ArrayList<>();
        ArrayList<String> RepleTrueBox = new ArrayList<>();
        ArrayList<String> days = new ArrayList<>();

        int total_geul = 0;

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<Void>> futures = new ArrayList<>();
        AtomicInteger completedPages = new AtomicInteger(0);
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
                        String key = baseurl + "&page=" + currentPage;
                        Document doc = Jsoup.connect(key)
                                .userAgent("Mozilla/5.0")
                                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                                .header("Accept-Language", "en-US,en;q=0.5")
                                .header("Connection", "keep-alive")
                                .timeout(2000)
                                .get();

                        Elements trList = doc.select("tr.ub-content");

                        synchronized (AuthorBox) {
                            for (Element tag : trList) {
                                Element subjectTd = tag.selectFirst(subtag);
                                if (subjectTd != null && !skipSubjects.contains(subjectTd.text().trim())) {

                                    // 작성자 처리
                                    Element writerTd = tag.selectFirst("td.gall_writer");
                                    String imgSrc = "";

                                    if (writerTd != null) {
                                        Element img = writerTd.selectFirst("a.writer_nikcon img");
                                        if (img != null) {
                                            imgSrc = img.attr("src");  // src 주소만 가져오기
                                        }
                                    }


                                    String displayName = "";
                                    String uid = writerTd.attr("data-uid");
                                    String nick = writerTd.attr("data-nick");
                                    String ip = writerTd.attr("data-ip");
//                                    if ("https://nstatic.dcinside.com/dc/w/images/nik.gif".equals(imgSrc)) {
//                                    	nick = nick+"("+uid+")";
//                                    }

                                    if ("mini".equals(Gall)) {
                                        Element span = writerTd.selectFirst("span.nickname");
                                        displayName = (span != null ? span.text().trim() : nick);
                                        //if ("ㅇㅇ".equals(displayName) && !uid.isEmpty()) displayName += "(" + uid + ")";
                                    }
                                    else {
                                        displayName = nick;
                                    }
//                                    if (displayName.equals("ㅇㅇ")) {
//                                    	displayName+="("+ip+")";
//                                    }
                                    AuthorBox.add(displayName);
                                    IpBox.add(ip);
                                    IDBox.add(uid);
                                    // System.out.println(displayName+" "+ip+" "+uid);
                                    // 글번호, 조회, 추천, 날짜
                                    Element view = tag.selectFirst("td.gall_count");
                                    Element recommend = tag.selectFirst("td.gall_recommend");
                                    Element reple = tag.selectFirst("span.reply_num");
                                    Element geulnum = tag.selectFirst("td.gall_num");
                                    Element day = tag.selectFirst("td.gall_date");

                                    days.add(day.text());
                                    ViewBox.add(Integer.parseInt(view.text()));
                                    RecomBox.add(Integer.parseInt(recommend.text()));

                                    if (reple != null) {
                                        RepleTrueBox.add(geulnum.text());
                                        String ra = reple.text();
                                        String repl = ra.contains("/") ? ra.substring(1, ra.length() - 1).split("/")[0] : ra.replaceAll("[\\[\\]]", "");
                                        RepleBox.add(Integer.parseInt(repl));
                                    } else {
                                        RepleBox.add(0);
                                    }
                                }
                            }
                        }

                        int done = completedPages.incrementAndGet();
                        if (done % 5 == 0 || done == (end - start + 1)) {
                            System.out.println("페이지 진행: " + done + "/" + (end - start + 1));
                        }

                        break; // 성공 시 반복문 탈출
                    } catch (Exception e) {
                        if (retries == 0) {
                            System.out.println("페이지 파싱 실패: " + currentPage);
                        } else {
                            Thread.sleep(300); // 재시도 간 대기
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

        return new CrawlerResult(AuthorBox,IDBox,IpBox, ViewBox, RecomBox, RepleBox, RepleTrueBox, days,total_geul);
    }
}
