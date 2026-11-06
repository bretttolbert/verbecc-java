package io.github.bretttolbert.verbecc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A verb and its conjugations, keyed by localized mood and tense names. */
public record CompleteConjugation(
        Language language,
        Verb verb,
        String stem,
        boolean reflexive,
        Map<String, Map<String, List<Conjugation>>> moods) {
    public CompleteConjugation {
        var copy = new LinkedHashMap<String, Map<String, List<Conjugation>>>();
        moods.forEach((mood, tenses) -> {
            var tenseCopy = new LinkedHashMap<String, List<Conjugation>>();
            tenses.forEach((tense, forms) -> tenseCopy.put(tense, List.copyOf(forms)));
            copy.put(mood, Collections.unmodifiableMap(tenseCopy));
        });
        moods = Collections.unmodifiableMap(copy);
    }
}
