package com.codeshield.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RepositoryScanRequest {

    @NotNull
    private Long assetId;

    @NotBlank
    private String repositorySource;

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getRepositorySource() {
        return repositorySource;
    }

    public void setRepositorySource(String repositorySource) {
        this.repositorySource = repositorySource;
    }
}
