package io.github.bretttolbert.verbecc;

/** Unknown verbs are not guessed; this library has no ML dependencies. */
public final class VerbNotFoundException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public VerbNotFoundException(Language language, String infinitive) {
        super("Verb not found in " + language.code() + " dictionary: " + infinitive);
    }
}
