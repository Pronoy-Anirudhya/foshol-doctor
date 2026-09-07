package com.rootcause.foshol.notification.application;

import org.springframework.stereotype.Component;

/**
 * Templates identified by CONTENT-OWNERS C12. Values are substitution slots only; wrapping Bangla
 * copy is human-supplied and is not authored here.
 */
@Component
public class NotificationTemplates {

    public record Pair(String title, String body) {}

    public Pair resolve(String key, String preferredLanguage) {
        Pair pair = forLanguage(key, preferredLanguage);
        if (pair == null) {
            return forLanguage(key, "bn");
        }
        return pair;
    }

    private Pair forLanguage(String key, String language) {
        if (language == null) {
            return null;
        }
        return switch (key) {
            case NotificationContentAssembler.KEY_ADVISORY_PUBLISHED ->
                new Pair("{diseaseNameBn}", "{diseaseNameBn} {officerName} {remedyCount}");
            case NotificationContentAssembler.KEY_ADVISORY_REVISED ->
                new Pair("{diseaseNameBn}", "{diseaseNameBn} {officerName} {remedyCount}");
            case NotificationContentAssembler.KEY_CASE_REJECTED -> new Pair("{officerName}", "{messageBn}");
            case NotificationContentAssembler.KEY_CASE_STATUS -> new Pair("{toStatus}", "{fromStatus} {toStatus}");
            default -> null;
        };
    }
}
