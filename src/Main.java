import java.util.*;
import crawler.page_parser;
import crawler.subResult;
import crawler.comment_parser;
import counter.CustomAnalyzer;
import crawler.CrawlerResult;

public class Main{
    public static void main(String[] args) throws InterruptedException{
        Scanner sc = new Scanner(System.in);
        System.out.print("TYPE:");
    	String TYPE = sc.next();
    	System.out.print("ID:");
        String ID = sc.next();
        System.out.print("시작 페이지:");
        int start_page = sc.nextInt();
        System.out.print("종료 페이지:");
        int end_page = sc.nextInt();
        long start = System.currentTimeMillis();
        CrawlerResult result = page_parser.Crawler(ID, TYPE,start_page,end_page,10);
        ArrayList<String> gall_nums = result.RepleTrueBox;
//        subResult commsub = comment_parser.geulp(ID, TYPE, gall_nums, 120);
//        ArrayList<String> comment_names = commsub.Names;
//        ArrayList<String> comment_uids = commsub.IDs;
//        ArrayList<String> comment_ips = commsub.Ips;
        ///comment_parser.geulp(2);
        ArrayList<String> Authors = result.authorBox;
        ArrayList<String> UIDs = result.IDBox;
        ArrayList<String> Ips = result.IpBox;
        ArrayList<Integer> views = result.viewBox;
        ArrayList<Integer> recoms = result.recomBox;
        ArrayList<Integer> reples = result.repleBox;
        ArrayList<String> days = result.DayBox;
        CustomAnalyzer analyzer = new CustomAnalyzer();
        analyzer.analyzeData(Authors,UIDs,Ips, views, recoms, reples);
//        analyzer.applyCommList(comment_names,comment_uids,comment_ips);
        analyzer.printSummary("example.txt");
        analyzer.dayprinter(days);
        System.out.printf("총소요: ",(System.currentTimeMillis()-start)/1000,"초\n");
        System.out.printf("누적 게시글: %d\n",result.total_geul);
        System.out.printf("대상 타겟: ",gall_nums.size(),"개");
    }
}

//import java.net.URI;
//import java.net.http.*;
//import java.time.Duration;
//import java.util.*;
//import java.util.concurrent.*;
//
//public class Main {
//
//    public static void main(String[] args) {
//        String ID = "bornin10";
//        String gallType = "mini";
//        int startNo = 222200;
//        int endNo = 223600;
//        int concurrency = 10;
//
//        HttpClient client = HttpClient.newBuilder()
//                .connectTimeout(Duration.ofSeconds(5))
//                .executor(Executors.newFixedThreadPool(concurrency))
//                .build();
//
//        List<String> allNames = Collections.synchronizedList(new ArrayList<>());
//        List<CompletableFuture<Void>> futures = new ArrayList<>();
//        long start = System.currentTimeMillis();
//        for (int no = startNo; no <= endNo; no++) {
//            final int articleNo = no;
//
//            String payload;
//            String referer;
//
//            if ("mini".equals(gallType)) {
//                payload = "id=" + ID + "&no=" + articleNo + "&cmt_id=" + ID + "&cmt_no=" + articleNo +
//                        "&focus_cno=&focus_pno=&e_s_n_o=3eabc219ebdd65ff3eef86e54781" +
//                        "&comment_page=1&sort=R&prevCnt=&board_type=&_GALLTYPE_=MI&secret_article_key=";
//                referer = "https://gall.dcinside.com/mini/board/view/?id=" + ID + "&no=" + articleNo;
//            } else if ("m".equals(gallType)) {
//                payload = "id=" + ID + "&no=" + articleNo + "&cmt_id=" + ID + "&cmt_no=" + articleNo +
//                        "&focus_cno=&focus_pno=&e_s_n_o=3eabc219ebdd65ff3eef86e54781" +
//                        "&comment_page=1&sort=R&prevCnt=&board_type=&_GALLTYPE_=M&secret_article_key=";
//                referer = "https://gall.dcinside.com/mgallery/board/view/?id=" + ID + "&no=" + articleNo;
//            } else {
//                payload = "id=" + ID + "&no=" + articleNo + "&cmt_id=" + ID + "&cmt_no=" + articleNo +
//                        "&focus_cno=&focus_pno=&e_s_n_o=3eabc219ebdd65ff3eef86e54781" +
//                        "&comment_page=1&sort=R&prevCnt=&board_type=&_GALLTYPE_=G&secret_article_key=";
//                referer = "https://gall.dcinside.com/board/view/?id=" + ID + "&no=" + articleNo;
//            }
//          
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create("https://gall.dcinside.com/board/comment/"))
//                    .timeout(Duration.ofSeconds(5))
//                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
//                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
//                    .header("X-Requested-With", "XMLHttpRequest")
//                    .header("Referer", referer)
//                    .POST(HttpRequest.BodyPublishers.ofString(payload))
//                    .build();
//            
//            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
//                    .thenAccept(response -> {
//                        String body = response.body();
//                        // System.out.println(body); // 필요시 주석 해제
//                        List<String> names = extractNames(body);
//                        allNames.addAll(names);
//                        System.out.println("글 번호: " + articleNo + " -> 댓글 수: " + names.size());
//                    })
//                    .exceptionally(ex -> {
//                        System.out.println("글 번호: " + articleNo + " 요청 실패 - " + ex.getMessage());
//                        return null;
//                    });
//
//            futures.add(future);
//        }
//
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//        System.out.println("모든 요청 완료");
//        System.out.println("총 수집된 name 수: " + allNames.size());
//        System.out.println("모든 name: " + allNames);
//        System.out.println(1000/((System.currentTimeMillis()-start)/(endNo-startNo))+"rp/s");
//    }
//
//    // name 추출 + 유니코드 디코딩
//    private static List<String> extractNames(String json) {
//        List<String> names = new ArrayList<>();
//        int idx = 0;
//
//        while ((idx = json.indexOf("\"name\":\"", idx)) != -1) {
//            idx += 8; // "name":" 길이
//            int end = json.indexOf("\"", idx);
//            if (end == -1) break;
//            String rawName = json.substring(idx, end);
//
//            String decodedName = decodeUnicode(rawName);
//
//            if (!decodedName.equals("댓글돌이")) {
//                names.add(decodedName);
//            }
//            idx = end;
//        }
//
//        return names;
//    }
//
//    private static String decodeUnicode(String input) {
//        try {
//            Properties p = new Properties();
//            p.load(new java.io.StringReader("a=" + input));
//            return p.getProperty("a");
//        } catch (Exception e) {
//            return input;
//        }
//    }
//}

