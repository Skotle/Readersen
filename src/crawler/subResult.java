package crawler;

import java.util.ArrayList;

public class subResult {
    public ArrayList<String> Names;
    public ArrayList<String> IDs;
    public ArrayList<String> Ips;
    public ArrayList<String> Days;
    public subResult(ArrayList<String> Names,ArrayList<String> IDs,ArrayList<String> Ips,ArrayList<String> Days){
        this.Names = Names;
        this.IDs = IDs;
        this.Ips = Ips;
        this.Days = Days;
    }
}