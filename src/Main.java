import java.util.*;
import crawler.page_parser;
import crawler.subResult;
import crawler.comment_Parser;
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
        subResult commsub = comment_Parser.geulp(ID, TYPE, gall_nums, 120);
        ArrayList<String> comment_names = commsub.Names;
        ArrayList<String> comment_uids = commsub.IDs;
        ArrayList<String> comment_ips = commsub.Ips;
        ArrayList<String> Authors = result.authorBox;
        ArrayList<String> UIDs = result.IDBox;
        ArrayList<String> Ips = result.IpBox;
        ArrayList<Integer> views = result.viewBox;
        ArrayList<Integer> recoms = result.recomBox;
        ArrayList<Integer> reples = result.repleBox;
        ArrayList<String> days = result.DayBox;
        CustomAnalyzer analyzer = new CustomAnalyzer();
        analyzer.analyzeData(Authors,UIDs,Ips, views, recoms, reples);
        analyzer.applyCommList(comment_names,comment_uids,comment_ips);
        analyzer.printSummary("example.txt");
        analyzer.dayprinter(days);
        System.out.println("총소요: "+(System.currentTimeMillis()-start)/1000+"초");
        System.out.println("누적 게시글: "+Authors.size()+"개");
        System.out.println("대상 타겟: "+gall_nums.size()+"개");
    }
}