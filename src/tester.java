import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.HashMap;
import java.util.Map;

public class tester {

    public static void main(String[] args) {
        String key = "https://gall.dcinside.com/mini/board/lists/?id=bornin10";
        for (int x=1;x<=100;x++) {
        	double start = System.currentTimeMillis();
        	try {
        		Document doc = Jsoup.connect(key)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .header("Connection", "keep-alive")
                        .get();
        		System.out.println("접속됨 : "+(System.currentTimeMillis()-start));
        	}
        	catch (Exception e) {System.out.println("불가");}
        }
    }
}

