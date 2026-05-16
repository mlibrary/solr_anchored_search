package edu.umich.lib.solr;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;

/**
 * A {@link TokenFilter} that appends a 1-based position number to each token's text,
 * enabling left-anchored phrase matching when applied to both index and query analysis.
 *
 * <h2>How it works</h2>
 * <p>Each token is rewritten as {@code <term><position>}, where position starts at 1
 * and increments by the token's {@link org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute}.
 * For example, the input {@code "bill dueber"} becomes {@code ["bill1", "dueber2"]}.
 *
 * <p>Tokens at the same position (e.g. synonyms produced by a preceding synonym filter)
 * all receive the same position suffix, preserving their OR semantics within a phrase query.
 *
 * <h2>Left-anchoring guarantee</h2>
 * <p>Because both indexed and query tokens carry their absolute position as a suffix,
 * a phrase query can only match if the first query token aligns with the first indexed
 * token (position 1). Queries starting mid-field will never match, because the
 * suffixed terms at position N ({@code "foo2"}) will not appear at the start of any
 * indexed document.
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Token text is rewritten, so the field is unsuitable for generic keyword search.</li>
 *   <li>This filter must appear in <em>both</em> the index and query analysis chains for
 *       matching to work correctly.</li>
 * </ul>
 *
 * @see LeftAnchoredSearchFilterFactory
 * @see FullyAnchoredSearchFilter
 */
public class LeftAnchoredSearchFilter extends TokenFilter {

    private int currentPosition = 0;
    private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
    private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);

    /** @param input the upstream token stream */
    public LeftAnchoredSearchFilter(TokenStream input) {
        super(input);
    }

    /**
     * Resets the position counter to zero. Must be called (via the enclosing
     * {@link org.apache.lucene.analysis.Analyzer}) before reusing this filter
     * on a new input string.
     */
    @Override
    public void reset() throws IOException {
        super.reset();
        currentPosition = 0;
    }

    /**
     * Advances to the next token and rewrites its text as {@code <term><position>}.
     *
     * <p>All other attributes (offsets, type, etc.) pass through unchanged.
     *
     * @return {@code true} if a token was produced; {@code false} at end of stream
     * @throws IOException if the underlying stream throws
     */
    @Override
    public final boolean incrementToken() throws IOException {
        if (!input.incrementToken()) {
            return false;
        }

        currentPosition += posIncrAtt.getPositionIncrement();
        String term = termAttr.toString();
        termAttr.setEmpty().append(term).append(String.valueOf(currentPosition));
        return true;
    }
}
