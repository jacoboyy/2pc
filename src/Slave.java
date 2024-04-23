/*
 * @file   Slave.java
 * @author Tengda Wang <tengdaw@andrew.cmu.edu>
 *
 * This is the implementation of a log manager on the user side that tracks details of each
 * transaction/commit initiated by the server. The logs will be flushed to disk when the log entry
 * is updated for recovery purposes.
 */

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

public class Slave implements Serializable {
  public ConcurrentHashMap<Integer, SlaveEntry> info;
  public ConcurrentHashMap<String, Integer> locked;

  public Slave() {
    this.info = new ConcurrentHashMap<Integer, SlaveEntry>();
    this.locked = new ConcurrentHashMap<String, Integer>();
  }

  /** retrieve a previous log entry */
  public SlaveEntry getEntry(int cid) {
    return info.get(cid);
  }

  /** add a new log entry after voting for a proposal */
  public SlaveEntry addEntry(int cid, String[] sources, boolean vote) {
    SlaveEntry entry = new SlaveEntry(cid, sources, vote);
    info.put(cid, entry);
    return entry;
  }

  /** check whether a resource on the user node is already locked by another commit */
  public boolean isLocked(String source) {
    return locked.containsKey(source);
  }

  /** explicit lock a resource for a commit */
  public void lockFile(String source, int cid) {
    locked.put(source, cid);
  }

  /** explicitly unlock a resource previously locked by a commit */
  public void unlockFile(String source, int cid) {
    if (locked.containsKey(source) && locked.get(source) == cid)
      locked.remove(source);
  }
}