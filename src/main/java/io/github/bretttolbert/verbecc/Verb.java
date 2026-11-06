package io.github.bretttolbert.verbecc;

/** An entry in the XML verb dictionary. Translation may be empty. */
public record Verb(String infinitive, String template, String translationEn, boolean aspirateH) {}
