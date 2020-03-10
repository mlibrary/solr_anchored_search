# solr_anchored_search -- Attempt decent left-anchored and fully-anchored phrase searches in solr

`solr_anchored_search` provides two analysis chain filters that try to 
restrict phrase search matches to either being fully-anchored (what in 
the past I've called "exactish") or left-anchored.

**It only makes sense to use these filters in fields that will be exclusively
phrase-searched!!!** 

## Usage

First, you need to put the .jar file somewhere solr is going to look for it.
Look for or add a `<lib.../>` directive in `solrconfig.xml`.

The idea is to index, say, the title a few ways: once as a regular
text field with whatever analysis chain you use for that and
once for each of these filters.

Then you can do a phrase search only (e.g., using the edismax `pf` param)
against the anchored fields, and muck with your relevancy by boosting the
anchored fields more than the unanchored field.

So: if title, title_l, and title_e are unanchored, left-anchored, and fully-anchored
(exactish) representations of the title, respectively, you might do:

`
qf=title
pf=title_e^20,title_l^10,title^5
`

A sample fully-anchored configuration for `schema.xml`

```xml

<!-- Provide an "exactish" match. Phrases must match the entire target string
exactly, in the correct order, but will ignore case and diacritics. Obviously,
other filters could be stuck in there as well.

-->

<fieldType name="text_exactish" class="solr.TextField" positionIncrementGap="1000">
  <analyzer>
    <tokenizer class="solr.ICUTokenizerFactory"/>
    <filter class="solr.ICUFoldingFilterFactory"/>
    <filter class="edu.umich.lib.solr.FullyAnchoredSearchFilterFactory"/>
    <filter class="solr.RemoveDuplicatesTokenFilterFactory"/>
  </analyzer>
</fieldType>

```

You can use the exact same chain but with 
edu.umich.lib.solr.LeftyAnchoredSearchFilterFactory for "starts with"
phrase searches.

<fieldType name="text_l" class="solr.TextField" positionIncrementGap="1000">
  <analyzer>
    <tokenizer class="solr.ICUTokenizerFactory"/>
    <filter class="solr.ICUFoldingFilterFactory"/>
    <filter class="edu.umich.lib.solr.LeftAnchoredSearchFilterFactory"/>
    <filter class="solr.RemoveDuplicatesTokenFilterFactory"/>
  </analyzer>
</fieldType>

```

## How they work

**The problem being addressed**: We want to prefer (give more relevancy) to matches
where, say, the title and the query string consist of the same tokens in the
same order, give-or-take other transformations we make like lower casing or 
whatever.

To do this, each token gets a suffix of it's position in the token string. 

For the Left anchor, that's it. 

For the fully anchored (exactish), the final token gets an extra '00' appended.

So _Lord of the Rings_ becomes:
* left-anchor: `lord1 of2 the3 rings4`
* fully-anchored: `lord1 of2 the3 rings400`

## Explanations, Caveats and Warnings

### Don't use multi-word synonyms

This would be, e.g., mapping "william" to "billy jack".

That's good general advice with solr. Throwing a multi-word synonym in will
screw up the counts and/or throw extra tokens around, which makes your
(e)dismax _minmatch_ parameter go crazy and will probably break both of these 
filters _badly_. More experimentation here is needed.

### Always put these filters at/near the end of your analysis chain

Any filter you try to apply afterwards to after either of these will be dealing
with tokens that have the extra digits attached, and they're probably not prepared
to do that.

So, I recommend putting these filters second-to-last, right before you...

### End your analysis chain with RemoveDuplicatesTokenFilterFactory

This is just good general advice. Use `<filter class="solr.RemoveDuplicatesTokenFilterFactory"/>` to end
any chain where you might have multiple tokens in one position. It costs
essentially nothing to run, and it prevents you from 
indexing overlapping, identical tokens more than once. 
