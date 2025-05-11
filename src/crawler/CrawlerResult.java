package crawler;

import java.util.ArrayList;

public class CrawlerResult {
    public ArrayList<String> authorBox;
    public ArrayList<Integer> viewBox;
    public ArrayList<Integer> recomBox;
    public ArrayList<Integer> repleBox;
    public ArrayList<String> RepleTrueBox;
    public ArrayList<String> DayBox;

    public CrawlerResult(ArrayList<String> authorBox, ArrayList<Integer> viewBox,
                         ArrayList<Integer> recomBox, ArrayList<Integer> repleBox, ArrayList<String> RepleTrueBox,ArrayList<String> DayBox) {
        this.authorBox = authorBox;
        this.viewBox = viewBox;
        this.recomBox = recomBox;
        this.repleBox = repleBox;
        this.RepleTrueBox = RepleTrueBox;
        this.DayBox = DayBox;
    }
}
