package com.rootcause.foshol.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BanglaTextNormaliserTest {

    @Test
    void stripsZwnjFoldsBengaliDigitsAndReplacesDanda() {
        String input = "পাতা\u200cয় ১২। হলুদ";
        String normalised = BanglaTextNormaliser.normalise(input);
        assertThat(normalised).doesNotContain("\u200c");
        assertThat(normalised).contains("12");
        assertThat(normalised).doesNotContain("।");
        assertThat(normalised).doesNotContain("  ");
    }

    @Test
    void isIdempotent() {
        String input = "পাতা\u200d ৩।";
        String once = BanglaTextNormaliser.normalise(input);
        assertThat(BanglaTextNormaliser.normalise(once)).isEqualTo(once);
    }
}
