package com.kabus.tracking.config;

/**
 * Live-status and retention defaults. Concrete runtime values live in
 * <code>system_settings</code> (see SettingsService) and overrides these.
 */
public class LiveStatusProperties {

    private int liveSeconds = 60;
    private int staleSeconds = 600;

    public int getLiveSeconds() {
        return liveSeconds;
    }

    public void setLiveSeconds(int liveSeconds) {
        this.liveSeconds = liveSeconds;
    }

    public int getStaleSeconds() {
        return staleSeconds;
    }

    public void setStaleSeconds(int staleSeconds) {
        this.staleSeconds = staleSeconds;
    }
}