package com.rootcause.foshol.review.application;

import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.domain.ReviewException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReviewDistrictGuard {

    private final OfficerLookupApi officers;
    private final ReviewQueryPort reads;

    public ReviewDistrictGuard(OfficerLookupApi officers, ReviewQueryPort reads) {
        this.officers = officers;
        this.reads = reads;
    }

    public void requireTaskInCallerDistrict(UUID callerId, UUID taskId) {
        var row = reads.findQueueRow(taskId).orElseThrow(ReviewException::taskNotFound);
        requireDistrict(callerId, row.districtCode());
    }

    public void requireDistrict(UUID callerId, String caseDistrictCode) {
        String callerDistrict = officers
                .findById(callerId)
                .map(o -> o.districtCode())
                .orElseThrow(ReviewException::taskNotFound);
        if (caseDistrictCode == null || !callerDistrict.equals(caseDistrictCode)) {
            throw ReviewException.taskNotFound();
        }
    }
}
