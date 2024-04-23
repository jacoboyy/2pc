/*
 * @file   UserNode.java
 * @author Tengda Wang <tengdaw@andrew.cmu.edu>
 *
 * Implementation of a user node that participates in the two-phase commit started by the server. It
 * can approve or disapprove a commit proposal from the server, send acknowledgement messages back
 * to the server, and perform necessary operations on the related resources in response to a commit
 * (e.g. resource locking and deletion).
 */

import java.io.*;
import java.util.*;

public class UserNode implements ProjectLib.MessageHandling {
  public static final String LOG = "WAL";
  public final String myId;
  public boolean ready;
  public Slave slave;
  public static ProjectLib PL;

  public UserNode(String id) {
    this.myId = id;
    this.ready = false;
  }

  /** flush log to disk */
  public void flush() {
    try (FileOutputStream f = new FileOutputStream(LOG, false);
         ObjectOutputStream o = new ObjectOutputStream(f)) {
      o.writeObject(slave);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      PL.fsync();
    }
  }

  /** load log from disk during start-up or recovery to restore state before node failure*/
  public void recover() {
    File log = new File(LOG);
    if (!log.exists()) {
      slave = new Slave();
    } else {
      // read log from disk
      try (FileInputStream f = new FileInputStream(LOG);
           ObjectInputStream o = new ObjectInputStream(f)) {
        slave = (Slave) o.readObject();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    ready = true;
  }

  /** Handle a proposal from the server */
  public void handlePrepare(MessageBody msg) {
    // resend saved decision if already processed
    SlaveEntry prevEntry = slave.getEntry(msg.cid);
    if (prevEntry != null) {
      MessageBody replyBody = new MessageBody(msg.cid, prevEntry.vote);
      replyBody.sendMessageBody(PL);
      return;
    }

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
    // write-ahead log
    slave.addEntry(msg.cid, msg.sources, vote);
    flush();
    // send reply to server
    MessageBody replyBody = new MessageBody(msg.cid, vote);
    replyBody.sendMessageBody(PL);
  }

  /** handle a commit message from the server */
  public void handleCommit(MessageBody msg) {
    Decision decision = msg.decision;
    assert (!msg.isPrepare);
    assert (decision != Decision.UNKNOWN);

    SlaveEntry entry = slave.getEntry(msg.cid);

    if (entry == null) {
      // special case: implicit abort due to timeout
      assert (msg.decision == Decision.ABORT);
      entry = slave.addEntry(msg.cid, msg.sources, false);
      entry.updateDecision(msg.decision);
      flush();
      // send ACK back, no need to unlock resources
      MessageBody replyBody = new MessageBody(msg.cid);
      replyBody.sendMessageBody(PL);
      return;
    }

    if (entry.decision != Decision.UNKNOWN) {
      // message already answered before, resend ack
      MessageBody replyBody = new MessageBody(msg.cid);
      replyBody.sendMessageBody(PL);
      return;
    }

    // otherwise, update decision in log
    assert (entry.decision == Decision.UNKNOWN);
    entry.updateDecision(decision);
    flush();
    // unlock resources
    for (String source : msg.sources) {
      slave.unlockFile(source, msg.cid);
      // commit should delete files
      if (decision == Decision.COMMIT) {
        File file = new File(source);
        file.delete();
      }
    }
    // send ACK back
    MessageBody replyBody = new MessageBody(msg.cid);
    replyBody.sendMessageBody(PL);
  }

  /**
   * Callback to asynchronously receive messages from the server and perform operations
   * accordingly
   */
  public synchronized boolean deliverMessage(ProjectLib.Message msg) {
    while (!ready) {
      // wait for log to be loaded first
    }
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
    // alwasy attempt to load log first
    UN.recover();
  }
}
