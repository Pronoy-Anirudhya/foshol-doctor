package com.rootcause.foshol.knowledge.infrastructure;

import com.rootcause.foshol.common.ConfigKeys;
import com.rootcause.foshol.knowledge.application.port.KnowledgeContentPort;
import com.rootcause.foshol.knowledge.domain.KnowledgeTaxonomy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class KnowledgeContentStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeContentStartupRunner.class);

    private final KnowledgeContentPort content;
    private final int expectedDiseaseCount;

    public KnowledgeContentStartupRunner(
            KnowledgeContentPort content,
            @Value("${" + ConfigKeys.KNOWLEDGE_EXPECTED_DISEASE_COUNT + ":"
                    + KnowledgeTaxonomy.DISEASE_CLASS_COUNT + "}")
                    int expectedDiseaseCount) {
        this.content = content;
        this.expectedDiseaseCount = expectedDiseaseCount;
    }

    @Override
    public void run(ApplicationArguments args) {
        KnowledgeContentPort.ContentSnapshot snapshot = content.snapshot();
        KnowledgeContentValidator.validateStrict(snapshot, expectedDiseaseCount);
        log.info(
                "knowledge content gate passed diseases={} expected={} phrases={} remedies={} weights={} labels={}",
                snapshot.liveDiseaseCount(),
                expectedDiseaseCount,
                snapshot.livePhraseCount(),
                snapshot.liveRemedyCount(),
                snapshot.diseaseSymptomRowCount(),
                snapshot.labelMapRowCount());
    }
}
