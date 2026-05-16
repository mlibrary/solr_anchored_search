package edu.umich.lib.solr.integration;

import edu.umich.lib.solr.FullyAnchoredSearchFilter;
import edu.umich.lib.solr.FullyAnchoredSearchFilterFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.HashMap;
import java.util.stream.Stream;

import static edu.umich.lib.solr.integration.TokenStreamAsserter.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link FullyAnchoredSearchFilter} and its factory.
 *
 * <p>The fully-anchored filter appends position numbers to every token AND
 * appends {@code "00"} to the last token only (its maximum position).  The
 * effect is that a phrase query must match the entire field value — no prefix
 * or suffix partial matches are possible.
 *
 * <p>Example: {@code "Bill Dueber"} →
 * {@code ["Bill1", "Dueber200"]}
 *
 * <p>These tests are written test-first against the Solr 10 target code paths;
 * they will fail to compile until Stage 2 adds the {@code NAME} field and
 * fixes the factory import.
 */
@DisplayName("FullyAnchoredSearchFilter – integration")
class FullyAnchoredSearchFilterIntegrationTest {

    private Analyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer source = new WhitespaceTokenizer();
                TokenStream result = new FullyAnchoredSearchFilter(source);
                return new TokenStreamComponents(source, result);
            }
        };
    }

    @AfterEach
    void tearDown() {
        analyzer.close();
    }

    // -------------------------------------------------------------------------
    // Core filter behaviour
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Term transformation")
    class TermTransformation {

        @Test
        @DisplayName("Single token gets position 1 and '00' appended (it is both first and last)")
        void singleToken_appendsPositionAndDoubleZero() throws IOException {
            // pos=1 and it IS the maximum position, so suffix is "1" + "00" = "100"
            var tokens = analyze(analyzer, "Bill");
            assertTerms(tokens, "Bill100");
        }

        @Test
        @DisplayName("Two tokens: first gets position, last gets position + '00'")
        void twoTokens_lastHasDoubleZeroSuffix() throws IOException {
            var tokens = analyze(analyzer, "Bill Dueber");
            assertTerms(tokens, "Bill1", "Dueber200");
        }

        @Test
        @DisplayName("Three tokens: only the last token gets '00'")
        void threeTokens_onlyLastHasDoubleZeroSuffix() throws IOException {
            var tokens = analyze(analyzer, "one two three");
            assertTerms(tokens, "one1", "two2", "three300");
        }
    }

    // -------------------------------------------------------------------------
    // Position-increment preservation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Position increment handling")
    class PositionIncrementHandling {

        @Test
        @DisplayName("Normal whitespace-tokenized input preserves posIncr=1 per token")
        void normalTokens_preservePositionIncrementOne() throws IOException {
            var tokens = analyze(analyzer, "John Smith");
            assertPositionIncrements(tokens, 1, 1);
        }

        /**
         * When synonyms share a position, both tokens at that position receive
         * the same numeric suffix.  Only the token(s) at the overall maximum
         * position get the {@code "00"} suffix.
         *
         * Input (via ManualTokenStream): Bill(1), John(2), James(2), Dueber(3)
         * Expected:                       Bill1,   John2,  James2,  Dueber300
         * Position increments:            1,       1,      0,       1
         */
        @Test
        @DisplayName("Synonym tokens share position suffix; last position gets '00'")
        void synonymTokens_sharePositionSuffixAndLastGetsDoubleZero() throws IOException {
            var ms = new edu.umich.lib.solr.ManualTokenStream();
            ms.add("Bill",   1);
            ms.add("John",   2);
            ms.add("James",  2);  // synonym
            ms.add("Dueber", 3);  // maximum position → gets "00"

            var ts = new FullyAnchoredSearchFilter(ms);
            var tokens = drainTokenStream(ts);

            assertAll(
                    "synonym + fully-anchored suffixes",
                    () -> assertTerms(tokens, "Bill1", "John2", "James2", "Dueber300"),
                    () -> assertPositionIncrements(tokens, 1, 1, 0, 1)
            );
        }

        /**
         * When the final position has multiple synonyms, ALL tokens at that
         * position must receive the {@code "00"} suffix.
         *
         * Input: Alpha(1), Beta(2), Gamma(2) — both Beta and Gamma are at max pos 2.
         * Expected: Alpha1, Beta200, Gamma200
         */
        @Test
        @DisplayName("Multiple synonyms at the maximum position all receive '00' suffix")
        void multipleSynonymsAtMaxPosition_allGetDoubleZeroSuffix() throws IOException {
            var ms = new edu.umich.lib.solr.ManualTokenStream();
            ms.add("Alpha", 1);
            ms.add("Beta",  2);
            ms.add("Gamma", 2);  // synonym of Beta, also at max position

            var ts = new FullyAnchoredSearchFilter(ms);
            var tokens = drainTokenStream(ts);

            assertAll(
                    "all synonyms at max position get '00'",
                    () -> assertTerms(tokens, "Alpha1", "Beta200", "Gamma200"),
                    () -> assertPositionIncrements(tokens, 1, 1, 0)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Contrast with LeftAnchoredSearchFilter
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Distinction from LeftAnchoredSearchFilter")
    class DistinctionFromLeftAnchored {

        @Test
        @DisplayName("Last token differs: FullyAnchored appends '00', LeftAnchored does not")
        void lastToken_differsFromLeftAnchored() throws IOException {
            // FullyAnchored: Bill1, Dueber200
            var fullyTokens = analyze(analyzer, "Bill Dueber");
            assertEquals("Dueber200", fullyTokens.get(1).term(),
                    "FullyAnchored last token should have '00' suffix");

            // LeftAnchored: Bill1, Dueber2  (no '00')
            try (var leftAnalyzer = new Analyzer() {
                @Override
                protected Analyzer.TokenStreamComponents createComponents(String fieldName) {
                    Tokenizer source = new WhitespaceTokenizer();
                    return new Analyzer.TokenStreamComponents(
                            source, new edu.umich.lib.solr.LeftAnchoredSearchFilter(source));
                }
            }) {
                var leftTokens = analyze(leftAnalyzer, "Bill Dueber");
                assertEquals("Dueber2", leftTokens.get(1).term(),
                        "LeftAnchored last token should NOT have '00' suffix");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Analyzer reuse
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Analyzer reuse")
    class AnalyzerReuse {

        @Test
        @DisplayName("Analyzer correctly recalculates maximum position on each call")
        void analyzerReuse_recalculatesMaximumPosition() throws IOException {
            // First call: two tokens → last gets "00"
            var first = analyze(analyzer, "Alpha Beta");
            assertTerms(first, "Alpha1", "Beta200");

            // Second call: three tokens → now the THIRD is last
            var second = analyze(analyzer, "One Two Three");
            assertTerms(second, "One1", "Two2", "Three300");

            // Third call: single token
            var third = analyze(analyzer, "Solo");
            assertTerms(third, "Solo100");
        }
    }

    // -------------------------------------------------------------------------
    // Factory path
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Factory instantiation")
    class FactoryInstantiation {

        @Test
        @DisplayName("Map constructor creates a factory that produces a working filter")
        void mapConstructor_producesWorkingFilter() throws IOException {
            var factory = new FullyAnchoredSearchFilterFactory(new HashMap<>());

            Tokenizer tokenizer = new WhitespaceTokenizer();
            tokenizer.setReader(new StringReader("Hello World"));
            TokenStream ts = factory.create(tokenizer);

            var tokens = drainTokenStream(ts);
            assertTerms(tokens, "Hello1", "World200");
        }
    }

    // -------------------------------------------------------------------------
    // Parameterised: a range of realistic name strings
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] input=\"{0}\" → expected={1}")
    @MethodSource("nameExamples")
    @DisplayName("Realistic name strings produce correctly anchored tokens")
    void nameStrings_produceCorrectlyAnchoredTokens(String input, String[] expectedTerms)
            throws IOException {
        var tokens = analyze(analyzer, input);
        assertTerms(tokens, expectedTerms);
    }

    static Stream<Arguments> nameExamples() {
        return Stream.of(
                Arguments.of("Smith",            new String[]{"Smith100"}),
                Arguments.of("John Smith",       new String[]{"John1", "Smith200"}),
                Arguments.of("Mary Jane Watson", new String[]{"Mary1", "Jane2", "Watson300"}),
                Arguments.of("O'Brien",          new String[]{"O'Brien100"}),
                Arguments.of("van der Waals",    new String[]{"van1", "der2", "Waals300"})
        );
    }
}
