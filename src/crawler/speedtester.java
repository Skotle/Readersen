package crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class speedtester {
    public static void main(String[] args){
        String key = "https://m.dcinside.com/mini/bornin10/";
        int count = 0;
        long start = System.currentTimeMillis();
        for (int x=0;x<300;x++){
            String url = key+x;
            try{
                Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 10; Mobile; rv:68.0) Gecko/68.0 Firefox/68.0")
                    .header("Accept", "*/*")
                    .header("Connection", "close")
                    .timeout(1000)
                    .get();
                Element tag = doc.selectFirst("a.nick");
                System.out.println(tag.text());

                }catch(Exception e){
                    count +=1;
                    continue;
                    ///System.out.println("에러");}
            }
            
        }
        System.out.println(count+", "+(System.currentTimeMillis()-start));
    }
}