package com.rootcause.foshol.analysis.infrastructure.adapter;

import com.rootcause.foshol.analysis.application.port.AnalysisEventPort;
import com.rootcause.foshol.common.events.AnalysisCompleted;
import com.rootcause.foshol.common.events.AnalysisFailed;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class SpringAnalysisEventAdapter implements AnalysisEventPort {

    private final ApplicationEventPublisher publisher;

    public SpringAnalysisEventAdapter(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publishCompleted(AnalysisCompleted event) {
        afterCommit(() -> publisher.publishEvent(event));
    }

    @Override
    public void publishFailed(AnalysisFailed event) {
        publisher.publishEvent(event);
    }

    private void afterCommit(Runnable publish) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
