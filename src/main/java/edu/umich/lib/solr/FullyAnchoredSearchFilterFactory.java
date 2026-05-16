package edu.umich.lib.solr;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.TokenFilterFactory;

import java.util.Map;

/**
 * Factory for {@link FullyAnchoredSearchFilter}.
 *
 * <p>When added to a field type's analysis chain (on both index and query),
 * phrase queries against that field will only match if they span the entire
 * field value — i.e. the query must be left-anchored AND right-anchored.
 *
 * <p><strong>Note:</strong> this filter rewrites token text, so fields that
 * include it are not suitable for generic keyword search.
 *
 * <h2>Schema example</h2>
 * <pre>{@code
 * <fieldType name="text_fullyanchored" class="solr.TextField">
 *   <analyzer>
 *     <tokenizer class="solr.WhitespaceTokenizerFactory"/>
 *     <filter class="solr.ICUFoldingFilterFactory"/>
 *     <filter class="fullyAnchoredSearch"/>
 *   </analyzer>
 * </fieldType>
 * }</pre>
 */
public class FullyAnchoredSearchFilterFactory extends TokenFilterFactory {

    /** SPI name used in schema.xml and for ServiceLoader registration. */
    public static final String NAME = "fullyAnchoredSearch";

    /**
     * No-arg constructor required by {@link java.util.ServiceLoader}.
     * Direct instantiation without a configuration map is not supported.
     */
    public FullyAnchoredSearchFilterFactory() {
        throw new UnsupportedOperationException(
                "Use FullyAnchoredSearchFilterFactory(Map<String,String>) instead");
    }

    /** Creates a factory pre-configured with {@code args}. */
    public FullyAnchoredSearchFilterFactory(Map<String, String> args) {
        super(args);
    }

    @Override
    public FullyAnchoredSearchFilter create(TokenStream input) {
        return new FullyAnchoredSearchFilter(input);
    }
}
