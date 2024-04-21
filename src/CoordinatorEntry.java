import java.io.*;
import java.util.*;

public class CoordinatorEntry implements Serializable {
  public final int cid;
  public final String filename;
  public final byte[] img;
  public final HashMap<String, ArrayList<String>> userToFiles;
  public HashSet<String> pendings;
  public Stage stage;
  public boolean canCommit;

  public static HashMap<String, ArrayList<String>> parseSources(String[] sources) {
    HashMap<String, ArrayList<String>> result = new HashMap<>();
    for (int i = 0; i < sources.length; i++) {
      String[] source = sources[i].split(":");
      String addr = source[0];
      String file = source[1];
      if (!result.containsKey(addr))
        result.put(addr, new ArrayList<>());
      result.get(addr).add(file);
    }
    return result;
  }

  public CoordinatorEntry(int cid, String filename, byte[] img, String[] sources) {
    this.cid = cid;
    this.filename = filename;
    this.img = img;
    this.userToFiles = parseSources(sources);
    this.pendings = new HashSet<>(userToFiles.keySet());
    this.stage = Stage.STAGE_I;
    this.canCommit = true;
  }

  public synchronized void endStageI() {
    assert(this.stage == Stage.STAGE_I);
    this.stage = Stage.STAGE_II;
    this.pendings = new HashSet<>(userToFiles.keySet());
  }

  public synchronized void endStageII() {
    assert(this.stage == Stage.STAGE_II);
    this.stage = Stage.END;
  }
}