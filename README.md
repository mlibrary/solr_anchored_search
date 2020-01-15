# solr_anchored_search -- Attempt decent left-anchored and fully-anchored phrase searches in solr

`solr_anchored_search` provides two analysis chain filters that try to 
restrict phrase search matches to either being fully-anchored (what in 
the past I've called "exactish") or left-anchored.




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
exactly, in the correct order, but will ignore case and diacritics and 
take advantage of synonym expansion 

You should only do synonym expansion at query time, but I wanted
to keep this simple and not repeat everything in separate index/query
blocks.

You can use the exact same chain but with 
edu.umich.lib.solr.LeftyAnchoredSearchFilterFactory for "starts with"
phrase searches.
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

## How they work

# FullyAnchoredSearchFilterFactory

**The problem being addressed**: We want to prefer (give more relevancy) to matches
where, say, the title and the query string consist of the same tokens in the
same order, give-or-take other transformations we make like lower casing or 
whatever.

`FullyAnchoredSearchFilterFactory` munges all the text given to it into one
underscore-separated single token for each valid combination.

Most of the time this means that, e.g., _molly knows mary_ will just get
turned into the single token `molly_knows_mary`. Once you start having
multiple tokens at the same token position, things get weirder (see
below)

* William John Dueber -> william_john_dueber
* John Dueber -> john_dueber

While a "normal" phrase match on "John Dueber" would match those, the
fully-anchored search doesn't (because the single token 'john_dueber'
is not the same as the single token 'william_john_dueber'). 


# LeftAnchoredSearchFilterFactory

**The problem being addressed**: We want to prefer items where 
the query string, when used as a phrases, matches the target string
starting from the first token in each. The search "The Lord of the Rings"
should match "The Lord of the Rings: The Two Towers" before "Blah blah blah
as expressed through the movies based on The Lord of the Rings."

LeftAnchoredSearchFilterFactory can take a much easier approach and just
append the position of the token to the token itself. 

* Lord of the Rings -> lord1 of2 the3 rings4
* Reading the lord of the rings -> reading1 the2 lord3 of4 the5 rings6

A phrase search on "lord of the rings" won't find the latter because
the appended numbers don't match up.


## Explanations, Caveats and Warnings

### Don't use multi-word synonyms

This would be, e.g., mapping "william" to "billy jack".

That's good general advice with solr. Throwing a multi-word synonym in will
screw up the counts and/or throw extra tokens around, which makes your
(e)dismax _minmatch_ parameter go crazy and will break both of these 
filters _badly_.

### Beware the combinatorial explosion for FullyAnchoredSearch

Both analyzers will deal with overlapping tokens -- tokens that "occupy the 
same space." This is usually from a synonym file or a stemmer, where you 
might get

`Seeing and Believing`

tokenized as

`[[seeing, see], and, [believing, believe]]`

with multiple tokens occupying the first and third positions.

The FullyAnchoredSearch will create *all* these tokens for matching:

* seeing_and_believing
* see_and_believing
* seeing_and_believe
* see_and_believe

Add a few more words with a few more varients and this can theoretically
get out of hand. Realistically, though, an exactish match only really
makes sense on pretty-darn-small fields anyway, so it's 
probably not a problem. 

### Always put FullyAnchoredSearch at/near the end of your analysis chain

Remember: in the simple case, the output of `FullyAnchoredSerch` 
is _a single token_. Any filter you try to apply afterwards to after that is likely
to get very confused. 

So, I recommend putting FullyAnchoredSearch second-to-last, right before you...

### End your analysis chain with RemoveDuplicatesTokenFilterFactory

This is just good general advice. Use `<filter class="solr.RemoveDuplicatesTokenFilterFactory"/>` to end
any chain where you might have multiple tokens in one position. It costs
essentially nothing to run, and it prevents you from 
indexing things more than once. 
