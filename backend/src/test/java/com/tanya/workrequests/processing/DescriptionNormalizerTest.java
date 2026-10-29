package com.tanya.workrequests.processing;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DescriptionNormalizerTest {

    private final DescriptionNormalizer normalizer = new DescriptionNormalizer();

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        assertThat(normalizer.normalize("  pump leaking \t")).isEqualTo("pump leaking");
    }

    @Test
    void collapsesInternalWhitespaceIncludingTabsAndNewlines() {
        assertThat(normalizer.normalize("pump \t leaking\n\nfast")).isEqualTo("pump leaking fast");
    }

    @Test
    void lowercasesText() {
        assertThat(normalizer.normalize("Pump LEAKING")).isEqualTo("pump leaking");
    }

    @Test
    void lowercasesIndependentOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            // In Turkish, "I".toLowerCase() is a dotless "ı", which would split duplicate keys.
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(normalizer.normalize("FILTER")).isEqualTo("filter");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void leavesPunctuationUntouched() {
        assertThat(normalizer.normalize("Pump leaking.")).isEqualTo("pump leaking.");
    }

    @Test
    void throwsOnNullDescription() {
        assertThatNullPointerException()
                .isThrownBy(() -> normalizer.normalize(null))
                .withMessage("description");
    }
}
