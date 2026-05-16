package edu.umich.lib.solr;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.TokenFilterFactory;

import java.util.Map;

/**
 * Factory for {@link LeftAnchoredSearchFilter}.
 *
 * <p>When added to a field type's analysis chain (on both index and query),
 * phrase queries against that field will only match if they are left-anchored —
 * i.e. the first query token must align with the first indexed token.
 *
 * <p><strong>Note:</strong> this filter rewrites token text, so fields that
 * include it are not suitable for generic keyword search.
 *
 * <h2>Schema example</h2>
 * <pre>{@code
 * <fieldType name="text_leftanchored" class="solr.TextField">
 *   <analyzer>
 *     <tokenizer class="solr.WhitespaceTokenizerFactory"/>
 *     <filter class="solr.ICUFoldingFilterFactory"/>
 *     <filter class="leftAnchoredSearch"/>
 *   </analyzer>
 * </fieldType>
 * }</pre>
 */
public class LeftAnchoredSearchFilterFactory extends TokenFilterFactory {

    /** SPI name used in schema.xml and for ServiceLoader registration. */
    public static final String NAME = "leftAnchoredSearch";

    /**
     * No-arg constructor required by {@link java.util.ServiceLoader}.
     * Direct instantiation without a configuration map is not supported.
     */
    public LeftAnchoredSearchFilterFactory() {
        throw new UnsupportedOperationException(
                "Use LeftAnchoredSearchFilterFactory(Map<String,String>) instead");
    }

    /** Creates a factory pre-configured with {@code args}. */
    public LeftAnchoredSearchFilterFactory(Map<String, String> args) {
        super(args);
    }

    @Override
    public LeftAnchoredSearchFilter create(TokenStream input) {
        return new LeftAnchoredSearchFilter(input);
    }
}
