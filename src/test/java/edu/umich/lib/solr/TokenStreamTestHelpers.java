package edu.umich.lib.solr;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TokenStreamTestHelpers {

    /*
  helper method to get simpletokens for any given TokenStream
   */

  private static final String[] EMPTY_ARRAY = {};

  public static ArrayList<ManualTokenStream.SimpleToken> get_simpletokens(TokenStream ts) throws IOException {
    CharTermAttribute ta = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute pia = ts.addAttribute(PositionIncrementAttribute.class);

    ArrayList<ManualTokenStream.SimpleToken> sts = new ArrayList<>();
    int currentTokenPosition = 0;
    while (ts.incrementToken()) {
      currentTokenPosition += pia.getPositionIncrement();
      sts.add(new ManualTokenStream.SimpleToken(ta.toString(), currentTokenPosition));
    }
    return sts;
  }

  public static String[] get_terms(TokenStream ts) throws IOException {
    return get_simpletokens(ts).stream().map(s -> s.text).toArray(String[]::new);
  }

  public static List<String[]> get_nested_terms(TokenStream ts) throws IOException {
    ArrayList<ManualTokenStream.SimpleToken> tokens = get_simpletokens(ts);
    int lastPosition = tokens.get(tokens.size() - 1).position;
    ArrayList<ArrayList<String>> values = new ArrayList<ArrayList<String>>(lastPosition);
    for(int i = 0; i < lastPosition; i++) {
      values.add(i, new ArrayList<String>());
    }
    tokens.forEach(st ->{
      int array_pos = st.position - 1;
      ArrayList<String> positional_values = values.get(array_pos);
      positional_values.add(st.text);
    } );
    return values.stream().map(al -> al.toArray(EMPTY_ARRAY)).collect(Collectors.toList());
  }
}
