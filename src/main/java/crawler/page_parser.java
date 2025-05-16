package crawler;

import java.util.ArrayList;
import org.jsoup.Jsoup;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
public class page_parser {
    public static CrawlerResult get(String ID, String Gall, int start, int end) {
        ArrayList<String> skipSubjects = new ArrayList<>();
        skipSubjects.add("고정");
        skipSubjects.add("공지");
        skipSubjects.add("설문");

        ArrayList<String> AuthorBox = new ArrayList<>();
        ArrayList<Integer> ViewBox = new ArrayList<>();
        ArrayList<Integer> RecomBox = new ArrayList<>();
        ArrayList<Integer> RepleBox = new ArrayList<>();
        ArrayList<String> RepleTrueBox = new ArrayList<>();
        ArrayList<String> days = new ArrayList<>();
        ArrayList<String> clocks = new ArrayList<>();
        String baseurl;
        if ("mini".equals(Gall)) {
            baseurl = "https://gall.dcinside.com/mini/board/lists/?id=" + ID;
        } else if ("m".equals(Gall)) {
            baseurl = "https://gall.dcinside.com/mgallery/board/lists/?id=" + ID;
        } else {
            baseurl = "https://gall.dcinside.com/board/lists/?id=" + ID;
        }
        for (int x = start; x <= end; x++) {
            String key = baseurl + "&page=" + x;
            try {
                Document doc = Jsoup.connect(key)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .header("Connection", "keep-alive")
                        .timeout(800)
                        .get();
                System.out.println("접속: " + key);
                Elements tr = doc.select("tr.ub-content");
                for (Element tag : tr) {
                    Element subjectTd = tag.selectFirst("td.gall_subject");
                    if (subjectTd != null && !skipSubjects.contains(subjectTd.text().trim())) {
                        Element name = tag.selectFirst("td.gall_writer");
                        Element view = tag.selectFirst("td.gall_count");
                        Element recommend = tag.selectFirst("td.gall_recommend");
                        Element reple = tag.selectFirst("span.reply_num");
                        Element geulnum = tag.selectFirst("td.gall_num");
                        Element day = tag.selectFirst("td.gall_date");
                        String[] clock = day.attr("title").split(" ");
                        clocks.add(clock[1]);
                        days.add(day.text());
                        String usid = name.attr("data-uid");
                        if (name != null) {
                            if (!name.text().equals("ㅇㅇ")){
                                AuthorBox.add(name.text());
                            }
                            else{
                                AuthorBox.add(name.text()+"("+usid+")");
                            }
                            ViewBox.add(Integer.parseInt(view.text()));
                            RecomBox.add(Integer.parseInt(recommend.text()));
                            if (reple!=null){
                                ///System.out.println(reple.text());
                                RepleTrueBox.add(geulnum.text());
                                String ra = reple.text();
                                String repl = "";
                                if (ra.contains("/")){
                                    ra = ra.substring(1, ra.length() - 1);
                                    String[] parts = ra.split("/");
                                    if (parts.length > 0) {
                                        repl = parts[0]; // "12"
                                    }
                                }
                                else{
                                    repl = ra.replaceAll("[\\[\\]]", "");
                                }
                                int rp = Integer.parseInt(repl);
                                RepleBox.add(rp);
                            }
                            else{
                                RepleBox.add(0);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("파싱 실패: " + key);
                x-=1;
            }
        }
        System.out.println(RecomBox.size());
        return new CrawlerResult(AuthorBox,ViewBox,RecomBox,RepleBox,RepleTrueBox,days,clocks);
    }
}
