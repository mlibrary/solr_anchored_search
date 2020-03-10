package edu.umich.lib.solr;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import java.io.IOException;

public class FullyAnchoredSearchFilter extends TokenFilter {

private static final Logger LOGGER = LoggerFactory.getLogger(FullyAnchoredSearchFilter.class);

  private final CharTermAttribute myTermAttribute =
    addAttribute(CharTermAttribute.class);
  private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);

  private boolean setup_done = false;
  private Integer maximum_position;

  private List<StatePos> states = new ArrayList<>();
  private Iterator<StatePos> statesIterator;
  private Integer last_position = -1;

  protected FullyAnchoredSearchFilter(TokenStream input) {
    super(input);
  }

  private void reset_class_variables() {
    maximum_position = 0;
    setup_done = false;
    states = new ArrayList<>();
    last_position = -1;
  }
  // A little data class to hold a state and its computed position.
  class StatePos {
    public State state;
    public Integer position;

    public StatePos(State s, Integer p) {
      state = s;
      position = p;
    }
  }

  // Grab all the token states (wraps up position, term, etc.)
  // and their computed position (starting with 1) so we can 
  // grab them and mess with them later.
  private List<StatePos> get_states() throws java.io.IOException {
    List<StatePos> token_states = new ArrayList<>();
    Integer pos = 0;
    while (input.incrementToken()) {
      pos += posIncrAtt.getPositionIncrement();
      token_states.add(new StatePos(captureState(), pos));
    }
    return token_states;
  }

  // Buzz through the StatePos to figure out what the position of
  // the last token is.
  private Integer get_final_position(List<StatePos> states) {
    Iterator<StatePos> iterator = states.iterator();
    Integer maxpos = 0;
    while (iterator.hasNext()) {
      maxpos = iterator.next().position;
    }
    return maxpos;
  }

  /**
   * Takes a set of tokens and returns them with their position appended
   * to the term (so [bill, dueber] becomes [bill1, dueber200]. When used
   * with a phrase query, will only allow matches that are fully-anchored
   *
   * We deal with the positionIncrementAttribute so tokens that occupy
   * the same position ([[Bill,bill], [Dueber,dueber]) will have the
   * correct number appended.
   * @return boolean
   * @throws IOException
   */
  @Override
  public final boolean incrementToken() throws IOException {
    if (! setup_done) {
      states = get_states();
      maximum_position = get_final_position(states);
      statesIterator = states.iterator();
      setup_done = true;
    }

    if (statesIterator.hasNext()) {
      StatePos sp = statesIterator.next();
      Integer pos = sp.position;
      restoreState(sp.state);

      String t = myTermAttribute.toString();
      String newtok;
      if (pos == maximum_position) {
        newtok = t + pos.toString() + "00";
      } else {
        newtok = t + pos.toString();
      }
      myTermAttribute.setEmpty().append(newtok);
      return true;
    } else {
      reset_class_variables();
      return false;
    }
  }
}
