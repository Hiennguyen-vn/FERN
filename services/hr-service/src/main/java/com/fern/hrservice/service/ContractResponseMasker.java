package com.fern.hrservice.service;

import com.fern.hrservice.dto.HrResponses.ContractResponse;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import org.springframework.stereotype.Component;

@Component
public class ContractResponseMasker {
    public ContractResponse maskForPrincipal(FernPrincipal principal, ContractResponse response) {
        if (response == null || canReadDetail(principal)) {
            return response;
        }
        return new ContractResponse(
                response.id(),
                response.employeeId(),
                response.employmentType(),
                response.salaryType(),
                null,
                response.regionId(),
                null,
                response.contractStatus(),
                response.startDate(),
                response.endDate()
        );
    }

    public boolean canReadDetail(FernPrincipal principal) {
        return principal != null && principal.permissions().contains(PermissionCodes.HR_CONTRACT_DETAIL_READ);
    }
}
