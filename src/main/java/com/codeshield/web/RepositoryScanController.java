package com.codeshield.web;

import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.ScanRun;
import com.codeshield.repository.ApplicationAssetRepository;
import com.codeshield.service.RepositoryInspection;
import com.codeshield.service.RepositoryInspectionService;
import com.codeshield.service.ScanOrchestrator;
import com.codeshield.web.dto.RepositoryScanRequest;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RepositoryScanController {

    private final ApplicationAssetRepository assetRepository;
    private final RepositoryInspectionService inspectionService;
    private final ScanOrchestrator scanOrchestrator;

    public RepositoryScanController(
            ApplicationAssetRepository assetRepository,
            RepositoryInspectionService inspectionService,
            ScanOrchestrator scanOrchestrator
    ) {
        this.assetRepository = assetRepository;
        this.inspectionService = inspectionService;
        this.scanOrchestrator = scanOrchestrator;
    }

    @GetMapping("/scans/repository")
    public String form(Model model) {
        RepositoryScanRequest request = new RepositoryScanRequest();
        if (!assetRepository.findAll().isEmpty()) {
            request.setAssetId(assetRepository.findAll().getFirst().getId());
        }
        prepareForm(model, request);
        return "repository-scan";
    }

    @PostMapping("/scans/repository")
    public String scan(
            @Valid @ModelAttribute("repositoryScanRequest") RepositoryScanRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, request);
            return "repository-scan";
        }

        ApplicationAsset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new EntityNotFoundException("Application asset not found"));
        try {
            RepositoryInspection inspection = inspectionService.inspect(asset, request.getRepositorySource());
            ScanRun scan = scanOrchestrator.run(inspection.scanRequest());
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Repository scan analyzed " + inspection.inspectedFiles().size()
                            + " configuration file(s) and created " + scan.getFindingsCount() + " finding(s)."
            );
            return "redirect:/findings";
        } catch (IllegalArgumentException exception) {
            bindingResult.rejectValue("repositorySource", "repository.invalid", exception.getMessage());
            prepareForm(model, request);
            return "repository-scan";
        }
    }

    private void prepareForm(Model model, RepositoryScanRequest request) {
        model.addAttribute("repositoryScanRequest", request);
        model.addAttribute("assets", assetRepository.findAll());
    }
}
