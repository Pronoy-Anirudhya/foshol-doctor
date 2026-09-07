package com.rootcause.foshol.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.notification.NotifyFixtures;
import org.junit.jupiter.api.Test;

class NotificationContentAssemblerTest {

    private final NotificationContentAssembler assembler = new NotificationContentAssembler(new NotificationTemplates());

    @Test
    void advisoryBodyUsesHumanValuesOnly() {
        var assembled = assembler.assembleAdvisory(
                NotificationType.ADVISORY_PUBLISHED, NotifyFixtures.advisoryView(), NotifyFixtures.farmer());
        assertThat(assembled.titleBn()).isEqualTo("d-name");
        assertThat(assembled.bodyBn()).contains("d-name");
        assertThat(assembled.bodyBn()).contains("Officer A");
        assertThat(assembled.bodyBn()).contains("2");
        assertOnlyTemplateOrInput(assembled.titleBn() + assembled.bodyBn(), "d-name", "Officer A", "2", " ");
    }

    @Test
    void rejectionBodyContainsOfficerMessageVerbatim() {
        var assembled = assembler.assembleRejection(NotifyFixtures.rejectionView(), NotifyFixtures.farmer());
        assertThat(assembled.bodyBn()).contains(NotifyFixtures.FIXTURE_MESSAGE);
        assertThat(assembled.bodyBn()).doesNotContain("summary");
    }

    @Test
    void titleTruncatesTo200Graphemes() {
        String longName = "অ".repeat(250);
        var assembled = assembler.assemble(
                NotificationContentAssembler.KEY_ADVISORY_PUBLISHED,
                "bn",
                java.util.Map.of("diseaseNameBn", longName, "officerName", "O", "remedyCount", "1"));
        assertThat(assembled.titleBn().length()).isLessThanOrEqualTo(200);
    }

    private static void assertOnlyTemplateOrInput(String output, String... allowed) {
        String remainder = output;
        for (String token : allowed) {
            remainder = remainder.replace(token, "");
        }
        assertThat(remainder).isBlank();
    }
}
