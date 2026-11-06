package io.github.bretttolbert.verbecc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

final class ConjugationData {
    record Ending(Person person, GrammaticalNumber number, Gender gender, List<String> forms) {}
    record Template(String name, boolean stripAccents, Map<String, Map<String, List<Ending>>> moods) {}

    final Map<String, Verb> verbs;
    final Map<String, Verb> verbsWithoutAccents;
    final Map<String, Template> templates;

    ConjugationData(Language language) {
        var parsedVerbs = new LinkedHashMap<String, Verb>();
        for (XmlElement v : children(read(language, "verbs"), "v")) {
            var verb = new Verb(text(v, "i"), text(v, "t"), text(v, "en"),
                    !children(v, "aspirate-h").isEmpty());
            parsedVerbs.put(verb.infinitive(), verb);
        }
        var unaccentedVerbs = new LinkedHashMap<String, Verb>();
        parsedVerbs.forEach((infinitive, verb) ->
                unaccentedVerbs.putIfAbsent(LanguageRules.stripAccents(infinitive), verb));
        var parsedTemplates = new LinkedHashMap<String, Template>();
        for (XmlElement template : children(read(language, "conjugations"), "template")) {
            var moods = new LinkedHashMap<String, Map<String, List<Ending>>>();
            for (XmlElement mood : children(template, null)) {
                var tenses = new LinkedHashMap<String, List<Ending>>();
                String moodName = mood.getTagName().toLowerCase(Locale.ROOT);
                for (XmlElement tense : children(mood, null)) {
                    List<XmlElement> persons = children(tense, "p");
                    var endings = new ArrayList<Ending>();
                    for (int i = 0; i < persons.size(); i++) {
                        var forms = children(persons.get(i), "i").stream()
                                .map(XmlElement::getTextContent).collect(Collectors.toUnmodifiableList());
                        if (forms.isEmpty()) {
                            continue;
                        }
                        Person person = null;
                        GrammaticalNumber number = null;
                        Gender gender = null;
                        if (persons.size() > 1) {
                            if (moodName.equals(LanguageRules.participleMood(language))) {
                                if (persons.size() != 4) {
                                    throw new IllegalStateException("Invalid participle: " + template.getAttribute("name"));
                                }
                                boolean italian = language == Language.IT;
                                gender = (italian ? i % 2 == 0 : i < 2) ? Gender.MASCULINE : Gender.FEMININE;
                                number = (italian ? i < 2 : i % 2 == 0)
                                        ? GrammaticalNumber.SINGULAR : GrammaticalNumber.PLURAL;
                            } else {
                                int slot = moodName.equals(LanguageRules.imperativeMood(language))
                                        ? imperativeSlot(language, i, persons.size()) : i;
                                if (slot > 5) {
                                    throw new IllegalStateException("Invalid person slot: " + slot);
                                }
                                person = Person.values()[slot % 3];
                                number = slot < 3 ? GrammaticalNumber.SINGULAR : GrammaticalNumber.PLURAL;
                            }
                        }
                        endings.add(new Ending(person, number, gender, forms));
                    }
                    tenses.put(tense.getTagName().toLowerCase(Locale.ROOT), List.copyOf(endings));
                }
                moods.put(moodName, Collections.unmodifiableMap(tenses));
            }
            String modifier = template.getAttribute("modify-stem");
            if (!modifier.isEmpty() && !modifier.equals("strip-accents")) {
                throw new IllegalStateException("Unknown stem modifier: " + modifier);
            }
            String name = template.getAttribute("name");
            // Python's sorted lookup selects the first occurrence of duplicate template names.
            parsedTemplates.putIfAbsent(name, new Template(name, modifier.equals("strip-accents"),
                    Collections.unmodifiableMap(moods)));
        }
        verbs = Collections.unmodifiableMap(parsedVerbs);
        verbsWithoutAccents = Collections.unmodifiableMap(unaccentedVerbs);
        templates = Collections.unmodifiableMap(parsedTemplates);
    }

    private static int imperativeSlot(Language language, int index, int count) {
        int[] slots = switch (language) {
            case FR -> new int[] {1, 3, 4};
            case ES, CA -> new int[] {1, 2, 3, 4, 5};
            case IT, PT -> new int[] {0, 1, 2, 3, 4, 5};
            case RO -> new int[] {1, 4};
        };
        if (count != slots.length) {
            throw new IllegalStateException("Unexpected imperative size for " + language + ": " + count);
        }
        return slots[index];
    }

    private static XmlElement read(Language language, String kind) {
        String resource = "/io/github/bretttolbert/verbecc/data/xml/" + kind + "/" + kind + "-" + language.code() + ".xml";
        try (InputStream stream = ConjugationData.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing conjugation data: " + resource);
            }
            // Android's DOM implementation rejects accented mood/tense tag names.
            // SAX preserves those names on both Android and the JDK.
            return parseXml(stream);
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new IllegalStateException("Cannot load conjugation data: " + resource, e);
        }

    }

    static XmlElement parseXml(InputStream stream)
            throws IOException, SAXException, ParserConfigurationException {
        var parser = SAXParserFactory.newInstance().newSAXParser();
        var handler = new XmlHandler();
        parser.parse(stream, handler);
        if (handler.root == null) {
            throw new SAXException("Empty conjugation XML");
        }
        return handler.root;
    }

    private static String text(XmlElement parent, String tag) {
        List<XmlElement> elements = children(parent, tag);
        return elements.isEmpty() ? "" : elements.get(0).getTextContent();
    }

    private static List<XmlElement> children(XmlElement parent, String tag) {
        var result = new ArrayList<XmlElement>();
        for (var element : parent.children) {
            if (tag == null || element.getTagName().equals(tag)) {
                result.add(element);
            }
        }
        return result;
    }

    static final class XmlElement {
        final String name;
        final Map<String, String> attributes = new LinkedHashMap<>();
        final List<XmlElement> children = new ArrayList<>();
        final StringBuilder text = new StringBuilder();

        XmlElement(String name, Attributes attributes) {
            this.name = name;
            for (int i = 0; i < attributes.getLength(); i++) {
                this.attributes.put(attributes.getQName(i), attributes.getValue(i));
            }
        }

        String getTagName() {
            return name;
        }

        String getAttribute(String name) {
            return attributes.getOrDefault(name, "");
        }

        String getTextContent() {
            return text.toString();
        }
    }

    private static final class XmlHandler extends DefaultHandler {
        final ArrayDeque<XmlElement> stack = new ArrayDeque<>();
        XmlElement root;

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes) {
            var element = new XmlElement(name, attributes);
            if (stack.isEmpty()) {
                root = element;
            } else {
                stack.peek().children.add(element);
            }
            stack.push(element);
        }

        @Override
        public void characters(char[] characters, int start, int length) {
            if (!stack.isEmpty()) {
                stack.peek().text.append(characters, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String name) {
            stack.pop();
        }

        @Override
        public org.xml.sax.InputSource resolveEntity(String publicId, String systemId) throws SAXException {
            throw new SAXException("External XML resources are not allowed: " + systemId);
        }
    }
}
