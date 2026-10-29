package com.tanya.workrequests.processing;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Normalizes a description for duplicate matching: trimmed, internal whitespace collapsed
 * to one space, lowercased. Real users add stray spaces and capitals; those shouldn't hide
 * a duplicate.
 */
public class DescriptionNormalizer {

    // \p{javaWhitespace} is Character.isWhitespace, the same definition String.isBlank and
    // String.strip use, so "blank" in the validator and "whitespace" here never disagree.
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\p{javaWhitespace}+");

    /**
     * @throws NullPointerException if {@code description} is null; validation runs first, so
     *         a null here is a programming error, and returning null would let unrelated
     *         requests share a duplicate key
     */
    public String normalize(String description) {
        Objects.requireNonNull(description, "description");
        String collapsed = WHITESPACE_RUN.matcher(description.strip()).replaceAll(" ");
        // Locale.ROOT so the key doesn't change with the server's locale (e.g. Turkish dotless i).
        return collapsed.toLowerCase(Locale.ROOT);
    }
}
