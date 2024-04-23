/*
 * @file   SlaveEntry.java
 * @author Tengda Wang <tengdaw@andrew.cmu.edu>
 *
 * Implementation of log entry on the user side. Each entry has all
 * necessary information to identify, describe and recover a commit/transaction
 * in case of node failure.
 */

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

  /** update the decision of a commit, used when the decision is recevied from the server */
  public void updateDecision(Decision decision) {
    this.decision = decision;
  }
}