import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

public class Coordinator implements Serializable {
  public int nextId;
  public ConcurrentHashMap<Integer, CoordinatorEntry> info;

  public Coordinator() {
    nextId = 0;
    info = new ConcurrentHashMap<>();
  }

  public CoordinatorEntry getEntry(int cid) {
    return info.get(cid);
  }

  public CoordinatorEntry addEntry(String filename, byte[] img, String[] sources) {
    int cid = nextId++;
    CoordinatorEntry entry = new CoordinatorEntry(cid, filename, img, sources);
    info.put(cid, entry);
    return entry;
  }
}