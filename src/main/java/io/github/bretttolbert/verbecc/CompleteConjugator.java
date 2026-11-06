package io.github.bretttolbert.verbecc;

import io.github.bretttolbert.verbecc.ConjugationData.Ending;
import io.github.bretttolbert.verbecc.ConjugationData.Template;
import io.github.bretttolbert.verbecc.LanguageRules.Compound;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * XML-backed conjugation without machine learning or runtime dependencies.
 * Instances and returned results are safe to share between threads.
 */
public final class CompleteConjugator {
    private static final ConcurrentMap<Language, ConjugationData> DATA = new ConcurrentHashMap<>();
    private static final Set<String> NON_REFLEXIVE_FR = Set.of("être", "aller", "avoir");

    private record Context(Verb verb, Template template, String stem, boolean reflexive) {}

    private final Language language;
    private final ConjugationData data;
    private final Map<String, Map<String, Compound>> compounds;

    public CompleteConjugator(Language language) {
        this.language = Objects.requireNonNull(language, "language");
        data = DATA.computeIfAbsent(language, ConjugationData::new);
        compounds = LanguageRules.compounds(language);
    }

    public CompleteConjugator(String languageCode) {
        this(Language.fromCode(languageCode));
    }

    public Language language() {
        return language;
    }

    public List<Verb> getVerbs() {
        return List.copyOf(data.verbs.values());
    }

    public List<String> getInfinitives() {
        return List.copyOf(data.verbs.keySet());
    }

    public List<String> getTemplateNames() {
        return List.copyOf(data.templates.keySet());
    }

    public Verb findVerbByInfinitive(String infinitive) {
        String normalized = normalize(infinitive);
        Verb verb = lookup(normalized);
        if (verb == null) {
            throw new VerbNotFoundException(language, normalized);
        }
        return verb;
    }

    public List<String> getVerbsThatStartWith(String query, int maxResults) {
        if (maxResults < 0) {
            throw new IllegalArgumentException("maxResults must not be negative");
        }
        String prefix = LanguageRules.stripAccents(Objects.requireNonNull(query, "query").strip().toLowerCase(Locale.ROOT));
        return data.verbs.keySet().stream().filter(verb -> LanguageRules.stripAccents(verb).startsWith(prefix))
                .limit(maxResults).collect(Collectors.toUnmodifiableList());
    }

    public CompleteConjugation conjugate(String infinitive) {
        return conjugate(infinitive, true);
    }

    public CompleteConjugation conjugate(String infinitive, boolean conjugatePronouns) {
        Context context = resolve(infinitive);
        var moods = new LinkedHashMap<String, Map<String, List<Conjugation>>>();
        var names = new LinkedHashSet<>(context.template().moods().keySet());
        names.addAll(compounds.keySet());
        for (String mood : names) {
            moods.put(mood, conjugateMood(context, mood, conjugatePronouns));
        }
        return new CompleteConjugation(language, context.verb(), context.stem(), context.reflexive(), moods);
    }

    public Map<String, List<Conjugation>> conjugateMood(String infinitive, String mood) {
        return conjugateMood(infinitive, mood, true);
    }

    public Map<String, List<Conjugation>> conjugateMood(String infinitive, String mood, boolean conjugatePronouns) {
        return conjugateMood(resolve(infinitive), normalize(mood), conjugatePronouns);
    }

    public List<Conjugation> conjugateMoodTense(String infinitive, String mood, String tense) {
        return conjugateMoodTense(infinitive, mood, tense, true);
    }

    public List<Conjugation> conjugateMoodTense(
            String infinitive, String mood, String tense, boolean conjugatePronouns) {
        return conjugateTense(resolve(infinitive), normalize(mood), normalize(tense), conjugatePronouns);
    }

    private Map<String, List<Conjugation>> conjugateMood(Context context, String mood, boolean showPronouns) {
        Map<String, List<Ending>> simple = context.template().moods().get(mood);
        Map<String, Compound> compound = compounds.get(mood);
        if (simple == null && compound == null) {
            throw new IllegalArgumentException("Invalid mood for " + language.code() + ": " + mood);
        }
        var result = new LinkedHashMap<String, List<Conjugation>>();
        if (simple != null) {
            for (String tense : simple.keySet()) {
                result.put(tense, conjugateTense(context, mood, tense, showPronouns));
            }
        }
        if (compound != null) {
            for (String tense : compound.keySet()) {
                result.put(tense, conjugateTense(context, mood, tense, showPronouns));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private List<Conjugation> conjugateTense(Context context, String mood, String tense, boolean showPronouns) {
        Compound compound = compounds.getOrDefault(mood, Map.of()).get(tense);
        if (compound != null) {
            return compound(context, compound, showPronouns);
        }
        if (!context.template().moods().containsKey(mood)) {
            throw new IllegalArgumentException("Invalid mood for " + language.code() + ": " + mood);
        }
        return simple(context, mood, tense, tense, showPronouns);
    }

    private Context resolve(String infinitive) {
        String normalized = normalize(infinitive);
        var split = LanguageRules.splitReflexive(language, normalized);
        Verb verb = lookup(split.infinitive());
        if (verb == null && split.reflexive()) {
            verb = lookup(normalized);
        }
        if (verb == null) {
            throw new VerbNotFoundException(language, normalized);
        }
        Template template = data.templates.get(verb.template());
        if (template == null) {
            throw new IllegalStateException("Missing template " + verb.template() + " for " + verb.infinitive());
        }
        int colon = template.name().indexOf(':');
        if (colon < 0) {
            throw new IllegalStateException("Invalid template name: " + template.name());
        }
        String suffix = template.name().substring(colon + 1);
        String stemInfinitive = verb.infinitive();
        if (split.reflexive() && !stemInfinitive.endsWith(suffix) && split.infinitive().endsWith(suffix)) {
            stemInfinitive = split.infinitive();
        }
        if (suffix.length() > stemInfinitive.length()
                || (language != Language.CA && !stemInfinitive.endsWith(suffix))) {
            throw new IllegalStateException("Template ending does not match " + verb.infinitive() + ": " + template.name());
        }
        if (split.reflexive() && language == Language.FR) {
            List<Ending> present = template.moods().get("indicatif").get("présent");
            if (NON_REFLEXIVE_FR.contains(verb.infinitive()) || present.size() < 6) {
                throw new IllegalArgumentException("Verb cannot be reflexive: " + verb.infinitive());
            }
        }
        return new Context(verb, template, stemInfinitive.substring(0, stemInfinitive.length() - suffix.length()),
                split.reflexive());
    }

    private List<Conjugation> simple(
            Context context, String mood, String tense, String primaryTense, boolean showPronouns) {
        List<Ending> endings = context.template().moods().getOrDefault(mood, Map.of()).get(tense);
        if (endings == null) {
            throw new IllegalArgumentException("Invalid tense for " + language.code() + " " + mood + ": " + tense);
        }
        boolean imperative = mood.equals(LanguageRules.imperativeMood(language));
        boolean participle = mood.equals(LanguageRules.participleMood(language));
        String stem = context.stem();
        if (context.template().stripAccents() && !mood.equals(LanguageRules.infinitiveMood(language))) {
            stem = LanguageRules.stripAccents(stem);
        }
        var result = new ArrayList<Conjugation>();
        for (Ending ending : endings) {
            List<String> pronouns = participle || ending.person() == null
                    ? Collections.singletonList(null)
                    : LanguageRules.pronouns(language, ending.person(), ending.number(), imperative);
            for (String pronoun : pronouns) {
                List<String> suffixes = voseo(endings, ending, mood, tense, pronoun);
                var forms = new ArrayList<String>();
                for (String suffix : suffixes) {
                    String form = LanguageRules.combine(stem, suffix);
                    if (!form.equals("-")) {
                        form = render(context, ending, pronoun, form, mood, tense, primaryTense, showPronouns);
                    }
                    forms.add(form);
                }
                Gender gender = LanguageRules.pronounGender(language, pronoun);
                result.add(new Conjugation(ending.person(), ending.number(),
                        gender == null ? ending.gender() : gender, pronoun, forms));
            }
        }
        return List.copyOf(result);
    }

    private String render(Context context, Ending ending, String pronoun, String form,
            String mood, String tense, String primaryTense, boolean showPronouns) {
        boolean imperative = mood.equals(LanguageRules.imperativeMood(language));
        boolean personalInfinitive = language == Language.PT && tense.equals("infinitivo-pessoal-presente");
        boolean finite = ending.person() != null && !imperative;
        if (pronoun != null && finite) {
            String prefix = showPronouns ? pronoun : "";
            if (context.reflexive() && !hasEmbeddedReflexiveEndings(context)) {
                String clitic = LanguageRules.reflexivePronoun(language, ending.person(), ending.number());
                if (language == Language.PT && !mood.equals("subjuntivo")) {
                    form += "-" + clitic;
                } else {
                    prefix = prefix.isEmpty() ? clitic : prefix + " " + clitic;
                }
            }
            if (!prefix.isEmpty()) {
                if (personalInfinitive) {
                    form += " " + prefix;
                } else {
                    form = language == Language.FR ? LanguageRules.joinFrench(prefix, form) : prefix + " " + form;
                }
            }
            if (showPronouns && mood.equals(LanguageRules.subjunctiveMood(language))) {
                form = LanguageRules.subjunctivePrefix(language, form, primaryTense, pronoun);
            }
        } else if (imperative) {
            if (context.reflexive() && !hasEmbeddedReflexiveEndings(context) && ending.person() != null) {
                form = reflexiveImperative(form, ending);
            } else if (language == Language.PT && showPronouns && pronoun != null) {
                form += " " + pronoun;
            }
        } else if (context.reflexive() && language == Language.FR && tense.equals("participe-passé")) {
            form = "étant " + form;
        }
        if (imperative && (tense.equals("negativo") || tense.equals("negativ"))) {
            form = switch (language) {
                case ES -> "no " + form;
                case PT -> "não " + form;
                case RO -> "nu " + form;
                default -> form;
            };
        }
        if (personalInfinitive) {
            form = "por " + form;
        }
        return form;
    }

    private boolean hasEmbeddedReflexiveEndings(Context context) {
        return language == Language.PT && context.template().name().endsWith("-se");
    }

    private String reflexiveImperative(String form, Ending ending) {
        String clitic = LanguageRules.reflexivePronoun(language, ending.person(), ending.number());
        return switch (language) {
            case FR -> form + "-" + switch (ending.person()) {
                case SECOND -> ending.number() == GrammaticalNumber.SINGULAR ? "toi" : "vous";
                case FIRST -> "nous";
                case THIRD -> "se";
            };
            case PT -> form + "-" + clitic;
            // Preserve the source inflectors' non-finite pronoun suffix convention.
            case ES, IT, CA, RO -> form + " " + LanguageRules.pronouns(
                    language, ending.person(), ending.number(), true).get(0);
        };
    }

    private List<String> voseo(List<Ending> endings, Ending ending, String mood, String tense, String pronoun) {
        if (language != Language.ES || !"vos".equals(pronoun)
                || !(tense.equals("presente") && (mood.equals("indicativo") || mood.equals("subjuntivo"))
                || mood.equals("imperativo") && tense.equals("afirmativo"))) {
            return ending.forms();
        }
        Ending plural = endings.stream().filter(e -> e.person() == Person.SECOND
                && e.number() == GrammaticalNumber.PLURAL).findFirst().orElseThrow(
                        () -> new IllegalStateException("Missing vosotros ending for voseo"));
        return plural.forms().stream().map(suffix -> {
            if (suffix.equals("-") || suffix.isEmpty()) {
                return suffix;
            }
            if (mood.equals("imperativo")) {
                if (suffix.endsWith("d")) {
                    suffix = suffix.substring(0, suffix.length() - 1);
                }
            } else if (suffix.length() > 1 && suffix.charAt(suffix.length() - 2) == 'i') {
                suffix = suffix.substring(0, suffix.length() - 2) + suffix.charAt(suffix.length() - 1);
            }
            if (mood.equals("subjuntivo")) {
                return LanguageRules.stripAccents(suffix);
            }
            var value = new StringBuilder(suffix);
            for (int i = Math.max(0, value.length() - 2); i < value.length(); i++) {
                int vowel = "aei".indexOf(value.charAt(i));
                if (vowel >= 0) {
                    value.setCharAt(i, "áéí".charAt(vowel));
                }
            }
            return value.toString();
        }).collect(Collectors.toUnmodifiableList());
    }

    private List<Conjugation> compound(Context context, Compound spec, boolean showPronouns) {
        if (language == Language.FR && context.reflexive() && spec.mood().equals("imperatif")) {
            return List.of();
        }
        String auxVerb = LanguageRules.auxiliary(language, context.verb().infinitive(), context.reflexive(), spec.tense());
        Context auxiliary = resolve(auxVerb);
        auxiliary = new Context(auxiliary.verb(), auxiliary.template(), auxiliary.stem(), context.reflexive());
        boolean personalInfinitive = language == Language.PT && spec.tense().equals("infinitivo-pessoal-composto");
        List<Conjugation> auxForms = simple(auxiliary, spec.auxiliaryMood(), spec.auxiliaryTense(),
                spec.tense(), showPronouns && !personalInfinitive);
        List<Ending> primaryPersons = context.template().moods()
                .getOrDefault(spec.mood(), context.template().moods().get(LanguageRules.indicativeMood(language)))
                .get(spec.auxiliaryTense());
        var allowedPersons = new LinkedHashSet<Person>();
        if (primaryPersons != null) {
            for (Ending ending : primaryPersons) {
                allowedPersons.add(ending.person());
            }
        }
        List<Conjugation> participles = simple(new Context(context.verb(), context.template(), context.stem(), false),
                LanguageRules.participleMood(language), LanguageRules.participleTense(language),
                spec.tense(), false);
        boolean agree = auxVerb.equals("être") || auxVerb.equals("essere");
        var result = new ArrayList<Conjugation>();
        for (Conjugation aux : auxForms) {
            if (primaryPersons != null && !allowedPersons.contains(aux.person())) {
                continue;
            }
            if (language == Language.RO) {
                result.add(romanianCompound(context, spec, aux, participles, showPronouns));
                continue;
            }
            if (participles.isEmpty()) {
                continue;
            }
            if (agree) {
                List<Gender> genders = aux.person() == Person.THIRD && aux.gender() != null
                        ? List.of(aux.gender()) : List.of(Gender.FEMININE, Gender.MASCULINE);
                for (Gender gender : genders) {
                    Conjugation participle = participles.stream()
                            .filter(p -> p.gender() == gender && p.number() == aux.number())
                            .findFirst().orElse(participles.get(0));
                    result.add(new Conjugation(aux.person(), aux.number(), gender, aux.pronoun(),
                            combineCompound(aux.forms().get(0), participle.forms())));
                }
            } else {
                String helping = aux.forms().get(0);
                if (language == Language.ES && helping.endsWith("hay")) {
                    helping = helping.substring(0, helping.length() - 1);
                }
                if (personalInfinitive && helping.startsWith("por ")) {
                    helping = helping.substring(4);
                }
                result.add(new Conjugation(aux.person(), aux.number(), aux.gender(), aux.pronoun(),
                        combineCompound(helping, participles.get(0).forms())));
            }
        }
        return List.copyOf(result);
    }

    private Conjugation romanianCompound(Context context, Compound spec, Conjugation aux,
            List<Conjugation> participles, boolean showPronouns) {
        String tense = spec.tense();
        String mood = spec.mood();
        int slot = aux.person().ordinal() + (aux.number() == GrammaticalNumber.PLURAL ? 3 : 0);
        String pronoun = showPronouns ? aux.pronoun() + " " : "";
        String helping = aux.forms().get(tense.startsWith("viitor") && aux.forms().size() > 1 ? 1 : 0);
        String participle = participles.isEmpty() ? "-" : participles.get(0).forms().get(0);
        String form;
        if (mood.equals("condițional")) {
            String conditional = new String[] {"aş", "ai", "ar", "am", "aţi", "ar"}[slot];
            form = pronoun + conditional + (tense.equals("perfect") ? " fi " + participle : " " + context.verb().infinitive());
        } else if (mood.equals("conjunctiv") && tense.equals("perfect")) {
            form = pronoun + "să fi " + participle;
        } else if (tense.equals("viitor-1-popular")) {
            form = helping.replace(" să ", " o să ");
            if (!showPronouns) {
                form = "o să " + helping;
            }
        } else if (tense.equals("viitor-1")) {
            form = helping + " " + context.verb().infinitive();
        } else {
            String particle = switch (tense) {
                case "viitor-2" -> " fi ";
                case "viitor-2-popular" -> " să fi ";
                default -> " ";
            };
            form = helping + particle + participle;
        }
        return new Conjugation(aux.person(), aux.number(), aux.gender(), aux.pronoun(), List.of(form));
    }

    private static List<String> combineCompound(String auxiliary, List<String> participles) {
        if (auxiliary.equals("-")) {
            return List.of("-");
        }
        return participles.stream().map(p -> p.equals("-") ? "-" : auxiliary + " " + p)
                .collect(Collectors.toUnmodifiableList());
    }

    private static String normalize(String value) {
        String result = Normalizer.normalize(Objects.requireNonNull(value, "value").strip(), Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Value must not be blank");
        }
        return result;
    }

    private Verb lookup(String normalized) {
        Verb exact = data.verbs.get(normalized);
        return exact == null ? data.verbsWithoutAccents.get(LanguageRules.stripAccents(normalized)) : exact;
    }

}
