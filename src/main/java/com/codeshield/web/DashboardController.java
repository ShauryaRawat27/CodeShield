package com.codeshield.web;

import com.codeshield.repository.FindingRepository;
import com.codeshield.repository.ScanRunRepository;
import com.codeshield.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final FindingRepository findingRepository;
    private final ScanRunRepository scanRunRepository;

    public DashboardController(
            DashboardService dashboardService,
            FindingRepository findingRepository,
            ScanRunRepository scanRunRepository
    ) {
        this.dashboardService = dashboardService;
        this.findingRepository = findingRepository;
        this.scanRunRepository = scanRunRepository;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("stats", dashboardService.stats());
        model.addAttribute("recentFindings", findingRepository.findTop8ByOrderByCreatedAtDesc());
        model.addAttribute("recentScans", scanRunRepository.findTop10ByOrderByStartedAtDesc().stream().limit(4).toList());
        return "dashboard";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }
}
