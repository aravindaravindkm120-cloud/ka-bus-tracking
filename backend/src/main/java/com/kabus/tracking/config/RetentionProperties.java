package com.kabus.tracking.config;

public class RetentionProperties {

    private int locationHistoryDays = 30;

    public int getLocationHistoryDays() {
        return locationHistoryDays;
    }

    public void setLocationHistoryDays(int locationHistoryDays) {
        this.locationHistoryDays = locationHistoryDays;
    }
}