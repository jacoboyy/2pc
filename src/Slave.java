import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

public class Slave implements Serializable {
  public ConcurrentHashMap<Integer, SlaveEntry> info;
  public ConcurrentHashMap<String, Integer> locked;

  public Slave() {
    this.info = new ConcurrentHashMap<Integer, SlaveEntry>();
    this.locked = new ConcurrentHashMap<String, Integer>();
  }

  public SlaveEntry getEntry(int cid) {
    return info.get(cid);
  }

  public SlaveEntry addEntry(int cid, String[] sources, boolean vote) {
    SlaveEntry entry = new SlaveEntry(cid, sources, vote);
    info.put(cid, entry);
    return entry;
  }

  public boolean isLocked(String source) {
    return locked.containsKey(source);
  }

  public void lockFile(String source, int cid) {
    locked.put(source, cid);
  }

  public void unlockFile(String source, int cid) {
    if (locked.containsKey(source) && locked.get(source) == cid)
      locked.remove(source);
  }
}