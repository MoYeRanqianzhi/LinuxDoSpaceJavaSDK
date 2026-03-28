package io.linuxdospace.sdk;

import java.util.Locale;

/**
 * SemanticSuffix represents one semantic mailbox root plus one optional
 * dynamic fragment appended after the fixed {@code -mail} label.
 *
 * <p>Examples under {@code Suffix.LINUXDO_SPACE}:</p>
 *
 * <ul>
 *   <li>empty fragment -> {@code <owner_username>-mail.linuxdo.space}</li>
 *   <li>{@code "foo"} -> {@code <owner_username>-mailfoo.linuxdo.space}</li>
 * </ul>
 */
public record SemanticSuffix(Suffix base, String mailSuffixFragment) {
    public SemanticSuffix {
        if (base == null) {
            throw new IllegalArgumentException("base must not be null");
        }
        mailSuffixFragment = normalizeMailSuffixFragment(mailSuffixFragment);
    }

    /**
     * withSuffix returns the same semantic base with one normalized dynamic
     * mail suffix fragment.
     */
    public SemanticSuffix withSuffix(String fragment) {
        return new SemanticSuffix(base, fragment);
    }

    @Override
    public String toString() {
        return base.value();
    }

    static String normalizeMailSuffixFragment(String rawFragment) {
        if (rawFragment == null) {
            throw new IllegalArgumentException("fragment must not be null");
        }

        String value = rawFragment.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder();
        boolean lastWasDash = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if ((current >= 'a' && current <= 'z') || (current >= '0' && current <= '9')) {
                normalized.append(current);
                lastWasDash = false;
                continue;
            }
            if (!lastWasDash) {
                normalized.append('-');
                lastWasDash = true;
            }
        }

        String collapsed = normalized.toString().replaceAll("^-+|-+$", "");
        if (collapsed.isEmpty()) {
            throw new IllegalArgumentException("fragment does not contain any valid dns characters");
        }
        if (collapsed.contains(".")) {
            throw new IllegalArgumentException("fragment must stay inside one dns label");
        }
        if (collapsed.length() > 48) {
            throw new IllegalArgumentException("fragment must be 48 characters or fewer");
        }
        return collapsed;
    }
}
