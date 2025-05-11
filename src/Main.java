import java.util.ArrayList;
import crawler.page_parser;
import crawler.subResult;
import crawler.comment_parser;
import counter.CustomAnalyzer;
import crawler.CrawlerResult;

public class Main{
    public static void main(String[] args){
        String ID = "bornin10";
        String Gall = "mini";
        int start_page = 1;
        int end_page = 615;
        long start = System.currentTimeMillis();
        CrawlerResult result = page_parser.Crawler(ID, Gall,start_page,end_page);
        ArrayList<String> gall_nums = result.RepleTrueBox;
        subResult commsub = comment_parser.geulp(ID,Gall, gall_nums);
        ArrayList<String> comment_names = commsub.Names;
        ///comment_parser.geulp(2);
        ArrayList<String> Authors = result.authorBox;
        ArrayList<Integer> views = result.viewBox;
        ArrayList<Integer> recoms = result.recomBox;
        ArrayList<Integer> reples = result.repleBox;
        ArrayList<String> days = result.DayBox;
        ArrayList<String> subdays = commsub.Days;
        days.addAll(subdays);

        CustomAnalyzer analyzer = new CustomAnalyzer();
        analyzer.analyzeData(Authors, views, recoms, reples);
        analyzer.applyCommList(comment_names);
        analyzer.printSummary();
        analyzer.dayprinter(days);
        System.out.println(System.currentTimeMillis()-start+"ms");
        System.out.println(gall_nums.size());
    }
}