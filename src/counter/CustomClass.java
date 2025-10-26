package counter;

public class CustomClass {
    public String name;
    public String userID;
    public String ip;
    public int num;
    public int view;
    public int recom;
    public int reple;
    public int comm;

    public CustomClass(String name,String userID, String ip) {
        this.name = name;
        this.userID = userID != null ? userID : "";
        this.ip = ip != null ? ip : "";
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
