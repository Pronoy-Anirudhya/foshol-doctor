package com.rootcause.foshol.intake.infrastructure;

import com.rootcause.foshol.intake.api.CaseIntakeChannel;
import com.rootcause.foshol.intake.api.IntakeRequest;
import com.rootcause.foshol.intake.application.command.SubmitCaseCommandHandler;
import com.rootcause.foshol.intake.application.command.SubmitCaseResult;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WebIntakeAdapter implements CaseIntakeChannel {

    private final SubmitCaseCommandHandler handler;

    public WebIntakeAdapter(SubmitCaseCommandHandler handler) {
        this.handler = handler;
    }

    public SubmitCaseResult submitForHttp(IntakeRequest request) {
        return handler.handle(request);
    }

    @Override
    public UUID submit(IntakeRequest request) {
        return submitForHttp(request).caseId();
    }
}
