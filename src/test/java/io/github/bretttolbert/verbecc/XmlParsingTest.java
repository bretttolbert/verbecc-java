package io.github.bretttolbert.verbecc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

class XmlParsingTest {
    private static ByteArrayInputStream xml(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void preservesAccentedTagsAndForms() throws Exception {
        var root = ConjugationData.parseXml(xml("<présent><i>je paie</i><i>je paye</i></présent>"));
        assertEquals("présent", root.getTagName());
        assertEquals("je paie", root.children.get(0).getTextContent());
        assertEquals("je paye", root.children.get(1).getTextContent());
    }

    @Test
    void allowsInternalDtds() throws Exception {
        var root = ConjugationData.parseXml(xml("<!DOCTYPE v [<!ELEMENT v (#PCDATA)>]><v>manger</v>"));
        assertEquals("manger", root.getTextContent());
    }

    @Test
    void rejectsExternalDtds() {
        assertThrows(SAXException.class, () -> ConjugationData.parseXml(
                xml("<!DOCTYPE v SYSTEM 'file:///not-allowed.dtd'><v/>")));
    }

    @Test
    void rejectsExternalGeneralEntities() {
        assertThrows(SAXException.class, () -> ConjugationData.parseXml(
                xml("<!DOCTYPE v [<!ENTITY external SYSTEM 'file:///not-allowed'>]><v>&external;</v>")));
    }

    @Test
    void rejectsExternalParameterEntities() {
        assertThrows(SAXException.class, () -> ConjugationData.parseXml(xml(
                "<!DOCTYPE v [<!ENTITY % external SYSTEM 'file:///not-allowed'>%external;]><v/>")));
    }
}
