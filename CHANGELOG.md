1.0beta 20260515
Code quality improvements and Javadoc additions. Fix missing reset() override in
LeftAnchoredSearchFilter (Lucene contract correctness). Fix FullyAnchoredSearchFilter
to use true max position rather than last-element position. Remove dead fields, unused
imports, and snake_case naming. Convert StatePos inner class to record. Unbox Integer
to int throughout.

0.3 20260515
Migrate to Solr 10 compatibility. Bump Java compiler source/target to 21.
Update solr-core and solr-analysis-extras to 10.0.0. Update slf4j-api to
2.0.17. Update junit-jupiter to 5.12.2. Fix import of TokenStream
(org.apache.lucene.analysis.TokenStream). Update NAME field access to use
getType() per Solr 10 API. Update SPI registration file to current class names.

0.2 20200310
Simplify everything a lot, and throw out old fully-anchored
code to mirror the left-anchored stuff.