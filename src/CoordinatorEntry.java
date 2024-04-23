/*
 * @file   CoordinatorEntry.java
 * @author Tengda Wang <tengdaw@andrew.cmu.edu>
 *
 * Implementation of an log entry used in the Coordinator on the server side. Each entry has all
 * necessary information to identify, describe and most importantly, recover a commit/transaction
 * during node failure.
 */

import java.io.*;
import java.util.*;

public class CoordinatorEntry implements Serializable {
  public static final String DELIMITER = ":";

  public final int cid; // unique id for commit
  public final String filename; // file name on server
  public final byte[] img; // image byte array
  public final HashMap<String, ArrayList<String>>
      userToFiles; // the required resources of each user
  public HashSet<String> pendings; // users whose responses are not received by the server
  public Stage stage; // stage in 2pc (PROPOSE, COMMIT, or END)
  public boolean canCommit; // commit decision

  /** static helper function to parse the commit source files and separate them by user */
  public static HashMap<String, ArrayList<String>> parseSources(String[] sources) {
    HashMap<String, ArrayList<String>> result = new HashMap<>();
    for (int i = 0; i < sources.length; i++) {
      String[] source = sources[i].split(DELIMITER);
      String addr = source[0];
      String file = source[1];
      if (!result.containsKey(addr))
        result.put(addr, new ArrayList<>());
      result.get(addr).add(file);
    }
    return result;
  }

  /** constructor */
  public CoordinatorEntry(int cid, String filename, byte[] img, String[] sources) {
    this.cid = cid;
    this.filename = filename;
    this.img = img;
    this.userToFiles = parseSources(sources);
    this.pendings = new HashSet<>(userToFiles.keySet());
    this.stage = Stage.PROPOSE;
    this.canCommit = true;
  }

  /** mark end of PREPARE stage and transit to COMMIT stage */
  public synchronized void endPrepareStage() {
    assert (this.stage == Stage.PROPOSE);
    this.stage = Stage.COMMIT;
    this.pendings = new HashSet<>(userToFiles.keySet());
  }

  /** mark end of COMMIT stage and transit to END stage */
  public synchronized void endCommitStage() {
    assert (this.stage == Stage.COMMIT);
    this.stage = Stage.END;
  }
}