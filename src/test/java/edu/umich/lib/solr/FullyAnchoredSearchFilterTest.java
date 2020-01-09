package edu.umich.lib.solr;


import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import org.eclipse.jetty.util.IO;
import org.junit.jupiter.api.Test;

import static edu.umich.lib.solr.TokenStreamTestHelpers.get_terms;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.*;

public class FullyAnchoredSearchFilterTest {

  @Test
  public void testJoinedStrings() {
    Map<Integer, List<String>> hm = new HashMap<>();
    String[] p1 = {"Bill", "bill"};
    String[] p2 = {"Dueber", "dueber"};
    List<String> pos1 = Arrays.asList("Bill", "bill");
    List<String> pos2 = Arrays.asList("Dueber", "dueber");
    hm.put(1, pos1);
    hm.put(2, pos2);

    ArrayList<String> expected = new ArrayList<>();
    expected.add("Bill_Dueber");
    expected.add("Bill_dueber");
    expected.add("bill_Dueber");
    expected.add("bill_dueber");

    FullyAnchoredSearchFilter asf = new FullyAnchoredSearchFilter();

    assertEquals(expected, asf.joinedTokens(hm), "Joining tokens works");

  }

  @Test
  public void testAnchors() throws IOException {
    ManualTokenStream ts = new ManualTokenStream();
    ts.add("Bill", 1);
    ts.add("Düeber", 2);
    ts.add("Dueber", 2);
    ts.add("Danit", 3);

    FullyAnchoredSearchFilter asf = new FullyAnchoredSearchFilter(ts);
    CharTermAttribute termAttribute = asf.addAttribute(CharTermAttribute.class);

    asf.incrementToken();
    assertEquals("Bill_Düeber_Danit", termAttribute.toString());

    asf.incrementToken();
    assertEquals("Bill_Dueber_Danit", termAttribute.toString());

    assertFalse(asf.incrementToken());
  }

  @Test
  public void testMultiplePermutations() throws IOException {
    ManualTokenStream ts = new ManualTokenStream();
    ts.add("Billy", 1);
    ts.add("Bill", 1);
    ts.add("Düeber", 2);
    ts.add("Dueber", 2);
    ts.add("Danit", 3);

    FullyAnchoredSearchFilter asf = new FullyAnchoredSearchFilter(ts);

    assertArrayEquals(
      new String[] {
        "Billy_Düeber_Danit",
        "Billy_Dueber_Danit",
        "Bill_Düeber_Danit",
        "Bill_Dueber_Danit",
      },
      get_terms(asf)
    );

  }
}
