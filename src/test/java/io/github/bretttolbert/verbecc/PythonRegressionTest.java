package io.github.bretttolbert.verbecc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PythonRegressionTest {
    static Stream<Arguments> pythonCases() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                PythonRegressionTest.class.getResourceAsStream("/python-regressions.tsv")), StandardCharsets.UTF_8))) {
            return reader.lines().map(line -> Arguments.of((Object) line.split("\t", -1))).toList().stream();
        }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("pythonCases")
    void matchesPythonTestExpectations(String[] row) {
        var actual = new CompleteConjugator(row[0]).conjugateMoodTense(row[1], row[2], row[3]);
        var expected = Arrays.stream(row).skip(5).map(value -> Arrays.asList(value.split("\\|", -1))).toList();
        var forms = actual.stream().map(c -> row[4].equals("all") ? c.forms() : java.util.List.of(c.forms().getFirst())).toList();
        assertEquals(expected, forms,
                () -> String.join(" ", Arrays.copyOf(row, 4)));
    }
}
