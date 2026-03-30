package counter;

public class CustomClass {
    public String name;
    public String userID;
    public String ip;
    public String nicktype; // 추가: G(고정), S(비고정), D(유동)
    public int num;
    public int view;
    public int recom;
    public int reple;
    public int comm;

    public CustomClass(String name, String userID, String ip, String nicktype) {
        this.name = name;
        this.userID = userID != null ? userID : "";
        this.ip = ip != null ? ip : "";
        this.nicktype = nicktype != null ? nicktype : ""; // nicktype 저장
        this.num = 1;
        this.view = 0;
        this.recom = 0;
        this.reple = 0;
        this.comm = 0;
    }

    public void addMember(int view, int recom, int reple) {
        this.num += 1;
        this.view += view;
        this.recom += recom;
        this.reple += reple;
    }
}