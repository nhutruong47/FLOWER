package com.flowerchain.audit.web;

import com.flowerchain.audit.api.AuditApi;
import com.flowerchain.audit.api.AuditLogDto;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.FlowerChainPrincipal;
import com.flowerchain.shared.api.PageResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {

    private final AuditApi auditApi;
    private final BranchAuthorizationGuard branchGuard;

    public AdminAuditController(AuditApi auditApi, BranchAuthorizationGuard branchGuard) {
        this.auditApi = auditApi;
        this.branchGuard = branchGuard;
    }

    @GetMapping
    public PageResponse<AuditLogDto> listAuditLogs(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        FlowerChainPrincipal principal =
                branchGuard.requireAnyRole("SYSTEM_OWNER", "HEAD_OFFICE_ADMIN", "BRANCH_MANAGER");
        if (branchId != null) {
            branchGuard.assertCanAccessBranch(branchId);
        }
        return auditApi.query(
                principal.authorizedBranchIds(),
                principal.globalAccess(),
                Optional.ofNullable(branchId),
                Optional.ofNullable(category),
                page,
                size);
    }
}
