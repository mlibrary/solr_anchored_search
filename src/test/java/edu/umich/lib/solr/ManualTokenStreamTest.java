package edu.umich.lib.solr;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import org.junit.jupiter.api.Test;

import static edu.umich.lib.solr.TokenStreamTestHelpers.get_simpletokens;
import static edu.umich.lib.solr.TokenStreamTestHelpers.get_terms;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class ManualTokenStreamTest {

  @Test
  public void singleAdd() throws IOException {
    ManualTokenStream ts = new ManualTokenStream();
    CharTermAttribute termAttribute = ts.addAttribute(CharTermAttribute.class);
    ts.add("Bill", 1);
    ts.incrementToken();

    assertEquals("Bill", termAttribute.toString());
  }

  @Test
  public void doubleAdd() throws IOException {
    ManualTokenStream ts = new ManualTokenStream();
    ts.add("Bill", 1);
    ts.add("Dueber", 2);
    assertArrayEquals(new String[]{"Bill", "Dueber"}, get_terms(ts));
  }


  @Test
  public void addOverlapping() throws IOException {

    int advance = 1;
    int noAdvance = 0;

    ManualTokenStream ts = new ManualTokenStream();
    ts.add("Bill", 1);
    ts.add("Düeber", 2);
    ts.add("Dueber", 2);
    ts.add("Danit", 3);

    List<ManualTokenStream.SimpleToken> simpleTokens = get_simpletokens(ts);



    ts.reset();
    ts.incrementToken();
    CharTermAttribute termAttribute = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute posAttribute = ts.addAttribute(PositionIncrementAttribute.class);

    assertEquals("Bill", termAttribute.toString(), "First term correct");
    assertEquals(advance, posAttribute.getPositionIncrement(), "First position correct");

    ts.incrementToken();
    String t2a = termAttribute.toString();
    long posincr2a = posAttribute.getPositionIncrement();

    ts.incrementToken();
    String t2b = termAttribute.toString();
    long posincr2b = posAttribute.getPositionIncrement();

    assertEquals("Düeber", t2a);
    assertEquals("Dueber", t2b);
    assertEquals(advance, posincr2a);
    assertEquals(noAdvance, posincr2b);

    ts.incrementToken();
    String t3 = termAttribute.toString();
    long posincr3 = posAttribute.getPositionIncrement();

    assertEquals("Danit", t3);
    assertEquals(advance, posincr3);

  }

}
