package com.rootcause.foshol.review.domain;

import com.rootcause.foshol.common.ErrorCodes;

public class ReviewException extends RuntimeException {

    private final String errorCode;
    private final int status;

    public ReviewException(String errorCode, int status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String errorCode() {
        return errorCode;
    }

    public int status() {
        return status;
    }

    public static ReviewException taskNotFound() {
        return new ReviewException(ErrorCodes.ERR_REVIEW_TASK_NOT_FOUND, 404, "Review task not found.");
    }

    public static ReviewException claimConflict() {
        return new ReviewException(ErrorCodes.ERR_CLAIM_CONFLICT, 409, "The task is claimed by another officer.");
    }

    public static ReviewException claimNotHeld() {
        return new ReviewException(ErrorCodes.ERR_CLAIM_NOT_HELD, 409, "The caller does not hold a live claim.");
    }

    public static ReviewException terminal() {
        return new ReviewException(ErrorCodes.ERR_TASK_TERMINAL, 409, "The review task is in a terminal state.");
    }

    public static ReviewException advisoryNotFound() {
        return new ReviewException(ErrorCodes.ERR_ADVISORY_NOT_FOUND, 409, "No published advisory exists for this case.");
    }

    public static ReviewException requiresRemedy() {
        return new ReviewException(
                ErrorCodes.ERR_ADVISORY_REQUIRES_REMEDY, 400, "A non-healthy diagnosis requires at least one remedy.");
    }

    public static ReviewException remedyDiseaseMismatch() {
        return new ReviewException(
                ErrorCodes.ERR_REMEDY_DISEASE_MISMATCH, 400, "A selected remedy does not belong to the diagnosis.");
    }

    public static ReviewException remedyPhiMissing() {
        return new ReviewException(
                ErrorCodes.ERR_REMEDY_PHI_MISSING, 400, "A chemical remedy is missing a pre-harvest interval.");
    }

    public static ReviewException unknownSymptom() {
        return new ReviewException(ErrorCodes.ERR_UNKNOWN_SYMPTOM, 400, "A submitted symptom is not in the knowledge base.");
    }

    public static ReviewException queueSortNotSupported() {
        return new ReviewException(
                ErrorCodes.ERR_QUEUE_SORT_NOT_SUPPORTED, 400, "Queue ordering is not client-controllable.");
    }

    public static ReviewException transferToSelf() {
        return new ReviewException(ErrorCodes.ERR_TRANSFER_TO_SELF, 400, "A task cannot be transferred to the holder.");
    }

    public static ReviewException transferTargetInvalid() {
        return new ReviewException(ErrorCodes.ERR_TRANSFER_TARGET_INVALID, 404, "Transfer target is not a valid officer.");
    }

    public static ReviewException bulkTooLarge() {
        return new ReviewException(ErrorCodes.ERR_BULK_TOO_LARGE, 400, "The bulk request exceeds the configured maximum.");
    }

    public static ReviewException bulkEmpty() {
        return new ReviewException(ErrorCodes.ERR_BAD_REQUEST, 400, "The bulk request has no items.");
    }

    public static ReviewException invalidRejectionMessage() {
        return new ReviewException(ErrorCodes.ERR_ADVISORY_REQUIRES_REMEDY, 400, "Rejection message must be non-blank and at most 500 characters.");
    }
}
