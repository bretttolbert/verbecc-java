package io.github.bretttolbert.verbecc;

import java.util.Locale;
import java.util.Objects;

/** Languages supported by the bundled verb dictionaries. */
public enum Language {
    CA, ES, FR, IT, PT, RO;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Language fromCode(String code) {
        Objects.requireNonNull(code, "code");
        try {
            return valueOf(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported language: " + code, e);
        }
    }
}
