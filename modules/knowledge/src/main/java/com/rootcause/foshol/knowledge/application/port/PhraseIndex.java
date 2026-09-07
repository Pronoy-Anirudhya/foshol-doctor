package com.rootcause.foshol.knowledge.application.port;

import com.rootcause.foshol.knowledge.domain.PhraseIndexEntry;
import java.util.List;

public interface PhraseIndex {

    List<PhraseIndexEntry> entries();

    int loadCount();
}
