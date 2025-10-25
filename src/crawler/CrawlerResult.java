package crawler;

import java.util.ArrayList;

public class CrawlerResult {
    public ArrayList<String> authorBox;
    public ArrayList<String> IDBox;
    public ArrayList<String> IpBox;
    public ArrayList<Integer> viewBox;
    public ArrayList<Integer> recomBox;
    public ArrayList<Integer> repleBox;
    public ArrayList<String> RepleTrueBox;
    public ArrayList<String> DayBox;
    public int total_geul;

    public CrawlerResult(ArrayList<String> authorBox,ArrayList<String> IDBox,ArrayList<String> IpBox, ArrayList<Integer> viewBox,
                         ArrayList<Integer> recomBox, ArrayList<Integer> repleBox, ArrayList<String> RepleTrueBox,ArrayList<String> DayBox,int total) {
        this.authorBox = authorBox;
        this.IDBox = IDBox;
        this.IpBox = IpBox;
        this.viewBox = viewBox;
        this.recomBox = recomBox;
        this.repleBox = repleBox;
        this.RepleTrueBox = RepleTrueBox;
        this.DayBox = DayBox;
        this.total_geul = total;
    }
}
