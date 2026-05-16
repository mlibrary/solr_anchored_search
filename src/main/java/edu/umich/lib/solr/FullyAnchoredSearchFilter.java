package edu.umich.lib.solr;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A {@link TokenFilter} that appends a 1-based position number (and a special
 * end-of-stream marker) to each token's text, enabling fully-anchored phrase
 * matching when applied to both index and query analysis.
 *
 * <h2>How it works</h2>
 * <p>All upstream tokens are buffered on the first call to {@link #incrementToken()},
 * so the maximum position is known before any output token is emitted. Each token is
 * then rewritten as:
 * <ul>
 *   <li>{@code <term><position>} for tokens before the final position, and</li>
 *   <li>{@code <term><position>00} for all tokens at the final position.</li>
 * </ul>
 * For example, {@code "bill dueber"} becomes {@code ["bill1", "dueber200"]}.
 *
 * <h2>The {@code "00"} end-marker</h2>
 * <p>The {@code "00"} suffix on the last-position tokens creates a term that cannot
 * occur at any non-final position.  Without it, a query {@code "bill dueber"} would
 * also match a document {@code "bill dueber smith"} at positions 1–2, because both
 * index and query would share {@code "bill1"} and {@code "dueber2"}.  With the marker,
 * the indexed document has {@code "dueber2"} while the query carries {@code "dueber200"},
 * so the phrase match fails unless the document also ends at position 2.
 *
 * <p>The suffix is {@code "00"} (two digits) rather than a single character to avoid
 * collisions: e.g. position 9 produces {@code "foo9"} and {@code "foo900"}, which must
 * not match {@code "foo90"} (the last token of a 90-token document).
 *
 * <h2>Synonym / multi-token positions</h2>
 * <p>Tokens that share a position (positionIncrement == 0) all receive the same numeric
 * suffix.  If they are at the final position, all receive {@code "00"} as well.
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Token text is rewritten, so the field is unsuitable for generic keyword search.</li>
 *   <li>This filter must appear in <em>both</em> the index and query analysis chains.</li>
 *   <li>The entire token stream is buffered in memory before the first output token is
 *       returned, which is acceptable for typical document fields but unsuitable for very
 *       large streaming inputs.</li>
 * </ul>
 *
 * @see FullyAnchoredSearchFilterFactory
 * @see LeftAnchoredSearchFilter
 */
public class FullyAnchoredSearchFilter extends TokenFilter {

    private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);

    private boolean setupDone = false;
    private int maximumPosition;
    private List<StatePos> states = new ArrayList<>();
    private Iterator<StatePos> statesIterator;

    /** Holds a captured token state together with its computed 1-based position. */
    private static final class StatePos {
        final State state;
        final int position;
        StatePos(State state, int position) {
            this.state = state;
            this.position = position;
        }
    }

    /** @param input the upstream token stream */
    public FullyAnchoredSearchFilter(TokenStream input) {
        super(input);
    }

    /**
     * Resets buffered state so the filter can be reused on a new input string.
     * Must be called (via the enclosing {@link org.apache.lucene.analysis.Analyzer})
     * before reuse.
     */
    @Override
    public void reset() throws IOException {
        super.reset();
        maximumPosition = 0;
        setupDone = false;
        states = new ArrayList<>();
    }

    /** Drains the upstream input, capturing each token state and its cumulative position. */
    private List<StatePos> captureStates() throws IOException {
        List<StatePos> captured = new ArrayList<>();
        int pos = 0;
        while (input.incrementToken()) {
            pos += posIncrAtt.getPositionIncrement();
            captured.add(new StatePos(captureState(), pos));
        }
        return captured;
    }

    /** Returns the maximum position across all captured states. */
    private int finalPosition(List<StatePos> statePoses) {
        return statePoses.stream().mapToInt(sp -> sp.position).max().orElse(0);
    }

    /**
     * Advances to the next token and rewrites its text as {@code <term><position>}
     * or {@code <term><position>00} for the final-position token(s).
     *
     * <p>On the first call, the entire upstream stream is buffered so the maximum
     * position can be determined before any token is emitted.
     *
     * <p>All other attributes (offsets, type, etc.) are restored from the captured
     * state and pass through unchanged.
     *
     * @return {@code true} if a token was produced; {@code false} at end of stream
     * @throws IOException if the underlying stream throws
     */
    @Override
    public final boolean incrementToken() throws IOException {
        if (!setupDone) {
            states = captureStates();
            maximumPosition = finalPosition(states);
            statesIterator = states.iterator();
            setupDone = true;
        }

        if (!statesIterator.hasNext()) {
            return false;
        }

        StatePos sp = statesIterator.next();
        restoreState(sp.state);
        String term = termAttr.toString();

        if (sp.position == maximumPosition) {
            termAttr.setEmpty().append(term).append(String.valueOf(sp.position)).append("00");
        } else {
            termAttr.setEmpty().append(term).append(String.valueOf(sp.position));
        }
        return true;
    }
}
