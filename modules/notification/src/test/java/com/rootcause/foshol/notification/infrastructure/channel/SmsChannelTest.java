package com.rootcause.foshol.notification.infrastructure.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.notification.NotifyFixtures;
import com.rootcause.foshol.notification.domain.ChannelNames;
import org.junit.jupiter.api.Test;

class SmsChannelTest {

    @Test
    void finishedDisabledAdapter() {
        SmsChannel channel = new SmsChannel(false);
        assertThat(channel.name()).isEqualTo(ChannelNames.SMS);
        assertThat(channel.enabled()).isFalse();
        assertThat(channel.supports(NotifyFixtures.FARMER)).isFalse();
        assertThat(channel.send(NotifyFixtures.advisoryNotification())).isFalse();
    }

    @Test
    void flagTrueStillDoesNotDeliver() {
        SmsChannel channel = new SmsChannel(true);
        assertThat(channel.enabled()).isTrue();
        assertThat(channel.supports(NotifyFixtures.FARMER)).isFalse();
        assertThat(channel.send(NotifyFixtures.advisoryNotification())).isFalse();
    }
}
