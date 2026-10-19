package com.codeshield.service;

import com.codeshield.domain.ScanRun;

public record LogAnalysisResult(ScanRun scanRun, int processedLines) {
}
