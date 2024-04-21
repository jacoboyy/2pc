/* Skeleton code for UserNode */
import java.io.*;
import java.util.*;

public class UserNode implements ProjectLib.MessageHandling {
  public final String myId;
  public final Slave slave;
  public static ProjectLib PL;

  public UserNode(String id) {
    myId = id;
    slave = new Slave();
  }

  public void handlePrepare(MessageBody msg) {
    // ask user first
    boolean vote = PL.askUser(msg.img, msg.sources);
    if (vote) {
      // check file exitance and lock status
      for (String source : msg.sources) {
        File file = new File(source);
        if (!file.exists() || slave.isLocked(source)) {
          vote = false;
          break;
        }
      }
      // processed to lock resources
      if (vote) {
        for (String source : msg.sources) slave.lockFile(source, msg.cid);
      }
    }
    System.out.println(myId + "'s vote on " + msg.cid + " is " + (vote ? "YES" : "NO"));
    // write-ahead log
    slave.addEntry(msg.cid, msg.sources, vote);
    // send reply to server
    MessageBody replyBody = new MessageBody(msg.cid, vote);
    replyBody.sendMessageBody(PL, "Server");
  }

  public void handleCommit(MessageBody msg) {
    Decision decision = msg.decision;
    assert(decision != Decision.UNKNOWN);
    // update decision in log
    SlaveEntry entry = slave.getEntry(msg.cid);
    entry.updateDecision(decision);
    // unlock resources
    for (String source : msg.sources) {
      slave.unlockFile(source, msg.cid);
      // commit should delete files
      if (decision == Decision.COMMIT) {
        File file = new File(source);
        file.delete();
        System.out.println("commit " + msg.cid + " deleted file " + source + " on " + myId);
      }
    }
    // send ACK back
    MessageBody replyBody = new MessageBody(msg.cid);
    replyBody.sendMessageBody(PL, "Server");
  }

  public synchronized boolean deliverMessage(ProjectLib.Message msg) {
    MessageBody body = MessageBody.deserialize(msg.body);
    assert (body != null);
    int cid = body.cid;
    if (body.isPrepare)
      handlePrepare(body);
    else
      handleCommit(body);
    return true;
  }

  public static void main(String args[]) throws Exception {
    if (args.length != 2)
      throw new Exception("Need 2 args: <port> <id>");
    UserNode UN = new UserNode(args[1]);
    PL = new ProjectLib(Integer.parseInt(args[0]), args[1], UN);
  }
}
