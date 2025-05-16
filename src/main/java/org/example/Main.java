package org.example;
import crawler.CrawlerResult;
import crawler.comment_Parser;
import crawler.page_parser;
import counter.CustomAnalyzer;
import java.util.ArrayList;
import crawler.subResult;
import counter.SpecialAnalyzer;

public class Main {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        String ID = "bornin10";
        String gall = "mini";
        CrawlerResult result = page_parser.get(ID, gall, 1, 218);
        ArrayList<String> gall_nums = result.RepleTrueBox;
        subResult commsub = comment_Parser.getcom(ID,gall,gall_nums);

        ArrayList<String> Authors = result.authorBox;
        ArrayList<Integer> views = result.viewBox;
        ArrayList<Integer> recoms = result.recomBox;
        ArrayList<Integer> reples = result.repleBox;
        ArrayList<String> days = result.DayBox;
        CustomAnalyzer analyzer = new CustomAnalyzer();

        analyzer.analyzeData(Authors, views, recoms, reples);

        ArrayList<String> comment_names = commsub.Names;

        analyzer.applyCommList(comment_names);
        analyzer.printSummary();
        analyzer.dayprinter(days);
        System.out.println(System.currentTimeMillis() - start + "ms");
        SpecialAnalyzer viewv = new SpecialAnalyzer(views);
        SpecialAnalyzer recomv = new SpecialAnalyzer(recoms);
        SpecialAnalyzer replev = new SpecialAnalyzer(reples);
        viewv.printStats();
        recomv.printStats();
        replev.printStats();
        analyzer.timeprinter(result.timeBox);
    }
}