package com.data_dive.com.clipster;
import org.json.JSONArray;

public class Clips {

    private static Clips instance;

    // Global variable
    private JSONArray clips;

    // Restrict the constructor from being instantiated
    private Clips(){}

    public void setData(JSONArray clips){
        this.clips = clips;
    }
    public JSONArray getData(){
        return this.clips;
    }

    public static synchronized Clips getInstance(){
        if(instance==null){
            instance = new Clips();
        }
        return instance;
    }
}