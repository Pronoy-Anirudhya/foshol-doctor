package com.rootcause.foshol.knowledge.infrastructure;

import com.pgvector.PGvector;
import com.rootcause.foshol.knowledge.application.port.KnnHit;
import com.rootcause.foshol.knowledge.application.port.SymptomPhraseVectorPort;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.springframework.stereotype.Repository;

@Repository
public class SymptomPhraseVectorRepository implements SymptomPhraseVectorPort {

    private static final String KNN_SQL =
            """
            SELECT sp.id            AS phrase_id,
                   sp.symptom_id    AS symptom_id,
                   1 - (sp.embedding <=> ?) AS similarity
            FROM   symptom_phrase sp
            JOIN   symptom s ON s.id = sp.symptom_id
            WHERE  sp.embedding  IS NOT NULL
              AND  sp.deleted_at IS NULL
              AND  s.deleted_at  IS NULL
            ORDER  BY sp.embedding <=> ?
            LIMIT  ?
            """;

    private final DataSource dataSource;

    public SymptomPhraseVectorRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<KnnHit> findNearest(float[] queryVector, int knnLimit) {
        PGvector vector = new PGvector(queryVector);
        try (Connection connection = dataSource.getConnection()) {
            connection.unwrap(PGConnection.class).addDataType("vector", PGvector.class);
            try (PreparedStatement statement = connection.prepareStatement(KNN_SQL)) {
                statement.setObject(1, vector);
                statement.setObject(2, vector);
                statement.setInt(3, knnLimit);
                try (ResultSet rs = statement.executeQuery()) {
                    List<KnnHit> hits = new ArrayList<>();
                    while (rs.next()) {
                        hits.add(new KnnHit(
                                rs.getObject("phrase_id", UUID.class),
                                rs.getObject("symptom_id", UUID.class),
                                rs.getObject("similarity", BigDecimal.class)));
                    }
                    return List.copyOf(hits);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Symptom phrase kNN query failed", ex);
        }
    }
}
