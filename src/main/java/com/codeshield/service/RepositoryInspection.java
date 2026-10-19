package com.codeshield.service;

import com.codeshield.web.dto.ScanRequest;

import java.util.List;

public record RepositoryInspection(
        ScanRequest scanRequest,
        List<String> inspectedFiles
) {
}
