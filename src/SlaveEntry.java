import java.io.*;

public class SlaveEntry implements Serializable {
  public final int cid;
  public final String[] sources;
  public final boolean vote;
  public Decision decision;

  public SlaveEntry(int cid, String[] sources, boolean vote) {
    this.cid = cid;
    this.sources = sources;
    this.vote = vote;
    this.decision = Decision.UNKNOWN;
  }

  public void updateDecision(Decision decision) {
    this.decision = decision;
  }
}