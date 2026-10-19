package com.codeshield.web;

import com.codeshield.domain.ApplicationAsset;
import com.codeshield.domain.Criticality;
import com.codeshield.domain.Exposure;
import com.codeshield.repository.ApplicationAssetRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AssetController {

    private final ApplicationAssetRepository assetRepository;

    public AssetController(ApplicationAssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @GetMapping("/assets")
    public String assets(Model model) {
        if (!model.containsAttribute("assetForm")) {
            model.addAttribute("assetForm", new ApplicationAsset());
        }
        model.addAttribute("assets", assetRepository.findAll());
        model.addAttribute("criticalities", Criticality.values());
        model.addAttribute("exposures", Exposure.values());
        return "assets";
    }

    @PostMapping("/assets")
    public String create(
            @Valid @ModelAttribute("assetForm") ApplicationAsset asset,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("assets", assetRepository.findAll());
            model.addAttribute("criticalities", Criticality.values());
            model.addAttribute("exposures", Exposure.values());
            return "assets";
        }
        assetRepository.save(asset);
        redirectAttributes.addFlashAttribute("message", "Application baseline created successfully.");
        return "redirect:/assets";
    }
}
