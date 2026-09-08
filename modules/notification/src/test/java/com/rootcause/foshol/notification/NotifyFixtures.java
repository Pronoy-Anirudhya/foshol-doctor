package com.rootcause.foshol.notification;

import com.rootcause.foshol.common.AdvisoryAction;
import com.rootcause.foshol.common.NotificationType;
import com.rootcause.foshol.common.RejectionReason;
import com.rootcause.foshol.common.RemedyType;
import com.rootcause.foshol.common.Uuid7;
import com.rootcause.foshol.identity.api.FarmerView;
import com.rootcause.foshol.notification.api.AdvisoryNotification;
import com.rootcause.foshol.review.api.AdvisoryView;
import com.rootcause.foshol.review.api.RejectionView;
import com.rootcause.foshol.review.api.RemedyRefView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NotifyFixtures {

    public static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    public static final UUID FARMER = Uuid7.create();
    public static final UUID CASE = Uuid7.create();
    public static final UUID ADVISORY = Uuid7.create();
    public static final UUID OFFICER = Uuid7.create();
    public static final String CORRELATION = "corr-1";
    public static final String FIXTURE_MESSAGE = "ছবি স্পষ্ট নয়";

    private NotifyFixtures() {}

    public static FarmerView farmer() {
        return new FarmerView(FARMER, "Farmer A", "DHA", "bn", "DHK");
    }

    public static AdvisoryView advisoryView() {
        return new AdvisoryView(
                ADVISORY,
                CASE,
                Uuid7.create(),
                "d-name",
                OFFICER,
                "Officer A",
                AdvisoryAction.APPROVED,
                null,
                1,
                null,
                List.of(
                        new RemedyRefView(Uuid7.create(), RemedyType.CULTURAL, "r1", List.of(), null, null, "", null, null, null, null, null),
                        new RemedyRefView(Uuid7.create(), RemedyType.CULTURAL, "r2", List.of(), null, null, "", null, null, null, null, null)),
                T0);
    }

    public static RejectionView rejectionView() {
        return new RejectionView(CASE, OFFICER, "Officer A", RejectionReason.BLURRY_IMAGE, FIXTURE_MESSAGE, T0);
    }

    public static AdvisoryNotification advisoryNotification() {
        return new AdvisoryNotification(
                Uuid7.create(),
                FARMER,
                CASE,
                ADVISORY,
                NotificationType.ADVISORY_PUBLISHED,
                "d-name",
                "d-name Officer A 2",
                Map.of("correlationId", CORRELATION, "advisoryId", ADVISORY.toString()));
    }
}
