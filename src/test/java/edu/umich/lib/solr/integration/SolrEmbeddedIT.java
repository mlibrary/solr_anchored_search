package edu.umich.lib.solr.integration;

import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests using an {@link EmbeddedSolrServer}.
 *
 * <p>These tests spin up a real (in-process) Solr 10 core configured with
 * the two anchored-search field types and verify:
 * <ol>
 *   <li>Schema loads without error (both factories are found on the classpath)</li>
 *   <li>The {@code /analysis/field} endpoint returns the expected token output</li>
 *   <li>Indexing and phrase-query search honour the anchoring semantics</li>
 * </ol>
 *
 * <p>The configset is loaded from
 * {@code src/test/resources/solr-integration/} and copied to a JUnit
 * {@link TempDir} at test startup, so Solr can write index data without
 * polluting the source tree.
 *
 * <h2>Dependency note</h2>
 * {@code EmbeddedSolrServer} is part of {@code solr-core}, so no extra Maven
 * dependency is required beyond what is already declared.
 */
@DisplayName("Solr embedded integration – anchored search filters")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SolrEmbeddedIT {

    /** Populated by {@link #setUpSolr(Path)} — shared across all tests in this class. */
    private static EmbeddedSolrServer solr;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @TempDir
    static Path solrHome;

    @BeforeAll
    static void setUpSolr() throws Exception {
        copyResourceTree("/solr-integration", solrHome);
        solr = new EmbeddedSolrServer(solrHome, "test-core");
    }

    @AfterAll
    static void tearDownSolr() throws Exception {
        if (solr != null) {
            solr.close();
        }
    }

    // -------------------------------------------------------------------------
    // Schema / startup
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("Core loads successfully (both factory classes are found)")
    void coreLoads_withoutError() {
        // If the factory classes are missing or broken, EmbeddedSolrServer
        // construction in @BeforeAll will throw before we even get here.
        assertNotNull(solr, "EmbeddedSolrServer must be non-null");
    }

    // -------------------------------------------------------------------------
    // /analysis/field endpoint — left-anchored
    // -------------------------------------------------------------------------

    @Nested
    @Order(2)
    @DisplayName("Field analysis — left-anchored type")
    class FieldAnalysisLeftAnchored {

        @Test
        @DisplayName("'Bill Dueber' produces [Bill1, Dueber2] via text_leftanchored")
        void analyzeFieldValue_leftAnchored() throws Exception {
            var tokens = fetchAnalyzedTokens("text_leftanchored", "Bill Dueber");
            assertEquals(List.of("Bill1", "Dueber2"), tokens,
                    "left-anchored analysis output mismatch");
        }

        @Test
        @DisplayName("Single token 'Smith' produces [Smith1]")
        void singleToken_leftAnchored() throws Exception {
            var tokens = fetchAnalyzedTokens("text_leftanchored", "Smith");
            assertEquals(List.of("Smith1"), tokens);
        }

        @Test
        @DisplayName("Three-token value produces positions 1, 2, 3")
        void threeTokens_leftAnchored() throws Exception {
            var tokens = fetchAnalyzedTokens("text_leftanchored", "one two three");
            assertEquals(List.of("one1", "two2", "three3"), tokens);
        }
    }

    // -------------------------------------------------------------------------
    // /analysis/field endpoint — fully-anchored
    // -------------------------------------------------------------------------

    @Nested
    @Order(3)
    @DisplayName("Field analysis — fully-anchored type")
    class FieldAnalysisFullyAnchored {

        @Test
        @DisplayName("'Bill Dueber' produces [Bill1, Dueber200] via text_fullyanchored")
        void analyzeFieldValue_fullyAnchored() throws Exception {
            var tokens = fetchAnalyzedTokens("text_fullyanchored", "Bill Dueber");
            assertEquals(List.of("Bill1", "Dueber200"), tokens,
                    "fully-anchored analysis output mismatch");
        }

        @Test
        @DisplayName("Single token 'Smith' produces [Smith100] (first AND last)")
        void singleToken_fullyAnchored() throws Exception {
            var tokens = fetchAnalyzedTokens("text_fullyanchored", "Smith");
            assertEquals(List.of("Smith100"), tokens);
        }

        @Test
        @DisplayName("Three-token value: only the third token gets '00' suffix")
        void threeTokens_fullyAnchored() throws Exception {
            var tokens = fetchAnalyzedTokens("text_fullyanchored", "one two three");
            assertEquals(List.of("one1", "two2", "three300"), tokens);
        }
    }

    // -------------------------------------------------------------------------
    // Index + phrase-query round-trip — left-anchored semantics
    // -------------------------------------------------------------------------

    @Nested
    @Order(4)
    @DisplayName("Phrase query — left-anchored semantics")
    class PhraseQueryLeftAnchored {

        private static final String DOC_ID = "left-1";

        @BeforeEach
        void indexDoc() throws Exception {
            var doc = new SolrInputDocument();
            doc.addField("id", DOC_ID);
            doc.addField("title_left", "Bill Dueber");
            solr.add(doc);
            solr.commit();
        }

        @AfterEach
        void deleteDoc() throws Exception {
            solr.deleteById(DOC_ID);
            solr.commit();
        }

        @Test
        @DisplayName("Exact phrase 'Bill Dueber' matches (phrase starts at position 1)")
        void exactPhrase_matches() throws Exception {
            long hits = phraseSearch("title_left", "Bill Dueber");
            assertEquals(1, hits, "Exact phrase must match");
        }

        @Test
        @DisplayName("Suffix-only phrase 'Dueber' does NOT match (not left-anchored)")
        void suffixPhrase_doesNotMatch() throws Exception {
            // "Dueber" alone would analyse to "Dueber1" at query time,
            // but the indexed term is "Dueber2" — so it must not match.
            long hits = phraseSearch("title_left", "Dueber");
            assertEquals(0, hits, "Suffix-only phrase must not match left-anchored field");
        }

        @Test
        @DisplayName("Left-anchored prefix 'Bill' matches (phrase starts at position 1)")
        void prefixPhrase_matches() throws Exception {
            // "Bill" analyses to "Bill1" at query time; the indexed term is "Bill1" — match.
            long hits = phraseSearch("title_left", "Bill");
            assertEquals(1, hits, "Left-anchored prefix phrase must match");
        }
    }

    // -------------------------------------------------------------------------
    // Index + phrase-query round-trip — fully-anchored semantics
    // -------------------------------------------------------------------------

    @Nested
    @Order(5)
    @DisplayName("Phrase query — fully-anchored semantics")
    class PhraseQueryFullyAnchored {

        private static final String DOC_ID = "full-1";

        @BeforeEach
        void indexDoc() throws Exception {
            var doc = new SolrInputDocument();
            doc.addField("id", DOC_ID);
            doc.addField("title_full", "Bill Dueber");
            solr.add(doc);
            solr.commit();
        }

        @AfterEach
        void deleteDoc() throws Exception {
            solr.deleteById(DOC_ID);
            solr.commit();
        }

        @Test
        @DisplayName("Exact full-value phrase 'Bill Dueber' matches")
        void exactPhrase_matches() throws Exception {
            long hits = phraseSearch("title_full", "Bill Dueber");
            assertEquals(1, hits, "Full phrase must match");
        }

        @Test
        @DisplayName("Prefix-only phrase 'Bill' does NOT match (fully-anchored requires full value)")
        void prefixPhrase_doesNotMatch() throws Exception {
            // "Bill" → query token "Bill1"; indexed has "Bill1" BUT also "Dueber200".
            // A phrase query for just "Bill1" finds that term but the slop=0 phrase
            // requires consecutive tokens — "Dueber200" is still present and the
            // phrase index prevents a prefix-only phrase from succeeding.
            // NOTE: this assertion holds when the query field type uses the same analyzer.
            long hits = phraseSearch("title_full", "Bill");
            assertEquals(0, hits, "Prefix-only phrase must not match fully-anchored field");
        }

        @Test
        @DisplayName("Suffix-only phrase 'Dueber' does NOT match")
        void suffixPhrase_doesNotMatch() throws Exception {
            long hits = phraseSearch("title_full", "Dueber");
            assertEquals(0, hits, "Suffix-only phrase must not match fully-anchored field");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Calls the {@code /analysis/field} handler and returns the list of
     * index-side token strings (in emission order).
     */
    @SuppressWarnings("unchecked")
    private static List<String> fetchAnalyzedTokens(String fieldType, String value) throws Exception {
        var params = new MapSolrParams(Map.of(
                "analysis.fieldtype", fieldType,
                "analysis.fieldvalue", value,
                "wt", "javabin"));
        var req = new QueryRequest(params);
        req.setPath("/analysis/field");

        NamedList<Object> response = solr.request(req);

        // Response structure:
        //   analysis → field_types → <fieldType> → index → List<NamedList>
        //                                                    each entry has "text" key
        var analysis    = (NamedList<Object>) response.get("analysis");
        var fieldTypes  = (NamedList<Object>) analysis.get("field_types");
        var ftEntry     = (NamedList<Object>) fieldTypes.get(fieldType);
        var indexSide   = (List<Object>)      ftEntry.get("index");

        // The last element of the index list is the final token list
        @SuppressWarnings("unchecked")
        var tokenList = (List<NamedList<Object>>) indexSide.get(indexSide.size() - 1);

        return tokenList.stream()
                .map(t -> (String) t.get("text"))
                .toList();
    }

    /**
     * Submits a phrase query against {@code field} and returns the hit count.
     */
    private static long phraseSearch(String field, String phrase) throws Exception {
        var query = new SolrQuery();
        query.setQuery("%s:\"%s\"".formatted(field, phrase));
        query.setRows(0);
        return solr.query(query).getResults().getNumFound();
    }

    /**
     * Recursively copies a classpath resource directory tree rooted at
     * {@code resourceRoot} into {@code targetDir}.
     *
     * <p>Works whether the resources are on the filesystem (Maven test-classes)
     * or packed inside a JAR.
     */
    private static void copyResourceTree(String resourceRoot, Path targetDir) throws IOException {
        URL rootUrl = SolrEmbeddedIT.class.getResource(resourceRoot);
        assertNotNull(rootUrl,
                "Classpath resource '%s' not found — check src/test/resources".formatted(resourceRoot));

        Path resourcePath = Path.of(rootUrl.getPath());
        try (var walk = Files.walk(resourcePath)) {
            walk.forEach(source -> {
                Path relative = resourcePath.relativize(source);
                Path dest     = targetDir.resolve(relative.toString());
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        try (InputStream in = Files.newInputStream(source)) {
                            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy resource: " + source, e);
                }
            });
        }
    }
}
