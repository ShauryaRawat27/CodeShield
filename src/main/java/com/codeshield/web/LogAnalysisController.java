package com.codeshield.web;

import com.codeshield.config.LogAnalysisProperties;
import com.codeshield.service.LogAnalysisResult;
import com.codeshield.service.LogAnalysisService;
import com.codeshield.repository.ApplicationAssetRepository;
import com.codeshield.web.dto.LogAnalysisRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Controller
public class LogAnalysisController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".log", ".txt", ".csv", ".json", ".jsonl");

    private final ApplicationAssetRepository assetRepository;
    private final LogAnalysisService logAnalysisService;
    private final LogAnalysisProperties properties;

    public LogAnalysisController(
            ApplicationAssetRepository assetRepository,
            LogAnalysisService logAnalysisService,
            LogAnalysisProperties properties
    ) {
        this.assetRepository = assetRepository;
        this.logAnalysisService = logAnalysisService;
        this.properties = properties;
    }

    @GetMapping("/logs")
    public String form(Model model) {
        LogAnalysisRequest request = new LogAnalysisRequest();
        if (!assetRepository.findAll().isEmpty()) {
            request.setAssetId(assetRepository.findAll().getFirst().getId());
        }
        prepareForm(model, request);
        return "log-analysis";
    }

    @PostMapping("/logs")
    public String analyze(
            @Valid @ModelAttribute("logAnalysisRequest") LogAnalysisRequest request,
            BindingResult bindingResult,
            @RequestParam(name = "logFile", required = false) MultipartFile logFile,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, request);
            return "log-analysis";
        }

        try {
            String content = combineInput(request.getLogText(), logFile);
            String sourceReference = logFile != null && !logFile.isEmpty()
                    ? safeFileName(logFile.getOriginalFilename())
                    : "pasted log text";
            LogAnalysisResult result = logAnalysisService.analyze(
                    request.getAssetId(), content, request.isInternetFacing(), sourceReference
            );
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Log analysis processed " + result.processedLines() + " line(s) and created "
                            + result.scanRun().getFindingsCount() + " security signal(s)."
            );
            return "redirect:/findings";
        } catch (IllegalArgumentException | IOException exception) {
            bindingResult.reject("logs.invalid", exception.getMessage());
            prepareForm(model, request);
            return "log-analysis";
        }
    }

    private String combineInput(String pastedText, MultipartFile logFile) throws IOException {
        String text = pastedText == null ? "" : pastedText;
        if (logFile == null || logFile.isEmpty()) {
            return text;
        }
        if (logFile.getSize() > properties.getMaximumContentBytes()) {
            throw new IllegalArgumentException("The selected log file is larger than the allowed input size.");
        }
        String fileName = safeFileName(logFile.getOriginalFilename());
        if (!hasAllowedExtension(fileName)) {
            throw new IllegalArgumentException("Choose a .log, .txt, .csv, .json, or .jsonl text file.");
        }
        String fileText = new String(logFile.getBytes(), StandardCharsets.UTF_8);
        return text.isBlank() ? fileText : text + System.lineSeparator() + fileText;
    }

    private boolean hasAllowedExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "uploaded log file";
        }
        String normalized = fileName.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private void prepareForm(Model model, LogAnalysisRequest request) {
        model.addAttribute("logAnalysisRequest", request);
        model.addAttribute("assets", assetRepository.findAll());
        model.addAttribute("maximumLines", properties.getMaximumLines());
        model.addAttribute("maximumMegabytes", properties.getMaximumContentBytes() / 1_000_000.0);
    }
}
