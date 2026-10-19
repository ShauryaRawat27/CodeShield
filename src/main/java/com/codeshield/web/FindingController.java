package com.codeshield.web;

import com.codeshield.domain.Finding;
import com.codeshield.domain.FindingStatus;
import com.codeshield.repository.FindingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class FindingController {

    private final FindingRepository findingRepository;

    public FindingController(FindingRepository findingRepository) {
        this.findingRepository = findingRepository;
    }

    @GetMapping("/findings")
    public String list(@RequestParam(required = false) FindingStatus status, Model model) {
        List<Finding> findings = status == null
                ? findingRepository.findAllByOrderByRiskScoreDescCreatedAtDesc()
                : findingRepository.findByStatusOrderByRiskScoreDescCreatedAtDesc(status);
        model.addAttribute("findings", findings);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", FindingStatus.values());
        return "findings";
    }

    @GetMapping("/findings/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("finding", find(id));
        model.addAttribute("statuses", FindingStatus.values());
        return "finding-detail";
    }

    @PostMapping("/findings/{id}/status")
    public String updateStatus(
            @PathVariable Long id,
            @RequestParam FindingStatus status,
            RedirectAttributes redirectAttributes
    ) {
        Finding finding = find(id);
        finding.setStatus(status);
        findingRepository.save(finding);
        redirectAttributes.addFlashAttribute("message", "Finding status updated to " + status + ".");
        return "redirect:/findings/" + id;
    }

    private Finding find(Long id) {
        return findingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Finding not found"));
    }
}
