package com.codeshield.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("codeshield.logs")
public class LogAnalysisProperties {

    private long maximumContentBytes = 2_000_000;
    private int maximumLines = 20_000;
    private int authenticationFailureThreshold = 5;
    private int sensitivePathThreshold = 3;
    private int serverErrorThreshold = 10;

    public long getMaximumContentBytes() {
        return maximumContentBytes;
    }

    public void setMaximumContentBytes(long maximumContentBytes) {
        this.maximumContentBytes = maximumContentBytes;
    }

    public int getMaximumLines() {
        return maximumLines;
    }

    public void setMaximumLines(int maximumLines) {
        this.maximumLines = maximumLines;
    }

    public int getAuthenticationFailureThreshold() {
        return authenticationFailureThreshold;
    }

    public void setAuthenticationFailureThreshold(int authenticationFailureThreshold) {
        this.authenticationFailureThreshold = authenticationFailureThreshold;
    }

    public int getSensitivePathThreshold() {
        return sensitivePathThreshold;
    }

    public void setSensitivePathThreshold(int sensitivePathThreshold) {
        this.sensitivePathThreshold = sensitivePathThreshold;
    }

    public int getServerErrorThreshold() {
        return serverErrorThreshold;
    }

    public void setServerErrorThreshold(int serverErrorThreshold) {
        this.serverErrorThreshold = serverErrorThreshold;
    }
}
