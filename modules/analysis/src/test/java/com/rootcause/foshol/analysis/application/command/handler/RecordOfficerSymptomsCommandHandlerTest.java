package com.rootcause.foshol.analysis.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rootcause.foshol.analysis.application.port.AnalysisPersistencePort;
import com.rootcause.foshol.analysis.domain.AnalysisNotFoundException;
import com.rootcause.foshol.analysis.domain.CaseSymptom;
import com.rootcause.foshol.analysis.domain.UnknownSymptomException;
import com.rootcause.foshol.common.ErrorCodes;
import com.rootcause.foshol.knowledge.api.KnowledgeQueryApi;
import com.rootcause.foshol.knowledge.api.SymptomRefView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordOfficerSymptomsCommandHandlerTest {

    @Mock
    AnalysisPersistencePort persistence;
    @Mock
    KnowledgeQueryApi knowledge;
    @InjectMocks
    RecordOfficerSymptomsCommandHandler handler;

    private final UUID caseId = UUID.fromString("01800000-0000-7000-8000-00000000c001");
    private final UUID known = UUID.fromString("01800000-0000-7000-8000-000000005001");
    private final UUID unknown = UUID.fromString("01800000-0000-7000-8000-000000005099");

    @Test
    void rejectsUnknownSymptomWithoutInsert() {
        when(persistence.hasCompletedRun(caseId)).thenReturn(true);
        when(knowledge.listSymptoms())
                .thenReturn(List.of(new SymptomRefView(known, "s", "TODO(content-owner)", null, "LEAF")));
        assertThatThrownBy(() -> handler.handle(new RecordOfficerSymptomsCommand(caseId, List.of(known, unknown))))
                .isInstanceOf(UnknownSymptomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.ERR_UNKNOWN_SYMPTOM);
        verify(persistence, never()).addOfficerSymptoms(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsWhenNoAnalysis() {
        when(persistence.hasCompletedRun(caseId)).thenReturn(false);
        assertThatThrownBy(() -> handler.handle(new RecordOfficerSymptomsCommand(caseId, List.of(known))))
                .isInstanceOf(AnalysisNotFoundException.class);
    }

    @Test
    void skipsAlreadyRecordedOfficerSymptom() {
        when(persistence.hasCompletedRun(caseId)).thenReturn(true);
        when(knowledge.listSymptoms())
                .thenReturn(List.of(new SymptomRefView(known, "s", "TODO(content-owner)", null, "LEAF")));
        when(persistence.existsOfficerSymptom(caseId, known)).thenReturn(true);
        handler.handle(new RecordOfficerSymptomsCommand(caseId, List.of(known)));
        verify(persistence).addOfficerSymptoms(caseId, List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertsNewOfficerSymptomOnce() {
        when(persistence.hasCompletedRun(caseId)).thenReturn(true);
        when(knowledge.listSymptoms())
                .thenReturn(List.of(new SymptomRefView(known, "s", "TODO(content-owner)", null, "LEAF")));
        when(persistence.existsOfficerSymptom(caseId, known)).thenReturn(false);
        handler.handle(new RecordOfficerSymptomsCommand(caseId, List.of(known)));
        ArgumentCaptor<List<CaseSymptom>> captor = ArgumentCaptor.forClass(List.class);
        verify(persistence).addOfficerSymptoms(org.mockito.ArgumentMatchers.eq(caseId), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).symptomId()).isEqualTo(known);
    }
}
