package edu.umich.lib.solr;


import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import org.eclipse.jetty.util.IO;
import org.junit.jupiter.api.Test;

import static edu.umich.lib.solr.TokenStreamTestHelpers.get_terms;
import static edu.umich.lib.solr.TokenStreamTestHelpers.get_nested_terms;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.*;

public class FullyAnchoredSearchFilterTest {

  @Test
  public void testNested() throws IOException {
        ManualTokenStream ts = new ManualTokenStream();

    ts.add("Bill", 1);
    ts.add("John", 2);
    ts.add("James", 2);
    ts.add("Dueber", 3);

    FullyAnchoredSearchFilter ff = new FullyAnchoredSearchFilter(ts);
    List<String[]> terms = get_nested_terms(ff);
    assertEquals("Bill1", terms.get(0)[0]);
    assertEquals("John2", terms.get(1)[0]);    
    assertEquals("James2", terms.get(1)[1]);
    assertEquals("Dueber300", terms.get(2)[0]);
  }

}
