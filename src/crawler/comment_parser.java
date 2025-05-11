package crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;
import java.util.List;

public class comment_parser {
    public static subResult geulp(String ID,String Gall,ArrayList<String> gall_nums){
        ///https://m.dcinside.com/mini/bornin10/119344
        ArrayList<String> NAMES = new ArrayList<>();
        ArrayList<String> dates = new ArrayList<>();
        String baseurl;
        if (Gall.equals("mini")){
            baseurl = "https://m.dcinside.com/mini/"+ID+"/";
        }
        else{
            baseurl = "https://m.dcinside.com/board/"+ID+"/";
        }
        List<Double> times = new ArrayList<>();
        int breakcount = 0;
        for (int f =0;f<gall_nums.size();f++){
            String key =baseurl+gall_nums.get(f);
            ///System.out.println(key);
            try{
                long checkt = System.currentTimeMillis();
                Document doc = Jsoup.connect(key)
                    .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Connection", "keep-alive")
                    .timeout(800)
                    .get();
                Elements names = doc.select("li.comment");
                Elements names_add = doc.select("li.comment-add");
                ///System.out.println("\n소스 불러옴"+key+"\n");
                ///System.out.println(names.text());
                for (Element tag : names){
                    Element delete = tag.selectFirst("div.delted");
                    if (delete!=null){continue;}
                    Element dayv = tag.selectFirst("span.date");
                    String[] mnda = dayv.text().split(" ");
                    dates.add(mnda[0]);
                    String name = tag.selectFirst("a.nick").text();
                    if (!name.equals("ㅇㅇ")){
                        ///System.out.print(name+" ");
                        NAMES.add(name);
                    }
                    else if (name.equals("ㅇㅇ")){
                        Element it = tag.selectFirst("span.blockCommentId");
                        if (it!=null){
                            String usid = it.attr("data-info");
                            NAMES.add(name+"("+usid+")");
                        }
                        else{NAMES.add(name);}
                    }
                    else{
                        continue;
                    }
                }
                for (Element tag : names_add){
                    Element delete = tag.selectFirst("div.delted");
                    if (delete!=null){continue;}
                    Element dayv = tag.selectFirst("span.date");
                    String[] mnda = dayv.text().split(" ");
                    dates.add(mnda[0]);
                    String name_Add = tag.selectFirst("a.nick").text();
                    ///System.out.print(name_Add+" ");   
                    NAMES.add(name_Add);
                    if (!name_Add.equals("ㅇㅇ")){
                        ///System.out.print(name+" ");
                        NAMES.add(name_Add);
                    }
                    else if (name_Add.equals("ㅇㅇ")){
                        Element it = tag.selectFirst("span.blockCommentId");
                        if (it!=null){
                            String usid = it.attr("data-info");
                            NAMES.add(name_Add+"("+usid+")");
                        }
                        else{NAMES.add(name_Add);}
                    }
                    else{
                        continue;
                    }
                }
                if (f%50==0){
                    System.out.println("\n댓글 파싱 중 : "+(f+1)+"/"+gall_nums.size());
                    double sum=0;
                    for (double v : times) {
                       sum += v;
                    }
                    System.out.println("현재 속도 : "+(Math.round(sum/times.size()))+"ms(글당), 예상 남은시간 "+(Math.round(sum/times.size())*gall_nums.size()-Math.round(sum/times.size())*f)+"ms");
                }
                double es = System.currentTimeMillis()-checkt;
                times.add(es);
            
            } catch(Exception e){
                breakcount+=1;
                System.out.println("파싱오류 : "+key);
                continue;
            }
        }
        ///System.out.println("실제 소모 : "+(System.currentTimeMillis()-astim)+"ms");
        System.out.println("누락된 글 : "+breakcount+"개");
        return new subResult(NAMES, dates);
    }
}