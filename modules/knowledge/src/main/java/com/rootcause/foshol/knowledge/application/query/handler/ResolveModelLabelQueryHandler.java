package com.rootcause.foshol.knowledge.application.query.handler;

import com.rootcause.foshol.knowledge.application.port.ModelLabelIndex;
import com.rootcause.foshol.knowledge.application.query.ResolveModelLabelQuery;
import com.rootcause.foshol.common.cqrs.QueryHandler;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class ResolveModelLabelQueryHandler implements QueryHandler<ResolveModelLabelQuery, Optional<UUID>> {

    @Override
    public Class<ResolveModelLabelQuery> queryType() {
        return ResolveModelLabelQuery.class;
    }

    private final ModelLabelIndex labels;

    public ResolveModelLabelQueryHandler(ModelLabelIndex labels) {
        this.labels = labels;
    }

    @Override
    public Optional<UUID> handle(ResolveModelLabelQuery query) {
        if (query.modelId() == null || query.modelVersion() == null || query.rawLabel() == null) {
            return Optional.empty();
        }
        return labels.resolve(query.modelId(), query.modelVersion(), query.rawLabel());
    }
}
