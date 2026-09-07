package com.rootcause.foshol.review.application.query.handler;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.review.application.port.ReviewQueryPort;
import com.rootcause.foshol.review.application.query.OfficerQueueQuery;
import com.rootcause.foshol.review.domain.ReviewException;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class OfficerQueueQueryHandlerTest {

    @Mock
    private ReviewQueryPort reads;

    @Test
    void sortParameterIsRejected() {
        OfficerQueueQueryHandler handler = new OfficerQueueQueryHandler(reads);
        assertThatThrownBy(() -> handler.handle(new OfficerQueueQuery(
                        "PENDING", false, Uuid7.create(), 0, 20, "confidence", "desc")))
                .extracting(ex -> ((ReviewException) ex).errorCode())
                .isEqualTo(ErrorCodes.ERR_QUEUE_SORT_NOT_SUPPORTED);
        verifyNoInteractions(reads);
    }
}
