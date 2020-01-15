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
    <filter class="solr.SynonymGraphFilterFactory" synonyms="schema/nicknames.txt" 
      ignoreCase="true" expand="true"/>
    <filter class="solr.FlattenGraphFilterFactory"/>
    <filter class="edu.umich.lib.solr.FullyAnchoredSearchFilterFactory"/>
    <filter class="solr.RemoveDuplicatesTokenFilterFactory"/>
  </analyzer>
</fieldType>

```

I would think most people wouldn't do synonym expansion for something that
exists only for phrase searches; it's just there for the example.

[ You can also imagine more going on here (character
and pattern substitutions, maybe stemming or dealing with plurals, trimming
punctuation, etc.), but
this is good for an example. It all really depends on how you want your
phrase searching to work. ] 

## How they work


## Explanations, Caveats and Warnings

### Don't use multi-word synonyms

This would be, e.g., mapping "william" to "billy jack".

That's good general advice with solr. Throwing a multi-word synonym in will
screw up the counts and/or throw extra tokens around, which makes your
(e)dismax _minmatch_ parameter go crazy and will break both of these 
filters badly.

### Beware the combinatorial explosion for FullyAnchoredSearch

Using that filter only really makes sense on pretty-darn-small
fields anyway, so it's probably not a problem. 

### Always put FullyAnchoredSearch at/near the end of your analysis chain

Remember: in the simple case, the output of `FullyAnchoredSerch` 
is _a single token_. Anything you're trying to do to after that is likely
to get very confused. 

### Always end with RemoveDuplicatesTokenFilterFactory

More general advice. Use `<filter class="solr.RemoveDuplicatesTokenFilterFactory"/>` to end
any chain where you might have multiple tokens in one position. It costs
essentially nothing to run, and it prevents you from 
indexing things more than once. 

  

## Even more: multi-term awareness

Some solr analyzers/filters produce
token streams where more than one token can occupy the same location.
These copies-at-the-same-position are called KEYWORDs (not to be confused
with the KeywordTokenFactory). 

The most obvious of these is the synonym filter, which is why it's 
included above. Let's presume we're using a synonym file that associates names and 
common nicknames and we're using synonym expansion. It might look
like

```
michael,mike
william,bill,will
```

We'd get the following position/token pairs for these queries,
given that synonym file and the above filedType declaration.

* Molly know Mary
  * 1 / "molly"
  * 2 / "knows"
  * 3 / "mary"
* William knows Mary
  * 1 / "william"
  * 1 / "bill"
  * 1 / "will"
  * 2 / "knows"
  * 3 / "mary"
* Mike knows Bill
  * 1 / michael
  * 1 / mike
  * 2 / knows
  * 3 / william
  * 3 / bill
  * 3 / will

You can see that some tokens are "occupying the same space."
 
OK, so given that, how do these filters work?

# FullyAnchoredSearchFilterFactory

**The problem being addressed**: We want to prefer (give more relevancy) to matches
where, say, the title and the query string consist of the same tokens in the
same order, give-or-take other transformations we make like lower casing or 
whatever.

`FullyAnchoredSearchFilterFactory` munges all the text given to it into one
underscore-separated single token for each valid combination.

Most of the time this means that, e.g., _molly knows mary_ will just get
turned into the single token `molly_knows_mary`. Once you start having
multiple tokens at the same token position, things get weirder.

* Molly knows Mary
  * molly_knows_mary
* Molly knows Mike
  * molly_knows_michael
  * molly_knows_mike
* Mike knows Bill
  * michael_knows_william
  * michael_knows_bill
  * michael_knows_will  
  * mike_knows_william
  * mike_knows_bill
  * mike_knows_will

Did you notice the combinatorial explosion there? Because you should have
noticed the combinatorial explosion.

It's easy to see how his forces an exact match. If the user's searching for
_"molly knows"_, well, `molly_knows` is not the same token as `molly_knows_mary`.

# LeftAnchoredSearchFilterFactory

**The problem being addressed**: We want to prefer items where 
the query string, when used as a phrases, matches the target string
starting from the first token in each. The search "The Lord of the Rings"
should match "The Lord of the Rings: The Two Towers" before "Blah blah blah
as expressed through the movies based on The Lord of the Rings."

LeftAnchoredSearchFilterFactory can take a much easier approach and just
append the position of the token to the token itself. 

* Molly knows Mary
  * 1 / molly1 
  * 2 / knows2
  * 3 / mary3
* Molly knows Mike
  * 1 / molly1 
  * 2 / knows2 
  * 3 / michael3 
  * 3 / mike3
* Mike knows Bill
  * 1 / michael1
  * 1 / mike1
  * 2 / knows2
  * 3 / william3
  * 3 / bill3
  * 3 / will3
  
Now, a phrase search _"Molly knows"_ will match the first two
(both starting with the tokens `molly1` and `knows`), but 
_"knows Mary"_ won't match anything, because it will become
`knows1 mary2`

