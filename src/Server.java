/*
 * @file   Server.java
 * @author Tengda Wang <tengdaw@andrew.cmu.edu>
 *
 * A server that coordinate and perform two-phase-commits on candiate collage images. It is robust
 * to message lost and node failures, and able to process multiple commits concurrently.
 */

import java.io.*;
import java.lang.Thread;
import java.util.*;
import java.util.concurrent.*;

public class Server implements ProjectLib.MessageHandling, ProjectLib.CommitServing {
  public static final long COOLDOWN = 1500; // interval of garbage collection thread
  public static final String LOG = "server_WAL"; // log file name

  public Coordinator coordinator; // log coordinator
  public ConcurrentLinkedQueue<MessageBody>
      queue; // global queue to store all messages sent in order to track timeout
  public static ProjectLib PL; // static ProjectLib object to send/receive message
  public boolean ready; // whether the recovery phase is completed

  public Server() {
    queue = new ConcurrentLinkedQueue<>();
    ready = false;
  }

  /** Static helper function to convert ArrayList<String> to string array */
  public static String[] listToArray(ArrayList<String> list) {
    String[] result = new String[list.size()];
    result = list.toArray(result);
    return result;
  }

  /** Flush write ahead log to disk */
  public void flush() {
    try (FileOutputStream f = new FileOutputStream(LOG, false);
         ObjectOutputStream o = new ObjectOutputStream(f)) {
      o.writeObject(coordinator);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      PL.fsync();
    }
  }

  /** Load and replay logs from disk to recover system stage prioir to the node failure */
  public void recover() {
    File log = new File(LOG);
    if (!log.exists()) {
      coordinator = new Coordinator();
    } else {
      try (FileInputStream f = new FileInputStream(LOG);
           ObjectInputStream o = new ObjectInputStream(f)) {
        coordinator = (Coordinator) o.readObject();
        coordinator.info.forEach((cid, entry) -> {
          if (entry.stage == Stage.PROPOSE) {
            // abort uncommited transactions
            entry.canCommit = false;
            entry.endPrepareStage();
            flush();
            commit(entry);
          } else if (entry.stage == Stage.COMMIT) {
            // explicitly ask for ACK again
            commit(entry);
          }
        });
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    ready = true;
  }

  /** Start or resume the prepare phase of a commit */
  public void prepare(CoordinatorEntry entry) {
    // send proposal to all participants
    for (String addr : entry.pendings) {
      ArrayList<String> files = entry.userToFiles.get(addr);
      String[] filesArr = listToArray(files);
      MessageBody body =
          new MessageBody(entry.cid, addr, entry.img, filesArr, System.currentTimeMillis());
      queue.add(body);
      body.sendMessageBody(PL);
    }
  }

  /** Start or resume the second stage of a commit*/
  public void commit(CoordinatorEntry entry) {
    // send commit message to all participants
    for (String addr : entry.pendings) {
      ArrayList<String> files = entry.userToFiles.get(addr);
      String[] filesArr = listToArray(files);
      Decision decision = (entry.canCommit) ? Decision.COMMIT : Decision.ABORT;
      MessageBody body =
          new MessageBody(entry.cid, addr, filesArr, decision, System.currentTimeMillis());
      queue.add(body);
      body.sendMessageBody(PL);
    }
  }

  /**
   * Handle a vote to a commit from a user.
   * @param addr  sender of the message
   * @param msg   the vote message
   */
  public void handleVote(String addr, MessageBody msg) {
    CoordinatorEntry entry = coordinator.getEntry(msg.cid);
    if (entry.stage == Stage.END) {
      return; // transaction ended, discard vote
    } else if (entry.stage == Stage.COMMIT) {
      // resend decision for that user node
      ArrayList<String> files = entry.userToFiles.get(addr);
      String[] filesArr = listToArray(files);
      Decision decision = (entry.canCommit) ? Decision.COMMIT : Decision.ABORT;
      MessageBody body =
          new MessageBody(msg.cid, addr, filesArr, decision, System.currentTimeMillis());
      queue.add(body);
      body.sendMessageBody(PL);
      return;
    }
    boolean vote = msg.vote;
    if (!vote)
      entry.canCommit = false; // all users must vote yes in order to commit
    entry.pendings.remove(addr);
    flush();
    // all votes are received, move to the second phase
    if (entry.pendings.isEmpty()) {
      entry.endPrepareStage();
      if (entry.canCommit)
        writeFile(entry.filename, entry.img);
      flush();
      commit(entry);
    }
  }

  /**
   * Handle an ACK message from a user.
   * @param addr  sender of the message
   * @param msg   the ack message
   */
  public void handleACK(String addr, MessageBody msg) {
    CoordinatorEntry entry = coordinator.getEntry(msg.cid);
    if (entry.stage == Stage.END) {
      return; // transaction ended, discard ACK
    }
    entry.pendings.remove(addr);
    flush();
    // all acks received: mark end of a commit
    if (entry.pendings.isEmpty()) {
      entry.endCommitStage();
      flush();
    }
  }

  /**
   * Callback to asynchronously receive reply messages from users and perform operations
   * accordingly
   */
  public synchronized boolean deliverMessage(ProjectLib.Message msg) {
    while (!ready) {
      // wait for log to be loaded first
    }
    String addr = msg.addr;
    MessageBody body = MessageBody.deserialize(msg.body);
    assert (body != null);
    if (body.isPrepare)
      handleVote(addr, body); // vote for a commit
    else
      handleACK(addr, body); // ack to the commit
    return true;
  }

  /**
   * Helper function to create a image file specified by the filename and write the collage image
   */
  public void writeFile(String filename, byte[] img) {
    try {
      FileOutputStream fos = new FileOutputStream(filename);
      fos.write(img);
      fos.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Server callback when new collage is to be committed. A call to this function should start a
   * two-phase commit operation.
   */
  public void startCommit(String filename, byte[] img, String[] sources) {
    CoordinatorEntry entry = coordinator.addEntry(filename, img, sources);
    flush();
    prepare(entry);
  }

  /** Check timeouted messages and perform necessary operations on it */
  public void checkTimeout() {
    synchronized (queue) {
      ArrayList<MessageBody> newMessages = new ArrayList<>();
      for (Iterator<MessageBody> it = queue.iterator(); it.hasNext();) {
        MessageBody msg = it.next();
        if (!msg.isExpired(System.currentTimeMillis()))
          continue;
        it.remove();
        CoordinatorEntry entry = coordinator.getEntry(msg.cid);
        if (msg.isPrepare && entry.stage == Stage.PROPOSE) {
          // prepare stage timeout, treat as implicit abort
          entry.canCommit = false;
          entry.endPrepareStage();
          flush();
          commit(entry);
        } else if (!msg.isPrepare && entry.stage == Stage.COMMIT) {
          // commit stage timeout, need to resend decision
          newMessages.add(msg);
        }
      }
      // resend decision
      for (MessageBody msg : newMessages) {
        msg.updateTime(System.currentTimeMillis());
        msg.sendMessageBody(PL);
        queue.add(msg);
      }
    }
  }

  public static void main(String args[]) throws Exception {
    if (args.length != 1)
      throw new Exception("Need 1 arg: <port>");
    Server srv = new Server();
    PL = new ProjectLib(Integer.parseInt(args[0]), srv, srv);
    srv.recover();
    // periodically invoke timeout checking
    while (true) {
      Thread.sleep(COOLDOWN);
      srv.checkTimeout();
    }
  }
}
