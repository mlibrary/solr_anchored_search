package edu.umich.lib.solr.integration;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Assertion helpers for Lucene TokenStream integration tests.
 *
 * <p>Always uses the proper lifecycle: {@code reset()} before consuming,
 * {@code end()} + {@code close()} after. This mirrors what Solr does internally
 * and catches resource-leak or improper-reset bugs.
 */
public final class TokenStreamAsserter {

    /**
     * Immutable snapshot of a single token's observable attributes.
     *
     * @param term              the term text after analysis
     * @param positionIncrement the position increment relative to the previous token
     */
    public record TokenData(String term, int positionIncrement) {
        @Override
        public String toString() {
            return "TokenData{term='" + term + "', posIncr=" + positionIncrement + "}";
        }
    }

    private TokenStreamAsserter() {}

    /**
     * Drains a {@link TokenStream} into an ordered list of {@link TokenData}.
     *
     * <p>The caller is responsible for closing the stream (or the enclosing
     * {@link Analyzer}) after this call.
     *
     * @param ts an already-created (but not yet reset) token stream
     * @return the full list of tokens in emission order
     */
    public static List<TokenData> drainTokenStream(TokenStream ts) throws IOException {
        var terms = ts.addAttribute(CharTermAttribute.class);
        var posIncr = ts.addAttribute(PositionIncrementAttribute.class);

        var results = new ArrayList<TokenData>();
        ts.reset();
        try {
            while (ts.incrementToken()) {
                results.add(new TokenData(terms.toString(), posIncr.getPositionIncrement()));
            }
            ts.end();
        } finally {
            ts.close();
        }
        return results;
    }

    /**
     * Runs the given {@code text} through the {@code analyzer} on a synthetic
     * field named {@code "field"} and returns the resulting tokens.
     */
    public static List<TokenData> analyze(Analyzer analyzer, String text) throws IOException {
        // Analyzer.tokenStream() returns a reused stream; we must reset manually.
        var ts = analyzer.tokenStream("field", text);
        var terms = ts.addAttribute(CharTermAttribute.class);
        var posIncr = ts.addAttribute(PositionIncrementAttribute.class);

        var results = new ArrayList<TokenData>();
        ts.reset();
        try {
            while (ts.incrementToken()) {
                results.add(new TokenData(terms.toString(), posIncr.getPositionIncrement()));
            }
            ts.end();
        } finally {
            ts.close();
        }
        return results;
    }

    /**
     * Asserts that the token stream produces exactly the expected term strings,
     * in order.  Position increments are not checked by this overload.
     */
    public static void assertTerms(List<TokenData> actual, String... expectedTerms) {
        var actualTerms = actual.stream().map(TokenData::term).collect(Collectors.toList());
        assertEquals(
                Arrays.asList(expectedTerms),
                actualTerms,
                "Expected terms %s but got %s".formatted(
                        Arrays.toString(expectedTerms), actualTerms));
    }

    /**
     * Asserts that each token's position increment matches the expected value.
     * The lists must be the same length; use {@link #assertTerms} first to
     * confirm token count.
     */
    public static void assertPositionIncrements(List<TokenData> actual, int... expectedIncrements) {
        assertEquals(
                expectedIncrements.length,
                actual.size(),
                "Token count mismatch — check assertTerms first");
        for (int i = 0; i < expectedIncrements.length; i++) {
            int idx = i;
            assertEquals(
                    expectedIncrements[idx],
                    actual.get(idx).positionIncrement(),
                    "Position increment mismatch at token %d ('%s')".formatted(idx, actual.get(idx).term()));
        }
    }

    /**
     * Combined convenience assertion: terms AND position increments.
     */
    public static void assertTokensExact(
            List<TokenData> actual, String[] expectedTerms, int[] expectedIncrements) {
        assertAll(
                "token stream contents",
                () -> assertTerms(actual, expectedTerms),
                () -> assertPositionIncrements(actual, expectedIncrements));
    }
}
