package edu.umich.lib.solr;

import org.junit.jupiter.api.Test;

import static edu.umich.lib.solr.TokenStreamTestHelpers.get_nested_terms;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class StartsWithPhraseMatchFilterTest {


  @Test
  public void testOneToken() throws IOException {
    ManualTokenStream ts = new ManualTokenStream();
    ts.add("Bill", 1);

    StartsWithPhraseMatchFilter lasf = new StartsWithPhraseMatchFilter(ts);
    assertArrayEquals(new String[]{"Bill1"}, get_nested_terms(lasf).get(0));
  }

  @Test
  public void testNested() throws IOException {
    ManualTokenStream ts = new ManualTokenStream();
    ts.add("Bill", 1);
    ts.add("John", 2);
    ts.add("James", 2);
    ts.add("Dueber", 3);


    ArrayList<String[]> expected = new ArrayList<>();
    expected.add(new String[]{"Bill1"});
    expected.add(new String[]{"John2", "James2"});
    expected.add(new String[]{"Dueber3"});

    StartsWithPhraseMatchFilter lasf = new StartsWithPhraseMatchFilter(ts);
    List<String[]> terms = get_nested_terms(lasf);

    for (int i = 0; i < expected.size(); i++) {
      assertArrayEquals(expected.get(i), terms.get(i));
    }
  }

}
