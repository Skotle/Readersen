package crawler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;//TIP 코드를 <b>실행</b>하려면 <shortcut actionId="Run"/>을(를) 누르거나
// 에디터 여백에 있는 <icon src="AllIcons.Actions.Execute"/> 아이콘을 클릭하세요.
public class comment_Parser {
    public static subResult getcom(String ID,String gall,ArrayList<String> gall_nums) {
        int count = 0;
        ArrayList<String> names = new ArrayList<>();
        int count2= 0;
        String baseurl = "";
        if (gall.equals("mini")){
            baseurl = "https://m.dcinside.com/mini/"+ID+"/";
        }
        else{
            baseurl = "https://gall.dcinside.com/board/"+ID+"/";
        }
        for (String x : gall_nums) {
            count2+=1;
            String key = baseurl + x;
            long atime = System.currentTimeMillis();
            try {
                ///System.out.println(key);
                Document doc = Jsoup.connect(key)
                        .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .header("Connection", "keep-alive")
                        .timeout(800)
                        .get();
                String commnum = doc.select("span.point-red").text();
                ///System.out.print(commnum);
                count+=Integer.parseInt(commnum);
                ArrayList<String> temp = new ArrayList<>();
                Elements commentlst = doc.select("li.comment");
                Elements commentlst2 = doc.select("li.comment-add");
                for (Element tag : commentlst) {
                    Element delete = tag.selectFirst("div.delted");
                    if (delete!=null) {
                        continue;
                    }
                    String name = tag.selectFirst("a.nick").text();
                    names.add(name);
                    temp.add(name);
                }
                for (Element tag : commentlst2) {
                    Element delete = tag.selectFirst("div.delted");
                    if (delete!=null) {
                        continue;
                    }
                    String name = tag.selectFirst("a.nick").text();
                    names.add(name);
                    temp.add(name);
                }
                ///System.out.print(" : "+temp.size()+"\n");
                if (temp.size()!=Integer.parseInt(commnum)){System.out.println(", 집계불일치 - "+x);}
            } catch (Exception e) {
                System.out.print("건너뜀 - "+x);
                continue;
            }
            if (count%20==0) {
                System.out.println("현재 속도 : " + (System.currentTimeMillis() - atime) + "ms - "+count2+"/"+gall_nums.size()+"완료");
            }
        }
        System.out.println(gall_nums.size());
        System.out.println(names.size());
        System.out.println(count);
        return new subResult(names);
    }
}