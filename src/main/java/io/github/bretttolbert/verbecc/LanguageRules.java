package io.github.bretttolbert.verbecc;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LanguageRules {
    record Compound(String mood, String tense, String auxiliaryMood, String auxiliaryTense) {}
    record Reflexive(String infinitive, boolean reflexive) {}

    private static final Set<String> ETRE_VERBS = Set.of(
            "aller", "arriver", "descendre", "redescendre", "entrer", "rentrer", "monter",
            "remonter", "mourir", "naître", "renaître", "partir", "repartir", "passer",
            "rester", "retourner", "sortir", "ressortir", "tomber", "retomber", "venir",
            "devenir", "parvenir", "revenir");
    private static final Set<String> ESSERE_VERBS = Set.of(
            "essere", "andare", "arrivare", "cadere", "entrare", "partire", "rimanere",
            "uscire", "venire", "stare", "passare", "diventare", "crescere", "morire", "nascere");

    private LanguageRules() {}

    static String indicativeMood(Language language) {
        return switch (language) {
            case FR -> "indicatif";
            case CA -> "indicatiu";
            case RO -> "indicativ";
            default -> "indicativo";
        };
    }

    static String imperativeMood(Language language) {
        return switch (language) {
            case FR -> "imperatif";
            case CA -> "imperatiu";
            case RO -> "imperativ";
            default -> "imperativo";
        };
    }

    static String subjunctiveMood(Language language) {
        return switch (language) {
            case FR -> "subjonctif";
            case CA -> "subjuntiu";
            case IT -> "congiuntivo";
            case RO -> "conjunctiv";
            default -> "subjuntivo";
        };
    }

    static String infinitiveMood(Language language) {
        return switch (language) {
            case FR -> "infinitif";
            case CA -> "infinitiu";
            case IT -> "infinito";
            case RO -> "infinitiv";
            default -> "infinitivo";
        };
    }

    static String participleMood(Language language) {
        return switch (language) {
            case FR -> "participe";
            case ES -> "participo";
            case CA -> "participi";
            case IT -> "participio";
            case PT -> "particípio";
            case RO -> "participiu";
        };
    }

    static String participleTense(Language language) {
        return switch (language) {
            case FR -> "participe-passé";
            case IT -> "participio-passato";
            default -> participleMood(language);
        };
    }

    static List<String> pronouns(Language language, Person person, GrammaticalNumber number, boolean imperative) {
        if (person == null || number == null) {
            return Collections.singletonList(null);
        }
        int slot = person.ordinal() + (number == GrammaticalNumber.PLURAL ? 3 : 0);
        String[] groups = switch (language) {
            case FR -> new String[] {"je", "tu", "il,elle,on", "nous", "vous", "ils,elles"};
            case ES -> new String[] {"yo", "tú,vos", "él,ella,usted", "nosotros", "vosotros", "ellos,ellas,ustedes"};
            case IT -> new String[] {"io", "tu", "lui,lei", "noi", "voi", "loro"};
            case CA -> new String[] {"jo", "tu", "ell,ella", "nosaltres", "vosaltres", "ells,elles"};
            case RO -> new String[] {"eu", "tu", "el,ea", "noi", "voi", "ei,ele"};
            case PT -> imperative
                    ? new String[] {"eu", "tu", "você", "nós", "vós", "vocês"}
                    : new String[] {"eu", "tu", "ele,ela,você", "nós", "vós", "eles,elas,vocês"};
        };
        return List.of(groups[slot].split(","));
    }

    static Gender pronounGender(Language language, String pronoun) {
        if (pronoun == null) {
            return null;
        }
        return switch (language) {
            case FR -> switch (pronoun) {
                case "elle", "elles" -> Gender.FEMININE;
                case "il", "ils" -> Gender.MASCULINE;
                default -> null;
            };
            case ES -> switch (pronoun) {
                case "ella", "ellas" -> Gender.FEMININE;
                case "él", "ellos" -> Gender.MASCULINE;
                default -> null;
            };
            case IT -> switch (pronoun) {
                case "lei" -> Gender.FEMININE;
                case "lui" -> Gender.MASCULINE;
                default -> null;
            };
            case CA -> switch (pronoun) {
                case "ella", "elles" -> Gender.FEMININE;
                case "ell", "ells" -> Gender.MASCULINE;
                default -> null;
            };
            case PT -> switch (pronoun) {
                case "ela", "elas" -> Gender.FEMININE;
                case "ele", "eles" -> Gender.MASCULINE;
                default -> null;
            };
            case RO -> switch (pronoun) {
                case "ea", "ele" -> Gender.FEMININE;
                case "el", "ei" -> Gender.MASCULINE;
                default -> null;
            };
        };
    }

    static Reflexive splitReflexive(Language language, String infinitive) {
        return switch (language) {
            case FR -> infinitive.startsWith("se ") ? new Reflexive(infinitive.substring(3), true)
                    : infinitive.startsWith("s'") ? new Reflexive(infinitive.substring(2), true)
                    : new Reflexive(infinitive, false);
            case ES -> infinitive.endsWith("se") ? new Reflexive(infinitive.substring(0, infinitive.length() - 2), true)
                    : new Reflexive(infinitive, false);
            case IT -> infinitive.endsWith("si") ? new Reflexive(infinitive.substring(0, infinitive.length() - 2) + "e", true)
                    : infinitive.startsWith("si ") ? new Reflexive(infinitive.substring(3), true)
                    : infinitive.startsWith("s'") ? new Reflexive(infinitive.substring(2), true)
                    : new Reflexive(infinitive, false);
            case PT -> infinitive.endsWith("-se") ? new Reflexive(infinitive.substring(0, infinitive.length() - 3), true)
                    : new Reflexive(infinitive, false);
            // These inflectors do not recognize reflexive infinitives in the Python library.
            case CA, RO -> new Reflexive(infinitive, false);
        };
    }

    static String auxiliary(Language language, String infinitive, boolean reflexive, String tense) {
        return switch (language) {
            case FR -> reflexive || ETRE_VERBS.contains(infinitive) ? "être" : "avoir";
            case IT -> reflexive || ESSERE_VERBS.contains(infinitive) ? "essere" : "avere";
            case ES -> "haber";
            case CA -> "haver";
            case PT -> "ter";
            case RO -> switch (tense) {
                case "viitor-1", "viitor-2" -> "voi";
                case "viitor-1-popular" -> infinitive;
                default -> "avea";
            };
        };
    }

    static String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    static boolean startsWithVowel(String value) {
        return !value.isEmpty() && "aeiouyhœ".indexOf(stripAccents(value).charAt(0)) >= 0;
    }

    static String combine(String stem, String ending) {
        if (ending.equals("-")) {
            return "-";
        }
        int deleted = 0;
        while (deleted < ending.length() && ending.charAt(deleted) == '-') {
            deleted++;
        }
        if (deleted > stem.length()) {
            throw new IllegalStateException("Ending deletes more characters than stem contains: " + stem + " + " + ending);
        }
        return stem.substring(0, stem.length() - deleted) + ending.substring(deleted);
    }

    static String reflexivePronoun(Language language, Person person, GrammaticalNumber number) {
        int slot = person.ordinal() + (number == GrammaticalNumber.PLURAL ? 3 : 0);
        return switch (language) {
            case FR -> new String[] {"me", "te", "se", "nous", "vous", "se"}[slot];
            case IT -> new String[] {"mi", "ti", "si", "ci", "vi", "si"}[slot];
            case ES, CA -> new String[] {"me", "te", "se", "nos", "os", "se"}[slot];
            case PT -> new String[] {"me", "te", "se", "nos", "vos", "se"}[slot];
            case RO -> new String[] {"mă", "te", "se", "ne", "vă", "se"}[slot];
        };
    }

    static String joinFrench(String prefix, String form) {
        return !prefix.equals("elle") && prefix.endsWith("e") && startsWithVowel(form)
                ? prefix.substring(0, prefix.length() - 1) + "'" + form : prefix + " " + form;
    }

    static String subjunctivePrefix(Language language, String form, String tense, String pronoun) {
        return switch (language) {
            case FR -> startsWithVowel(form) ? "qu'" + form : "que " + form;
            case IT -> "che " + form;
            case PT -> switch (tense) {
                case "presente" -> "que " + form;
                case "pretérito-imperfeito" -> "se " + form;
                case "futuro" -> "quando " + form;
                default -> form;
            };
            case RO -> {
                String particle = tense.equals("perfect") ? "să fi" : "să";
                if (pronoun != null && form.startsWith(pronoun + " ")) {
                    yield pronoun + " " + particle + form.substring(pronoun.length());
                }
                yield particle + " " + form;
            }
            default -> form;
        };
    }

    static Map<String, Map<String, Compound>> compounds(Language language) {
        List<Compound> entries = new ArrayList<>();
        switch (language) {
            case FR -> {
                add(entries, "indicatif", "indicatif",
                        "passé-composé", "présent", "plus-que-parfait", "imparfait",
                        "futur-antérieur", "futur-simple", "passé-antérieur", "passé-simple");
                add(entries, "subjonctif", "subjonctif", "passé", "présent", "plus-que-parfait", "imparfait");
                add(entries, "conditionnel", "conditionnel", "passé", "présent");
                add(entries, "imperatif", "imperatif", "imperatif-passé", "imperatif-présent");
            }
            case ES -> {
                add(entries, "indicativo", "indicativo",
                        "pretérito-perfecto-compuesto", "presente", "pretérito-pluscuamperfecto", "pretérito-imperfecto",
                        "pretérito-anterior", "pretérito-perfecto-simple", "futuro-perfecto", "futuro");
                add(entries, "condicional", "condicional", "perfecto", "presente");
                add(entries, "subjuntivo", "subjuntivo", "pretérito-perfecto", "presente",
                        "pretérito-pluscuamperfecto-1", "pretérito-imperfecto-1",
                        "pretérito-pluscuamperfecto-2", "pretérito-imperfecto-2", "futuro-perfecto", "futuro");
            }
            case IT -> {
                add(entries, "indicativo", "indicativo",
                        "passato-prossimo", "presente", "trapassato-prossimo", "imperfetto",
                        "trapassato-remoto", "passato-remoto", "futuro-anteriore", "futuro");
                add(entries, "congiuntivo", "congiuntivo", "passato", "presente", "trapassato", "imperfetto");
                add(entries, "condizionale", "condizionale", "passato", "presente");
            }
            case PT -> {
                add(entries, "indicativo", "indicativo",
                        "pretérito-perfeito-composto", "presente",
                        "pretérito-mais-que-perfeito-composto", "pretérito-imperfeito",
                        "pretérito-mais-que-perfeito-anterior", "pretérito-mais-que-perfeito",
                        "futuro-do-presente-composto", "futuro-do-presente");
                add(entries, "subjuntivo", "subjuntivo", "pretérito-perfeito", "presente",
                        "pretérito-mais-que-perfeito", "pretérito-imperfeito", "futuro-composto", "futuro");
                add(entries, "condicional", "condicional", "futuro-do-pretérito-composto", "futuro-do-pretérito");
                add(entries, "infinitivo", "infinitivo", "infinitivo-pessoal-composto", "infinitivo-pessoal-presente");
            }
            case RO -> {
                add(entries, "indicativ", "indicativ", "perfect-compus", "prezent",
                        "viitor-1", "prezent", "viitor-2", "prezent", "viitor-2-popular", "prezent");
                add(entries, "indicativ", "conjunctiv", "viitor-1-popular", "prezent");
                add(entries, "conjunctiv", "indicativ", "perfect", "prezent");
                add(entries, "condițional", "indicativ", "prezent", "prezent", "perfect", "prezent");
            }
            case CA -> { /* The Python inflector has no compound tense definitions. */ }
        }
        var result = new LinkedHashMap<String, Map<String, Compound>>();
        for (Compound entry : entries) {
            result.computeIfAbsent(entry.mood(), key -> new LinkedHashMap<>()).put(entry.tense(), entry);
        }
        result.replaceAll((key, value) -> Collections.unmodifiableMap(value));
        return Collections.unmodifiableMap(result);
    }

    private static void add(List<Compound> entries, String mood, String auxMood, String... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            entries.add(new Compound(mood, pairs[i], auxMood, pairs[i + 1]));
        }
    }
}
