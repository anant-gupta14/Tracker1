package com.dev.infinitoz.tracker1.historyRecyclerView;

public class HistoryObject {
    private String rideId,time;


    public HistoryObject(String rideId, String time){
        this.rideId = rideId;
        this.time = time;

    }

    public String getTime() {
        return time;
    }

    public String getRideId() {
        return rideId;
    }
}
