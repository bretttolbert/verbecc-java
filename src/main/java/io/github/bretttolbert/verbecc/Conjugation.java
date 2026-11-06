package io.github.bretttolbert.verbecc;

import java.util.List;

/**
 * One inflection, including all alternate forms. Grammatical fields are null
 * when inapplicable (for example, person on a participle).
 */
public record Conjugation(
        Person person,
        GrammaticalNumber number,
        Gender gender,
        String pronoun,
        List<String> forms) {
    public Conjugation {
        forms = List.copyOf(forms);
    }
}
