package com.rootcause.foshol.notification.infrastructure.channel;

import static org.assertj.core.api.Assertions.assertThat;

import com.rootcause.foshol.notification.NotifyFixtures;
import com.rootcause.foshol.notification.domain.ChannelNames;
import org.junit.jupiter.api.Test;

class WebPushChannelTest {

    @Test
    void finishedDisabledAdapter() {
        WebPushChannel channel = new WebPushChannel(false);
        assertThat(channel.name()).isEqualTo(ChannelNames.WEB_PUSH);
        assertThat(channel.enabled()).isFalse();
        assertThat(channel.supports(NotifyFixtures.FARMER)).isFalse();
        assertThat(channel.send(NotifyFixtures.advisoryNotification())).isFalse();
    }

    @Test
    void flagTrueStillDoesNotDeliver() {
        WebPushChannel channel = new WebPushChannel(true);
        assertThat(channel.enabled()).isTrue();
        assertThat(channel.supports(NotifyFixtures.FARMER)).isFalse();
        assertThat(channel.send(NotifyFixtures.advisoryNotification())).isFalse();
    }
}
