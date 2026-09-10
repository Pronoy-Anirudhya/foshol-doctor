package com.rootcause.foshol.review.domain.spec;

import com.rootcause.foshol.common.enums.ReviewState;
import com.rootcause.foshol.review.domain.ReviewTask;

public final class TaskIsTerminal {

    public boolean isSatisfiedBy(ReviewTask task) {
        ReviewState state = task.state();
        return state == ReviewState.DONE || state == ReviewState.REJECTED;
    }
}
