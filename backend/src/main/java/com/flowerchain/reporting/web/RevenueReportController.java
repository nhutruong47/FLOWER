package com.flowerchain.reporting.web;

import com.flowerchain.reporting.application.RevenueReportService;
import com.flowerchain.reporting.application.RevenueReportService.RevenueReportResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin revenue report (FR-034). Role, branch scope and date-range rules are enforced in the service. */
@RestController
public class RevenueReportController {

    private final RevenueReportService revenueReportService;

    public RevenueReportController(RevenueReportService revenueReportService) {
        this.revenueReportService = revenueReportService;
    }

    @GetMapping("/api/v1/admin/reports/revenue")
    public RevenueReportResponse getRevenueReport(
            @RequestParam(required = false) UUID branchId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return revenueReportService.generateRevenueReport(branchId, from, to);
    }
}
