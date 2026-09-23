package com.kabus.tracking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Api api = new Api();
    private RateLimit rateLimit = new RateLimit();
    private Gps gps = new Gps();
    private LiveStatus liveStatus = new LiveStatus();
    private Retention retention = new Retention();
    private Seed seed = new Seed();
    private Routing routing = new Routing();
    private Search search = new Search();

    public static class Jwt {
        private String secret;
        private long expirationMs = 900_000L;
        private long refreshExpirationMs = 2_592_000_000L;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpirationMs() { return expirationMs; }
        public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
        public long getRefreshExpirationMs() { return refreshExpirationMs; }
        public void setRefreshExpirationMs(long refreshExpirationMs) { this.refreshExpirationMs = refreshExpirationMs; }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");

        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class Api {
        private String baseUrl = "http://localhost:8080";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    public static class RateLimit {
        private int loginPerMinute = 5;
        private int gpsPerMinute = 120;
        private int publicRequestsPerMinute = 120;

        public int getLoginPerMinute() { return loginPerMinute; }
        public void setLoginPerMinute(int loginPerMinute) { this.loginPerMinute = loginPerMinute; }
        public int getGpsPerMinute() { return gpsPerMinute; }
        public void setGpsPerMinute(int gpsPerMinute) { this.gpsPerMinute = gpsPerMinute; }
        public int getPublicRequestsPerMinute() { return publicRequestsPerMinute; }
        public void setPublicRequestsPerMinute(int publicRequestsPerMinute) { this.publicRequestsPerMinute = publicRequestsPerMinute; }
    }

    public static class Gps {
        private long minIntervalMs = 3_000L;
        private long timestampMaxSkewMs = 30_000L;

        public long getMinIntervalMs() { return minIntervalMs; }
        public void setMinIntervalMs(long minIntervalMs) { this.minIntervalMs = minIntervalMs; }
        public long getTimestampMaxSkewMs() { return timestampMaxSkewMs; }
        public void setTimestampMaxSkewMs(long timestampMaxSkewMs) { this.timestampMaxSkewMs = timestampMaxSkewMs; }
    }

    public static class Seed {
        private String adminUsername = "admin";
        private String adminPassword = "Admin@123";
        private String adminEmail = "admin@kabus.local";
        private String adminFullName = "Super Administrator";

        public String getAdminUsername() { return adminUsername; }
        public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
        public String getAdminPassword() { return adminPassword; }
        public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
        public String getAdminEmail() { return adminEmail; }
        public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
        public String getAdminFullName() { return adminFullName; }
        public void setAdminFullName(String adminFullName) { this.adminFullName = adminFullName; }
    }

    public static class LiveStatus {
        private int liveSeconds = 60;
        private int staleSeconds = 600;

        public int getLiveSeconds() { return liveSeconds; }
        public void setLiveSeconds(int liveSeconds) { this.liveSeconds = liveSeconds; }
        public int getStaleSeconds() { return staleSeconds; }
        public void setStaleSeconds(int staleSeconds) { this.staleSeconds = staleSeconds; }
    }

public static class Retention {
        private int locationHistoryDays = 30;

        public int getLocationHistoryDays() { return locationHistoryDays; } 
        public void setLocationHistoryDays(int locationHistoryDays) { this.locationHistoryDays = locationHistoryDays; }
    }

    /** Road-network routing (OSRM) settings for passenger route geometry. */
    public static class Routing {
        private String baseUrl = "https://router.project-osrm.org";
        private int timeoutMs = 10_000;
        private int maxRetries = 1;
        private int maxStops = 200;
        private double validationRadiusKm = 5.0;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public int getMaxStops() { return maxStops; }
        public void setMaxStops(int maxStops) { this.maxStops = maxStops; }
        public double getValidationRadiusKm() { return validationRadiusKm; }
        public void setValidationRadiusKm(double validationRadiusKm) { this.validationRadiusKm = validationRadiusKm; }
    }

    /** Passenger connecting-journey search settings. */
    public static class Search {
        private int minTransferMinutes = 10;

        public int getMinTransferMinutes() { return minTransferMinutes; }
        public void setMinTransferMinutes(int minTransferMinutes) { this.minTransferMinutes = minTransferMinutes; }
    }

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public LiveStatus getLiveStatus() { return liveStatus; }
    public void setLiveStatus(LiveStatus liveStatus) { this.liveStatus = liveStatus; }
    public Retention getRetention() { return retention; }
    public void setRetention(Retention retention) { this.retention = retention; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }
    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
    public Gps getGps() { return gps; }
    public void setGps(Gps gps) { this.gps = gps; }
    public Seed getSeed() { return seed; }
    public void setSeed(Seed seed) { this.seed = seed; }
    public Routing getRouting() { return routing; }
    public void setRouting(Routing routing) { this.routing = routing; }
    public Search getSearch() { return search; }
    public void setSearch(Search search) { this.search = search; }
}