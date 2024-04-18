/* Skeleton code for Server */
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server implements ProjectLib.MessageHandling, ProjectLib.CommitServing {
  public int nextId;
  public ConcurrentHashMap<Integer, CommitInfo> commitInfo;

  public static class CommitInfo {
    public byte[] img;
    public HashMap<String, ArrayList<String>> userToImages;
    public String filename;
    public int pendings;
    public boolean commit;

    public CommitInfo(
        String filename, byte[] img, HashMap<String, ArrayList<String>> userToImages) {
      this.filename = filename;
      this.img = img;
      this.userToImages = userToImages;
      pendings = 0;
      commit = true;
    }

    public synchronized void updatePending(int num) {
      pendings = num;
    }

    public synchronized void decrPending() {
      pendings -= 1;
    }

    public synchronized void markAbort() {
      commit = false;
    }

    public synchronized boolean canCommit() {
      return commit;
    }

    public synchronized boolean allReceived() {
      return pendings == 0;
    }
  }

  public ProjectLib serverPL;

  public Server() {
    nextId = 0;
    commitInfo = new ConcurrentHashMap<>();
  }

  public int getNextId() {
    return nextId++;
  }

  public void assignPL(ProjectLib PL) {
    serverPL = PL;
  }

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

  public static String[] listToArray(ArrayList<String> list) {
    String[] result = new String[list.size()];
    result = list.toArray(result);
    return result;
  }

  public void prepare(int cid, byte[] img, HashMap<String, ArrayList<String>> userToImages) {
    commitInfo.get(cid).updatePending(userToImages.size());
    for (Map.Entry<String, ArrayList<String>> entry : userToImages.entrySet()) {
      try {
        String addr = entry.getKey();
        // send
        System.out.println("Server sent prepare message for commit " + cid + " to " + addr
            + " asking resources " + entry.getValue());
        String[] filesArr = listToArray(entry.getValue());
        MessageBody body = new MessageBody(cid, img, filesArr);
        ProjectLib.Message msg = new ProjectLib.Message(addr, Serializer.serialize(body));
        serverPL.sendMessage(msg);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public boolean deliverMessage(ProjectLib.Message msg) {
    String addr = msg.addr;
    try {
      MessageBody body = Serializer.deserialize(msg.body);
      int cid = body.cid;
      System.out.println("Server receive response from " + addr + " for commit " + cid);
      boolean decision = body.decision;
      synchronized (commitInfo) {
        CommitInfo info = commitInfo.get(cid);
        if (!decision)
          info.markAbort();
        info.decrPending();
        if (info.allReceived()) {
          boolean result = info.canCommit();
          if (result)
            writeFile(info.filename, info.img);
          commit(cid, info.userToImages, result);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return true;
  }

  public void commit(int cid, HashMap<String, ArrayList<String>> userToImages, boolean canCommit) {
    for (Map.Entry<String, ArrayList<String>> entry : userToImages.entrySet()) {
      String addr = entry.getKey();
      ArrayList<String> files = entry.getValue();
      try {
        // send
        String[] filesArr = listToArray(entry.getValue());
        MessageBody body = new MessageBody(cid, filesArr, canCommit);
        ProjectLib.Message msg = new ProjectLib.Message(addr, Serializer.serialize(body));
        serverPL.sendMessage(msg);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public void writeFile(String filename, byte[] img) {
    try {
      FileOutputStream fos = new FileOutputStream(filename);
      fos.write(img);
      fos.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void startCommit(String filename, byte[] img, String[] sources) {
    int cid = getNextId();
    System.out.println("Server start commit " + cid);
    // find all local image files of each user
    HashMap<String, ArrayList<String>> userToImages = parseSources(sources);
    commitInfo.put(cid, new CommitInfo(filename, img, userToImages));
    // prepare stage
    prepare(cid, img, userToImages);
  }

  public static void main(String args[]) throws Exception {
    if (args.length != 1)
      throw new Exception("Need 1 arg: <port>");
    Server srv = new Server();
    ProjectLib PL = new ProjectLib(Integer.parseInt(args[0]), srv, srv);
    srv.assignPL(PL);
  }
}
