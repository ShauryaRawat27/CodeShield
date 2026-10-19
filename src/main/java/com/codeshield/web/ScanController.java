package com.codeshield.web;

import com.codeshield.domain.Exposure;
import com.codeshield.domain.ScanRun;
import com.codeshield.repository.ApplicationAssetRepository;
import com.codeshield.repository.ScanRunRepository;
import com.codeshield.service.ScanOrchestrator;
import com.codeshield.web.dto.ScanRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ScanController {

    private final ApplicationAssetRepository assetRepository;
    private final ScanRunRepository scanRunRepository;
    private final ScanOrchestrator scanOrchestrator;

    public ScanController(
            ApplicationAssetRepository assetRepository,
            ScanRunRepository scanRunRepository,
            ScanOrchestrator scanOrchestrator
    ) {
        this.assetRepository = assetRepository;
        this.scanRunRepository = scanRunRepository;
        this.scanOrchestrator = scanOrchestrator;
    }

    @GetMapping("/scans")
    public String history(Model model) {
        model.addAttribute("scans", scanRunRepository.findTop10ByOrderByStartedAtDesc());
        return "scans";
    }

    @GetMapping("/scans/new")
    public String form(
            @RequestParam(required = false) Long assetId,
            Model model
    ) {
        ScanRequest request = new ScanRequest();
        var assets = assetRepository.findAll();
        if (assetId == null && !assets.isEmpty()) {
            assetId = assets.getFirst().getId();
        }
        if (assetId != null) {
            request = requestFromBaseline(assetId);
        }
        prepareForm(model, request);
        return "scan-form";
    }

    @PostMapping("/scans")
    public String run(
            @Valid @ModelAttribute("scanRequest") ScanRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, request);
            return "scan-form";
        }
        ScanRun scan = scanOrchestrator.run(request);
        redirectAttributes.addFlashAttribute(
                "message",
                "Scan completed with " + scan.getFindingsCount() + " finding(s)."
        );
        return "redirect:/findings";
    }

    private void prepareForm(Model model, ScanRequest request) {
        model.addAttribute("scanRequest", request);
        model.addAttribute("assets", assetRepository.findAll());
        model.addAttribute("exposures", Exposure.values());
    }

    private ScanRequest requestFromBaseline(Long assetId) {
        var asset = assetRepository.findById(assetId).orElseThrow();
        ScanRequest request = new ScanRequest();
        request.setAssetId(assetId);
        request.setJavaVersion(asset.getExpectedJavaVersion());
        request.setDatabaseExposure(asset.getExpectedDatabaseExposure());
        request.setContainerUser(asset.getExpectedContainerUser());
        request.setDockerSocketMounted(false);
        request.setImageDigest(asset.getExpectedImageDigest());
        request.setDependencyVersion(asset.getExpectedDependencyVersion());
        request.setInternetFacing(false);
        request.setKnownExploited(false);
        request.setEvidenceSource("Manual runtime observation");
        return request;
    }
}
