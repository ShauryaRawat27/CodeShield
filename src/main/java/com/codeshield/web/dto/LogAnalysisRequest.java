package com.codeshield.web.dto;

import jakarta.validation.constraints.NotNull;

public class LogAnalysisRequest {

    @NotNull
    private Long assetId;

    private boolean internetFacing;
    private String logText;

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public boolean isInternetFacing() {
        return internetFacing;
    }

    public void setInternetFacing(boolean internetFacing) {
        this.internetFacing = internetFacing;
    }

    public String getLogText() {
        return logText;
    }

    public void setLogText(String logText) {
        this.logText = logText;
    }
}
