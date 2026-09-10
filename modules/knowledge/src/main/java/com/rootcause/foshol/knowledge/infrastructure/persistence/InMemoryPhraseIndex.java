package com.rootcause.foshol.knowledge.infrastructure.persistence;

import com.rootcause.foshol.knowledge.application.port.KnowledgeContentPort;
import com.rootcause.foshol.knowledge.application.port.PhraseIndex;
import com.rootcause.foshol.knowledge.domain.PhraseIndexEntry;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InMemoryPhraseIndex implements PhraseIndex {

    private final List<PhraseIndexEntry> entries;
    private final int loadCount;

    public InMemoryPhraseIndex(KnowledgeContentPort content) {
        this.entries = List.copyOf(content.loadLivePhrases());
        this.loadCount = 1;
    }

    @Override
    public List<PhraseIndexEntry> entries() {
        return entries;
    }

    @Override
    public int loadCount() {
        return loadCount;
    }
}
