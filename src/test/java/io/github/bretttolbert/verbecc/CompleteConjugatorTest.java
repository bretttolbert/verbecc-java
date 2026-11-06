package io.github.bretttolbert.verbecc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CompleteConjugatorTest {
    private static List<String> firstForms(List<Conjugation> conjugations) {
        return conjugations.stream().map(c -> c.forms().getFirst()).toList();
    }

    @ParameterizedTest
    @CsvSource({
        "ca,ser,indicatiu,present,jo sóc",
        "es,tener,indicativo,presente,yo tengo",
        "fr,manger,indicatif,présent,je mange",
        "it,avere,indicativo,presente,io ho",
        "pt,ter,indicativo,presente,eu tenho",
        "ro,face,indicativ,prezent,eu fac"
    })
    void conjugatesSixLanguages(String language, String verb, String mood, String tense, String expected) {
        var conjugator = new CompleteConjugator(language);
        assertEquals(expected, conjugator.conjugateMoodTense(verb, mood, tense).getFirst().forms().getFirst());
        assertEquals(conjugator.conjugateMoodTense(verb, mood, tense), conjugator.conjugate(verb).moods().get(mood).get(tense));
    }

    @Test
    void frenchPresentIncludesEveryPronoun() {
        assertEquals(List.of("je mange", "tu manges", "il mange", "elle mange", "on mange",
                "nous mangeons", "vous mangez", "ils mangent", "elles mangent"),
                firstForms(new CompleteConjugator("fr").conjugateMoodTense("manger", "indicatif", "présent")));
    }

    @Test
    void frenchCompoundUsesAvoir() {
        assertEquals(List.of("j'ai mangé", "tu as mangé", "il a mangé", "elle a mangé", "on a mangé",
                "nous avons mangé", "vous avez mangé", "ils ont mangé", "elles ont mangé"),
                firstForms(new CompleteConjugator("fr").conjugateMoodTense("manger", "indicatif", "passé-composé")));
    }

    @Test
    void frenchCompoundAgreesWithGenderAndNumber() {
        assertEquals(List.of("je suis allée", "je suis allé", "tu es allée", "tu es allé", "il est allé",
                "elle est allée", "on est allée", "on est allé", "nous sommes allées", "nous sommes allés",
                "vous êtes allées", "vous êtes allés", "ils sont allés", "elles sont allées"),
                firstForms(new CompleteConjugator("fr").conjugateMoodTense("aller", "indicatif", "passé-composé")));
    }

    @Test
    void frenchSubjunctiveElision() {
        assertEquals(List.of("que j'aie mangé", "que tu aies mangé", "qu'il ait mangé", "qu'elle ait mangé",
                "qu'on ait mangé", "que nous ayons mangé", "que vous ayez mangé", "qu'ils aient mangé",
                "qu'elles aient mangé"),
                firstForms(new CompleteConjugator("fr").conjugateMoodTense("manger", "subjonctif", "passé")));
    }

    @Test
    void frenchReflexives() {
        var conjugator = new CompleteConjugator("fr");
        assertEquals("je m'habille", conjugator.conjugateMoodTense("s'habiller", "indicatif", "présent")
                .getFirst().forms().getFirst());
        assertEquals("je me suis levée", conjugator.conjugateMoodTense("se lever", "indicatif", "passé-composé")
                .getFirst().forms().getFirst());
        assertEquals(List.of("lève-toi", "levons-nous", "levez-vous"),
                firstForms(conjugator.conjugateMoodTense("se lever", "imperatif", "imperatif-présent")));
        assertTrue(conjugator.conjugateMoodTense("se lever", "imperatif", "imperatif-passé").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> conjugator.conjugate("se pleuvoir"));
        assertThrows(IllegalArgumentException.class, () -> conjugator.conjugate("s'être"));
    }

    @Test
    void emptyPersonsAreOmittedButSlotsPreserved() {
        var conjugations = new CompleteConjugator("fr").conjugateMoodTense("pleuvoir", "indicatif", "présent");
        assertTrue(conjugations.size() < 9);
        assertTrue(conjugations.stream().allMatch(c -> c.person() == Person.THIRD));
        assertEquals("il pleut", conjugations.getFirst().forms().getFirst());
    }

    @Test
    void alternatesArePreserved() {
        var forms = new CompleteConjugator("ca").conjugateMoodTense("ser", "condicional", "present");
        assertEquals(List.of("jo seria", "jo fora"), forms.getFirst().forms());
        assertEquals(List.of("je paie", "je paye"), new CompleteConjugator("fr")
                .conjugateMoodTense("payer", "indicatif", "présent").getFirst().forms());
    }

    @Test
    void spanishVoseoAndFormalPronouns() {
        assertEquals(List.of("yo tengo", "tú tienes", "vos tenés", "él tiene", "ella tiene", "usted tiene",
                "nosotros tenemos", "vosotros tenéis", "ellos tienen", "ellas tienen", "ustedes tienen"),
                firstForms(new CompleteConjugator("es").conjugateMoodTense("tener", "indicativo", "presente")));
    }

    @Test
    void spanishCompoundsUseHaNotHay() {
        var forms = new CompleteConjugator("es").conjugateMoodTense("hablar", "indicativo", "pretérito-perfecto-compuesto");
        assertEquals("yo he hablado", forms.getFirst().forms().getFirst());
        assertEquals("él ha hablado", forms.stream().filter(c -> "él".equals(c.pronoun())).findFirst().orElseThrow().forms().getFirst());
    }

    @Test
    void spanishNegativeImperative() {
        var forms = new CompleteConjugator("es").conjugateMoodTense("hablar", "imperativo", "negativo");
        assertEquals("no hables", forms.getFirst().forms().getFirst());
    }

    @Test
    void italianCompoundAndReflexive() {
        var conjugator = new CompleteConjugator("it");
        assertEquals("io ho parlato", conjugator.conjugateMoodTense("parlare", "indicativo", "passato-prossimo")
                .getFirst().forms().getFirst());
        var forms = conjugator.conjugateMoodTense("andare", "indicativo", "passato-prossimo");
        assertTrue(firstForms(forms).contains("lei è andata"));
        assertTrue(firstForms(forms).contains("noi siamo andati"));
        assertEquals("io mi alzo", conjugator.conjugateMoodTense("alzarsi", "indicativo", "presente")
                .getFirst().forms().getFirst());
    }

    @Test
    void participlesUseLanguageSpecificOrdering() {
        var french = new CompleteConjugator("fr").conjugateMoodTense("aimer", "participe", "participe-passé");
        assertEquals(Gender.MASCULINE, french.get(1).gender());
        assertEquals(GrammaticalNumber.PLURAL, french.get(1).number());
        var italian = new CompleteConjugator("it").conjugateMoodTense("amare", "participio", "participio-passato");
        assertEquals(Gender.FEMININE, italian.get(1).gender());
        assertEquals(GrammaticalNumber.SINGULAR, italian.get(1).number());
        assertNull(italian.getFirst().person());
        assertNull(italian.getFirst().pronoun());
    }

    @Test
    void portuguesePersonalInfinitiveAndImperative() {
        var conjugator = new CompleteConjugator("pt");
        assertEquals(List.of("por ter eu", "por teres tu", "por ter ele", "por ter ela", "por ter você",
                "por termos nós", "por terdes vós", "por terem eles", "por terem elas", "por terem vocês"),
                firstForms(conjugator.conjugateMoodTense("ter", "infinitivo", "infinitivo-pessoal-presente")));
        assertEquals(List.of("-", "tem tu", "tenha você", "tenhamos nós", "tende vós", "tenham vocês"),
                firstForms(conjugator.conjugateMoodTense("ter", "imperativo", "afirmativo")));
        assertEquals(List.of("ter tido", "teres tido", "ter tido", "ter tido", "ter tido", "termos tido",
                "terdes tido", "terem tido", "terem tido", "terem tido"),
                firstForms(conjugator.conjugateMoodTense("ter", "infinitivo", "infinitivo-pessoal-composto")));
    }

    @Test
    void portugueseReflexivePlacement() {
        var conjugator = new CompleteConjugator("pt");
        assertEquals("eu visto-me", conjugator.conjugateMoodTense("vestir-se", "indicativo", "presente")
                .getFirst().forms().getFirst());
        assertEquals("se eu me vestisse", conjugator.conjugateMoodTense("vestir-se", "subjuntivo", "pretérito-imperfeito")
                .getFirst().forms().getFirst());
        assertEquals("eu tenho-me vestido", conjugator.conjugateMoodTense("vestir-se", "indicativo", "pretérito-perfeito-composto")
                .getFirst().forms().getFirst());
    }

    @ParameterizedTest
    @CsvSource({
        "indicativ,perfect-compus,eu am făcut",
        "indicativ,viitor-1,eu voi face",
        "indicativ,viitor-2,eu voi fi făcut",
        "indicativ,viitor-1-popular,eu o să fac",
        "indicativ,viitor-2-popular,eu am să fi făcut",
        "conjunctiv,prezent,eu să fac",
        "conjunctiv,perfect,eu să fi făcut",
        "condițional,prezent,eu aş face",
        "condițional,perfect,eu aş fi făcut"
    })
    void romanianSpecialCompounds(String mood, String tense, String expected) {
        var conjugator = new CompleteConjugator("ro");
        assertEquals(expected, conjugator.conjugateMoodTense("face", mood, tense).getFirst().forms().getFirst());
        assertEquals(expected, conjugator.conjugate("face").moods().get(mood).get(tense).getFirst().forms().getFirst());
    }

    @Test
    void omittedPronounsKeepMetadata() {
        var conjugator = new CompleteConjugator("fr");
        var forms = conjugator.conjugateMoodTense("manger", "indicatif", "présent", false);
        assertEquals("mange", forms.getFirst().forms().getFirst());
        assertEquals("je", forms.getFirst().pronoun());
        assertEquals(Person.FIRST, forms.getFirst().person());
        assertEquals("ai mangé", conjugator.conjugateMoodTense("manger", "indicatif", "passé-composé", false)
                .getFirst().forms().getFirst());
        assertEquals("aie mangé", conjugator.conjugateMoodTense("manger", "subjonctif", "passé", false)
                .getFirst().forms().getFirst());
    }

    @Test
    void normalizesUnicodeCaseAndWhitespaceIndependentlyOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var conjugator = new CompleteConjugator("FR");
            assertEquals(conjugator.conjugate("être"), conjugator.conjugate(" E\u0302TRE "));
            assertEquals(conjugator.conjugateMood("finir", "indicatif"),
                    conjugator.conjugateMood("FINIR", "INDICATIF"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejectsUnknownVerbsAndInvalidParameters() {
        var conjugator = new CompleteConjugator("fr");
        assertThrows(VerbNotFoundException.class, () -> conjugator.conjugate("not-a-real-verb"));
        assertThrows(IllegalArgumentException.class, () -> new CompleteConjugator("en"));
        assertThrows(IllegalArgumentException.class, () -> conjugator.conjugate(" "));
        assertThrows(NullPointerException.class, () -> conjugator.conjugate(null));
        assertThrows(IllegalArgumentException.class, () -> conjugator.conjugateMood("manger", "invalid"));
        assertThrows(IllegalArgumentException.class, () -> conjugator.conjugateMoodTense("manger", "indicatif", "invalid"));
        assertThrows(IllegalArgumentException.class, () -> conjugator.getVerbsThatStartWith("a", -1));
    }

    @Test
    void reflexiveOnlyDictionaryEntriesUseTheirOwnTemplates() {
        assertEquals("yo me ababillo", new CompleteConjugator("es")
                .conjugateMoodTense("ababillarse", "indicativo", "presente").getFirst().forms().getFirst());
        assertEquals("eu abarrego-me", new CompleteConjugator("pt")
                .conjugateMoodTense("abarregar-se", "indicativo", "presente").getFirst().forms().getFirst());
    }

    @Test
    void inconsistentSourceMappingsAreReportedRatherThanGuessed() {
        var exception = assertThrows(IllegalStateException.class, () -> new CompleteConjugator("it").conjugate("astrignere"));
        assertTrue(exception.getMessage().contains("str:ingere"));
    }

    @Test
    void dictionarySearchAndMetadata() {
        var conjugator = new CompleteConjugator("fr");
        assertTrue(conjugator.getInfinitives().size() > 7000);
        assertEquals(146, conjugator.getTemplateNames().size());
        assertEquals("aim:er", conjugator.findVerbByInfinitive("parler").template());
        assertFalse(conjugator.findVerbByInfinitive("parler").translationEn().isBlank());
        assertEquals(3, conjugator.getVerbsThatStartWith("PAR", 3).size());
        assertTrue(conjugator.getVerbsThatStartWith("PAR", 3).stream().allMatch(v -> v.startsWith("par")));
        assertEquals(List.of(), conjugator.getVerbsThatStartWith("", 0));
        assertEquals(conjugator.conjugate("être"), conjugator.conjugate("etre"));
        assertTrue(conjugator.getVerbsThatStartWith("etre", 10).contains("être"));
        assertEquals("abañar", new CompleteConjugator("es").findVerbByInfinitive("abañar").infinitive());
    }

    @Test
    void resultsAreDeeplyImmutable() {
        var result = new CompleteConjugator("fr").conjugate("manger");
        assertThrows(UnsupportedOperationException.class, () -> result.moods().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.moods().get("indicatif").clear());
        var tense = result.moods().get("indicatif").get("présent");
        assertThrows(UnsupportedOperationException.class, tense::clear);
        assertThrows(UnsupportedOperationException.class, () -> tense.getFirst().forms().clear());
        var mutableForms = new ArrayList<>(List.of("a"));
        var conjugation = new Conjugation(null, null, null, null, mutableForms);
        mutableForms.clear();
        assertEquals(List.of("a"), conjugation.forms());
    }

    @Test
    void concurrentCallsAreDeterministic() throws Exception {
        var conjugator = new CompleteConjugator("fr");
        var expected = conjugator.conjugate("se lever");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 20)
                    .<java.util.concurrent.Callable<CompleteConjugation>>mapToObj(i -> () -> conjugator.conjugate("se lever")).toList();
            for (var result : executor.invokeAll(tasks)) {
                assertEquals(expected, result.get());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void allDictionaryEntriesHaveTemplates(Language language) {
        var conjugator = new CompleteConjugator(language);
        var templates = new java.util.HashSet<>(conjugator.getTemplateNames());
        assertFalse(conjugator.getVerbs().isEmpty());
        for (Verb verb : conjugator.getVerbs()) {
            assertTrue(templates.contains(verb.template()), () -> "Missing " + verb.template() + " for " + verb.infinitive());
        }
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void everyReferencedTemplateWithMatchingSuffixCanConjugateARepresentativeVerb(Language language) {
        var conjugator = new CompleteConjugator(language);
        var representatives = new java.util.HashSet<String>();
        for (Verb verb : conjugator.getVerbs()) {
            String suffix = verb.template().substring(verb.template().indexOf(':') + 1);
            if (!verb.infinitive().endsWith(suffix)) {
                continue;
            }
            if (representatives.add(verb.template())) {
                assertDoesNotThrow(() -> conjugator.conjugate(verb.infinitive()), () -> language + ": " + verb);
            }
        }
        assertTrue(representatives.size() >= conjugator.getTemplateNames().size() - 3);
    }
}
