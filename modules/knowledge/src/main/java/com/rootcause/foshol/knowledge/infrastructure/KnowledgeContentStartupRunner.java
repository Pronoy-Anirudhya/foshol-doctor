package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.knowledge.application.port.KnowledgeContentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class KnowledgeContentStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeContentStartupRunner.class);

    private final KnowledgeContentPort content;

    public KnowledgeContentStartupRunner(KnowledgeContentPort content) {
        this.content = content;
    }

    @Override
    public void run(ApplicationArguments args) {
        KnowledgeContentPort.ContentSnapshot snapshot = content.snapshot();
        KnowledgeContentValidator.validateStrict(snapshot);
        log.info(
                "knowledge content gate passed diseases={} phrases={} remedies={} weights={} labels={}",
                snapshot.liveDiseaseCount(),
                snapshot.livePhraseCount(),
                snapshot.liveRemedyCount(),
                snapshot.diseaseSymptomRowCount(),
                snapshot.labelMapRowCount());
    }
}
