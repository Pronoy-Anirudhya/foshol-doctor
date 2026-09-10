package com.rootcause.foshol.notification.application.command;

import com.rootcause.foshol.common.enums.NotificationType;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.RejectionView;
import java.text.BreakIterator;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public final class NotificationContentAssembler {

    public static final String KEY_ADVISORY_PUBLISHED = "notify.advisory.published";
    public static final String KEY_ADVISORY_REVISED = "notify.advisory.revised";
    public static final String KEY_CASE_REJECTED = "notify.case.rejected";
    public static final String KEY_CASE_STATUS = "notify.case.status";

    private static final int TITLE_MAX = 200;

    private final NotificationTemplates templates;

    public NotificationContentAssembler(NotificationTemplates templates) {
        this.templates = templates;
    }

    public Assembled assembleAdvisory(NotificationType type, AdvisoryView advisory, FarmerView farmer) {
        String key = type == NotificationType.ADVISORY_REVISED ? KEY_ADVISORY_REVISED : KEY_ADVISORY_PUBLISHED;
        Map<String, String> values = Map.of(
                "diseaseNameBn", nullToEmpty(advisory.diseaseNameBn()),
                "officerName", nullToEmpty(advisory.officerName()),
                "remedyCount", Integer.toString(advisory.remedies().size()));
        return assemble(key, farmer.preferredLanguage(), values);
    }

    public Assembled assembleRejection(RejectionView rejection, FarmerView farmer) {
        Map<String, String> values = Map.of(
                "officerName", nullToEmpty(rejection.officerName()),
                "messageBn", nullToEmpty(rejection.messageBn()));
        return assemble(KEY_CASE_REJECTED, farmer.preferredLanguage(), values);
    }

    public Assembled assembleStatus(String fromStatus, String toStatus, FarmerView farmer) {
        Map<String, String> values = Map.of("fromStatus", fromStatus, "toStatus", toStatus);
        return assemble(KEY_CASE_STATUS, farmer.preferredLanguage(), values);
    }

    public Assembled assemble(String key, String preferredLanguage, Map<String, String> values) {
        NotificationTemplates.Pair pair = templates.resolve(key, preferredLanguage);
        return new Assembled(truncateTitle(substitute(pair.title(), values)), substitute(pair.body(), values));
    }

    private static String substitute(String template, Map<String, String> values) {
        String out = template;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    private static String truncateTitle(String title) {
        if (title.length() <= TITLE_MAX) {
            return title;
        }
        BreakIterator it = BreakIterator.getCharacterInstance(Locale.forLanguageTag("bn"));
        it.setText(title);
        int end = it.first();
        int last = end;
        while (end != BreakIterator.DONE && end <= TITLE_MAX) {
            last = end;
            end = it.next();
        }
        return title.substring(0, last);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record Assembled(String titleBn, String bodyBn) {}
}
