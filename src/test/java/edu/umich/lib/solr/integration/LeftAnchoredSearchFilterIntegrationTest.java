package edu.umich.lib.solr.integration;

import edu.umich.lib.solr.LeftAnchoredSearchFilter;
import edu.umich.lib.solr.LeftAnchoredSearchFilterFactory;
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
 * Integration tests for {@link LeftAnchoredSearchFilter} and its factory.
 *
 * <p>"Integration" here means the filter is tested through a real Lucene
 * {@link WhitespaceTokenizer} rather than the hand-rolled {@code ManualTokenStream}
 * used in unit tests.  This exercises the full token-stream lifecycle (reset /
 * incrementToken / end / close) and the factory instantiation path.
 *
 * <p>These tests are written test-first and will fail to compile until Stage 2
 * of the Solr 10 migration adds the {@code NAME} field and fixes the factory
 * import.  That is intentional.
 */
@DisplayName("LeftAnchoredSearchFilter – integration")
class LeftAnchoredSearchFilterIntegrationTest {

    // -------------------------------------------------------------------------
    // Shared analyzer: WhitespaceTokenizer → LeftAnchoredSearchFilter
    // -------------------------------------------------------------------------

    private Analyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer source = new WhitespaceTokenizer();
                TokenStream result = new LeftAnchoredSearchFilter(source);
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
        @DisplayName("Single token gets position 1 appended")
        void singleToken_appendsPositionOne() throws IOException {
            var tokens = analyze(analyzer, "Bill");
            assertTerms(tokens, "Bill1");
        }

        @Test
        @DisplayName("Multiple tokens each get their 1-based position appended")
        void multipleTokens_appendSequentialPositions() throws IOException {
            var tokens = analyze(analyzer, "Bill Dueber");
            assertTerms(tokens, "Bill1", "Dueber2");
        }

        @Test
        @DisplayName("Three tokens produce positions 1, 2, 3")
        void threeTokens_appendPositionsOneTwoThree() throws IOException {
            var tokens = analyze(analyzer, "one two three");
            assertTerms(tokens, "one1", "two2", "three3");
        }
    }

    // -------------------------------------------------------------------------
    // Position-increment preservation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Position increment handling")
    class PositionIncrementHandling {

        @Test
        @DisplayName("Normal tokens each have posIncr=1")
        void normalTokens_havePositionIncrementOne() throws IOException {
            var tokens = analyze(analyzer, "Bill Dueber");
            assertPositionIncrements(tokens, 1, 1);
        }

        /**
         * The filter appends the *accumulated* position to each token.
         * Tokens at position 1 that share that slot (posIncr=0 for the second
         * synonym) both receive the suffix "1".
         *
         * This test directly constructs the filter over a ManualTokenStream so
         * we can inject posIncr=0 (synonym-style), which WhitespaceTokenizer
         * cannot produce on its own.
         */
        @Test
        @DisplayName("Synonym tokens (posIncr=0) share the same position suffix")
        void synonymTokens_shareSamePositionSuffix() throws IOException {
            // Reproduce: Bill(pos=1), John(pos=2), James(pos=2, synonym), Dueber(pos=3)
            // Expected:   Bill1, John2, James2, Dueber3
            // We drive this via ManualTokenStream (existing test infrastructure)
            // to inject the overlapping positions.
            var ms = new edu.umich.lib.solr.ManualTokenStream();
            ms.add("Bill",   1);
            ms.add("John",   2);
            ms.add("James",  2);  // synonym – same position
            ms.add("Dueber", 3);

            var ts = new LeftAnchoredSearchFilter(ms);
            var tokens = drainTokenStream(ts);

            assertAll(
                    "synonym position suffixes",
                    () -> assertTerms(tokens, "Bill1", "John2", "James2", "Dueber3"),
                    () -> assertPositionIncrements(tokens, 1, 1, 0, 1)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Analyzer reuse (ensures reset() is idempotent)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Analyzer reuse")
    class AnalyzerReuse {

        @Test
        @DisplayName("Analyzer can be called twice with different inputs")
        void analyzerCanBeReused() throws IOException {
            var first  = analyze(analyzer, "Alpha Beta");
            var second = analyze(analyzer, "Gamma");

            assertAll(
                    "reuse correctness",
                    () -> assertTerms(first,  "Alpha1", "Beta2"),
                    () -> assertTerms(second, "Gamma1")
            );
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
            var factory = new LeftAnchoredSearchFilterFactory(new HashMap<>());

            Tokenizer tokenizer = new WhitespaceTokenizer();
            tokenizer.setReader(new StringReader("Hello World"));
            TokenStream ts = factory.create(tokenizer);

            var tokens = drainTokenStream(ts);
            assertTerms(tokens, "Hello1", "World2");
        }
    }

    // -------------------------------------------------------------------------
    // Parameterised: a range of realistic name strings
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] input=\"{0}\" → expected={1}")
    @MethodSource("nameExamples")
    @DisplayName("Realistic name strings get correct position suffixes")
    void nameStrings_getCorrectPositionSuffixes(String input, String[] expectedTerms)
            throws IOException {
        var tokens = analyze(analyzer, input);
        assertTerms(tokens, expectedTerms);
    }

    static Stream<Arguments> nameExamples() {
        return Stream.of(
                Arguments.of("Smith",                new String[]{"Smith1"}),
                Arguments.of("John Smith",           new String[]{"John1", "Smith2"}),
                Arguments.of("Mary Jane Watson",     new String[]{"Mary1", "Jane2", "Watson3"}),
                Arguments.of("O'Brien",              new String[]{"O'Brien1"}),
                Arguments.of("van der Waals",        new String[]{"van1", "der2", "Waals3"})
        );
    }
}
