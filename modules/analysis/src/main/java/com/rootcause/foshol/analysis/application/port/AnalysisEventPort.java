package com.rootcause.foshol.analysis.application.port;

import com.rootcause.foshol.common.events.AnalysisCompleted;
import com.rootcause.foshol.common.events.AnalysisFailed;

public interface AnalysisEventPort {

    void publishCompleted(AnalysisCompleted event);

    void publishFailed(AnalysisFailed event);
}
