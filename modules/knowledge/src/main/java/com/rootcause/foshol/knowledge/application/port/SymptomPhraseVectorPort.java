package com.rootcause.foshol.knowledge.application.port;

import java.util.List;

public interface SymptomPhraseVectorPort {

    List<KnnHit> findNearest(float[] queryVector, int knnLimit);
}
