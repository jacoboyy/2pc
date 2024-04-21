/* Skeleton code for Server */
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server implements ProjectLib.MessageHandling, ProjectLib.CommitServing {
  public Coordinator coordinator;
  public static ProjectLib PL;

  public Server() {
    coordinator = new Coordinator();
  }

  public static String[] listToArray(ArrayList<String> list) {
    String[] result = new String[list.size()];
    result = list.toArray(result);
    return result;
  }

  public void prepare(CoordinatorEntry entry) {
    for (String addr : entry.pendings) {
      ArrayList<String> files = entry.userToFiles.get(addr);
      System.out.println("Server sent prepare message for commit " + entry.cid + " to " + addr
          + " asking resources " + files);
      String[] filesArr = listToArray(files);
      MessageBody body = new MessageBody(entry.cid, entry.img, filesArr);
      body.sendMessageBody(PL, addr);
    }
  }

  public void commit(CoordinatorEntry entry) {
    for (String addr : entry.pendings) {
      ArrayList<String> files = entry.userToFiles.get(addr);
      String[] filesArr = listToArray(files);
      Decision decision = (entry.canCommit) ? Decision.COMMIT : Decision.ABORT;
      MessageBody body = new MessageBody(entry.cid, filesArr, decision);
      body.sendMessageBody(PL, addr);
    }
  }

  public void handleVote(String addr, MessageBody msg) {
    System.out.println("Server receive vote from " + addr + " for commit " + msg.cid);
    synchronized (coordinator) {
      CoordinatorEntry entry = coordinator.getEntry(msg.cid);
      boolean vote = msg.vote;
      if (!vote)
        entry.canCommit = false;
      entry.pendings.remove(addr);
      if (entry.pendings.isEmpty()) {
        entry.endStageI();
        if (entry.canCommit)
          writeFile(entry.filename, entry.img);
        commit(entry);
      }
    }
  }

  public void handleACK(String addr, MessageBody msg) {
    System.out.println("Server receive ack from " + addr + " for commit " + msg.cid);
    synchronized (coordinator) {
      CoordinatorEntry entry = coordinator.getEntry(msg.cid);
      entry.pendings.remove(addr);
      if (entry.pendings.isEmpty())
        entry.endStageII();
    }
  }

  public synchronized boolean deliverMessage(ProjectLib.Message msg) {
    String addr = msg.addr;
    MessageBody body = MessageBody.deserialize(msg.body);
    assert (body != null);
    int cid = body.cid;
    if (body.isPrepare)
      handleVote(addr, body);
    else
      handleACK(addr, body);
    return true;
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
    CoordinatorEntry entry = coordinator.addEntry(filename, img, sources);
    System.out.println("Server start commit " + entry.cid);
    prepare(entry);
  }

  public static void main(String args[]) throws Exception {
    if (args.length != 1)
      throw new Exception("Need 1 arg: <port>");
    Server srv = new Server();
    PL = new ProjectLib(Integer.parseInt(args[0]), srv, srv);
  }
}
