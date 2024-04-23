/*
 * @file   Coordinator.java
 * @author Tengda Wang <tengdaw@andrew.cmu.edu>
 *
 * This is the implementation of a central coordinator on the server side that tracks and logs the
 * details of each transaction/commit. The logs will be flushed to disk when the log entry is
 * updated for recovery purposes.
 */

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

public class Coordinator implements Serializable {
  public int nextId; // strictly-increasing running id, uniquely identifies a commit
  public ConcurrentHashMap<Integer, CoordinatorEntry> info; // track log entry of each commit

  public Coordinator() {
    nextId = 0;
    info = new ConcurrentHashMap<>();
  }

  /** retrieve an log entry based on commit id */
  public CoordinatorEntry getEntry(int cid) {
    return info.get(cid);
  }

  /** add a new log entry and assign it a unique id */
  public CoordinatorEntry addEntry(String filename, byte[] img, String[] sources) {
    int cid = nextId++;
    CoordinatorEntry entry = new CoordinatorEntry(cid, filename, img, sources);
    info.put(cid, entry);
    return entry;
  }
}