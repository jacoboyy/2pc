/* Skeleton code for UserNode */
import java.io.*;
import java.util.*;

public class UserNode implements ProjectLib.MessageHandling {
  public final String myId;
  public HashSet<String> locked;
  public ProjectLib userPL;

  public UserNode(String id) {
    myId = id;
    locked = new HashSet<>();
  }

  public void assignPL(ProjectLib PL) {
    userPL = PL;
  }

  public MessageBody prepare(int cid, byte[] img, String[] sources) {
    System.out.println(myId + " prepare response for commit " + cid + " starts");
    boolean decision = true;
    // grab locks and check file exitance
    for (String source : sources) {
      File file = new File(source);
      if (!file.exists() || locked.contains(source))
        decision = false;
      locked.add(source);
    }
    // ask user
    if (decision)
      decision = userPL.askUser(img, sources);
    System.out.println(myId + "'s decision on " + cid + " is " + decision);
    return new MessageBody(cid, decision);
  }

  public void commit(int cid, String[] sources) {
    System.out.println(myId + " commit " + cid + " starts");
    // delete and unlock resources
    for (String source : sources) {
      File file = new File(source);
      file.delete();
      System.out.println("commit " + cid + " deleted file " + source + " on " + myId);
      locked.remove(source);
    }
  }

  public void abort(int cid, String[] sources) {
    System.out.println(myId + " abort " + cid + " starts");
    // unlock resources
    for (String source : sources) locked.remove(source);
  }

  public boolean deliverMessage(ProjectLib.Message msg) {
    try {
      MessageBody body = Serializer.deserialize(msg.body);
      int cid = body.cid;
      if (body.isPrepare) {
        MessageBody replyBody = prepare(cid, body.img, body.sources);
        ProjectLib.Message reply =
            new ProjectLib.Message("Server", Serializer.serialize(replyBody));
        userPL.sendMessage(reply);
      } else {
        boolean canCommit = body.decision;
        if (canCommit)
          commit(cid, body.sources);
        else
          abort(cid, body.sources);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return true;
  }

  public static void main(String args[]) throws Exception {
    if (args.length != 2)
      throw new Exception("Need 2 args: <port> <id>");
    UserNode UN = new UserNode(args[1]);
    ProjectLib PL = new ProjectLib(Integer.parseInt(args[0]), args[1], UN);
    UN.assignPL(PL);
  }
}
