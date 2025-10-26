package crawler;

import java.util.ArrayList;

public class subResult {
    public ArrayList<String> Names;
    public ArrayList<String> IDs;
    public ArrayList<String> Ips;
    public subResult(ArrayList<String> Names,ArrayList<String> IDs,ArrayList<String> Ips){
        this.Names = Names;
        this.IDs = IDs;
        this.Ips = Ips;
    }
}