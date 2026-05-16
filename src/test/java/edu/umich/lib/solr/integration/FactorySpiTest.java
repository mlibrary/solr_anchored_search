package edu.umich.lib.solr.integration;

import edu.umich.lib.solr.FullyAnchoredSearchFilterFactory;
import edu.umich.lib.solr.LeftAnchoredSearchFilterFactory;
import org.apache.lucene.analysis.TokenFilterFactory;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that both factory classes satisfy the Lucene 9+ SPI contract required
 * for automatic discovery by {@link java.util.ServiceLoader}.
 *
 * <h2>Why these tests matter</h2>
 * <p>Lucene 9 replaced its custom SPI loader with the standard
 * {@code java.util.ServiceLoader}.  Every concrete {@link TokenFilterFactory}
 * subclass must now:
 * <ol>
 *   <li>Declare {@code public static final String NAME = "shortName";}</li>
 *   <li>Register that short name in
 *       {@code META-INF/services/org.apache.lucene.analysis.TokenFilterFactory}</li>
 *   <li>Provide a <em>public no-arg constructor</em> (typically throwing
 *       {@link UnsupportedOperationException} to prevent accidental direct use).</li>
 * </ol>
 *
 * <p>If any of these are absent, Solr will throw an exception at schema-load
 * time, not at the point the filter is actually applied — making the failure
 * hard to diagnose in production.  These tests catch such problems at
 * compile / test time.
 *
 * <p>These tests are written test-first and will fail to compile until Stage 2
 * of the Solr 10 migration is complete (adding the {@code NAME} fields).
 */
@DisplayName("Factory SPI contract")
class FactorySpiTest {

    // -------------------------------------------------------------------------
    // NAME field
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("NAME constant")
    class NameConstant {

        @Test
        @DisplayName("LeftAnchoredSearchFilterFactory declares a non-blank NAME")
        void leftFactory_hasNonBlankName() {
            assertFalse(
                    LeftAnchoredSearchFilterFactory.NAME.isBlank(),
                    "NAME must not be blank");
        }

        @Test
        @DisplayName("FullyAnchoredSearchFilterFactory declares a non-blank NAME")
        void fullyFactory_hasNonBlankName() {
            assertFalse(
                    FullyAnchoredSearchFilterFactory.NAME.isBlank(),
                    "NAME must not be blank");
        }

        @Test
        @DisplayName("NAME values are distinct across the two factories")
        void factoryNames_areDistinct() {
            assertNotEquals(
                    LeftAnchoredSearchFilterFactory.NAME,
                    FullyAnchoredSearchFilterFactory.NAME,
                    "Two different factories must have different NAME values");
        }

        @Test
        @DisplayName("NAME contains only alphanumeric characters and no spaces (Lucene convention)")
        void factoryNames_followLuceneNamingConvention() {
            assertAll(
                    "NAME convention",
                    () -> assertTrue(
                            LeftAnchoredSearchFilterFactory.NAME.matches("[A-Za-z][A-Za-z0-9]*"),
                            "LeftAnchoredSearchFilterFactory.NAME must match [A-Za-z][A-Za-z0-9]*"),
                    () -> assertTrue(
                            FullyAnchoredSearchFilterFactory.NAME.matches("[A-Za-z][A-Za-z0-9]*"),
                            "FullyAnchoredSearchFilterFactory.NAME must match [A-Za-z][A-Za-z0-9]*")
            );
        }
    }

    // -------------------------------------------------------------------------
    // No-arg constructor
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("No-arg constructor (ServiceLoader requirement)")
    class NoArgConstructor {

        @Test
        @DisplayName("LeftAnchoredSearchFilterFactory exposes a public no-arg constructor")
        void leftFactory_hasPublicNoArgConstructor() {
            assertDoesNotThrow(() -> {
                var ctor = LeftAnchoredSearchFilterFactory.class.getConstructor();
                assertNotNull(ctor);
            }, "No public no-arg constructor found");
        }

        @Test
        @DisplayName("FullyAnchoredSearchFilterFactory exposes a public no-arg constructor")
        void fullyFactory_hasPublicNoArgConstructor() {
            assertDoesNotThrow(() -> {
                var ctor = FullyAnchoredSearchFilterFactory.class.getConstructor();
                assertNotNull(ctor);
            }, "No public no-arg constructor found");
        }

        @Test
        @DisplayName("LeftAnchoredSearchFilterFactory no-arg constructor throws UnsupportedOperationException")
        void leftFactory_noArgConstructorThrowsUnsupportedOperation() throws Exception {
            var ctor = LeftAnchoredSearchFilterFactory.class.getConstructor();
            assertThrows(
                    java.lang.reflect.InvocationTargetException.class,
                    ctor::newInstance,
                    "No-arg constructor should throw UnsupportedOperationException");

            // Unwrap and verify the cause
            try {
                ctor.newInstance();
            } catch (java.lang.reflect.InvocationTargetException e) {
                assertInstanceOf(UnsupportedOperationException.class, e.getCause(),
                        "Underlying cause must be UnsupportedOperationException");
            }
        }

        @Test
        @DisplayName("FullyAnchoredSearchFilterFactory no-arg constructor throws UnsupportedOperationException")
        void fullyFactory_noArgConstructorThrowsUnsupportedOperation() throws Exception {
            var ctor = FullyAnchoredSearchFilterFactory.class.getConstructor();
            try {
                ctor.newInstance();
                fail("Expected InvocationTargetException wrapping UnsupportedOperationException");
            } catch (java.lang.reflect.InvocationTargetException e) {
                assertInstanceOf(UnsupportedOperationException.class, e.getCause(),
                        "Underlying cause must be UnsupportedOperationException");
            }
        }
    }

    // -------------------------------------------------------------------------
    // SPI discovery via TokenFilterFactory.forName()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SPI discovery via TokenFilterFactory.forName()")
    class SpiDiscovery {

        /**
         * {@link TokenFilterFactory#forName(String, Map)} is the same lookup
         * that Solr's schema loader uses when it sees a short class name.
         * If the META-INF/services file is missing or wrong, this call throws.
         */
        @Test
        @DisplayName("LeftAnchoredSearchFilterFactory can be resolved by its NAME via SPI")
        void leftFactory_canBeResolvedByName() {
            var name = LeftAnchoredSearchFilterFactory.NAME;
            assertDoesNotThrow(
                    () -> {
                        var factory = TokenFilterFactory.forName(name, new HashMap<>());
                        assertInstanceOf(
                                LeftAnchoredSearchFilterFactory.class,
                                factory,
                                "Factory resolved by name '%s' should be a LeftAnchoredSearchFilterFactory"
                                        .formatted(name));
                    },
                    "TokenFilterFactory.forName('%s') failed — check META-INF/services file".formatted(name));
        }

        @Test
        @DisplayName("FullyAnchoredSearchFilterFactory can be resolved by its NAME via SPI")
        void fullyFactory_canBeResolvedByName() {
            var name = FullyAnchoredSearchFilterFactory.NAME;
            assertDoesNotThrow(
                    () -> {
                        var factory = TokenFilterFactory.forName(name, new HashMap<>());
                        assertInstanceOf(
                                FullyAnchoredSearchFilterFactory.class,
                                factory,
                                "Factory resolved by name '%s' should be a FullyAnchoredSearchFilterFactory"
                                        .formatted(name));
                    },
                    "TokenFilterFactory.forName('%s') failed — check META-INF/services file".formatted(name));
        }

        @Test
        @DisplayName("Both factory NAMEs appear in the set of all known TokenFilterFactory names")
        void bothNames_appearsInKnownFactoryNames() {
            var knownNames = TokenFilterFactory.availableTokenFilters();
            assertAll(
                    "factory names are known to ServiceLoader",
                    () -> assertTrue(
                            knownNames.contains(LeftAnchoredSearchFilterFactory.NAME),
                            "LeftAnchoredSearchFilterFactory.NAME not found in availableTokenFilters()"),
                    () -> assertTrue(
                            knownNames.contains(FullyAnchoredSearchFilterFactory.NAME),
                            "FullyAnchoredSearchFilterFactory.NAME not found in availableTokenFilters()")
            );
        }
    }
}
