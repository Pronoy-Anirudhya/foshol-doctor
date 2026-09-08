package com.rootcause.foshol.review.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.identity.api.OfficerLookupApi;
import com.rootcause.foshol.identity.api.OfficerView;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.port.ReviewQueryPort.QueueTaskRow;
import com.rootcause.foshol.review.domain.ReviewException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewDistrictGuardTest {

    @Mock
    private OfficerLookupApi officers;

    @Mock
    private ReviewQueryPort reads;

    @Test
    void otherDistrictLooksLikeNotFound() {
        UUID caller = Uuid7.create();
        UUID task = Uuid7.create();
        when(officers.findById(caller))
                .thenReturn(Optional.of(new OfficerView(caller, "A", "DHA", "OFFICER", true, "DHK")));
        when(reads.findQueueRow(task)).thenReturn(Optional.of(row("CTG")));
        ReviewDistrictGuard guard = new ReviewDistrictGuard(officers, reads);
        assertThatThrownBy(() -> guard.requireTaskInCallerDistrict(caller, task))
                .isInstanceOf(ReviewException.class)
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_REVIEW_TASK_NOT_FOUND);
    }

    private static QueueTaskRow row(String district) {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        return new QueueTaskRow(
                Uuid7.create(),
                Uuid7.create(),
                "Farmer",
                "rice",
                "ধান",
                district,
                "PRIMARY",
                Uuid7.create(),
                "d",
                new BigDecimal("0.50"),
                1,
                false,
                "REPLAY",
                "PENDING",
                null,
                false,
                (short) 0,
                t0,
                t0,
                t0,
                null);
    }
}
