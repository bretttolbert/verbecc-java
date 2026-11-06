package io.github.bretttolbert.verbecc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class XmlIntegrityTest {
    @ParameterizedTest
    @CsvSource(textBlock = """
        conjugations/ca,e9f3c403640b56d5f79b7fc943949ce2058e3fee7f4b7aa3ff7b4ea0bf5b39ac
        verbs/ca,b9d5bd9b6ab4bfe48f2d9ae421b1116852a46c4360bbaddc8092995846f25b25
        conjugations/es,84240077ebaaac23c160e5e14b2cbb41cb101b74cc7544daeb5262efed12cc4f
        verbs/es,798be3b00339216212029471921789951be1a578ff46592db132ba0efc64cb51
        conjugations/fr,98794bd4ae37fcf0f776afc9bbf6427b927a6999bb97e13fc2cf5125fe6891f9
        verbs/fr,f51b0a868f63036313f13e97d7eeca8ff5a0c3d4ffabfbe4b46150af379c0042
        conjugations/it,93d56bd17dd34cc5385aec0f16943e1048fb14a497e3c77cb275f95e7adff4cd
        verbs/it,f5c206ec00264a954cd0a34e839ed4bb216c7b2bd22c01bfd149ef8337839e7b
        conjugations/pt,802b377c9bc0b6f3001c6a51460d16434505e8c6e6aaf44f26ac2d7f04099110
        verbs/pt,5d9650b2b11b69c9fdd1d505f13f8ba8d021684b05332ba6c22885ddf9685cb0
        conjugations/ro,8e71da6f3accf4f7ab6dd1850d4dee7d86c70bd76a68d466beb60c62a7e0b450
        verbs/ro,de51514789fccdf6f46dc7edb2c1256bf90fa00671413fbdee6f790b84d01b4b
        """)
    void copiedXmlIsByteForByteIdenticalToPythonSource(String key, String expectedHash)
            throws IOException, NoSuchAlgorithmException {
        String[] parts = key.split("/");
        String resource = "/io/github/bretttolbert/verbecc/data/xml/" + parts[0] + "/" + parts[0] + "-" + parts[1] + ".xml";
        try (var stream = XmlIntegrityTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
            assertEquals(expectedHash, actual, resource);
        }
    }
}
