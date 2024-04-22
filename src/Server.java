/*
 * @file   Server.java
 * @author Tengda Wang <tengdaw@andrew.cmu.edu>
 * @brief
 */

import java.io.*;
import java.lang.Thread;
import java.util.*;
import java.util.concurrent.*;

public class Server implements ProjectLib.MessageHandling, ProjectLib.CommitServing {
  public static final long COOLDOWN = 1500;
  public static final String LOG = "server_WAL";
  public Coordinator coordinator;
  public ConcurrentLinkedQueue<MessageBody> queue;
  public static ProjectLib PL;
  public boolean ready;

  public Server() {
    queue = new ConcurrentLinkedQueue<>();
    ready = false;
  }

  /**
   * Static helper function to convert ArrayList<String> to string array
   */
  public static String[] listToArray(ArrayList<String> list) {
    String[] result = new String[list.size()];
    result = list.toArray(result);
    return result;
  }

  /**
   * Flush log to disk
   */
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

  /**
   * Recover log from disk
   */
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
            entry.endStageI();
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

  public void handleVote(String addr, MessageBody msg) {
    CoordinatorEntry entry = coordinator.getEntry(msg.cid);
    assert (msg.isPrepare);
    if (entry.stage == Stage.END) {
      // transaction ended, discard vote
      return;
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
      entry.canCommit = false;
    entry.pendings.remove(addr);
    flush();
    if (entry.pendings.isEmpty()) {
      entry.endStageI();
      if (entry.canCommit)
        writeFile(entry.filename, entry.img);
      flush();
      commit(entry);
    }
  }

  public void handleACK(String addr, MessageBody msg) {
    CoordinatorEntry entry = coordinator.getEntry(msg.cid);
    if (entry.stage == Stage.END) {
      // transaction ended, discard ack
      return;
    }
    assert (entry.stage == Stage.COMMIT);
    entry.pendings.remove(addr);
    flush();
    if (entry.pendings.isEmpty()) {
      entry.endStageII();
      flush();
    }
  }

  public synchronized boolean deliverMessage(ProjectLib.Message msg) {
    while (!ready) {
    }
    String addr = msg.addr;
    MessageBody body = MessageBody.deserialize(msg.body);
    assert (body != null);
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
    flush();
    prepare(entry);
  }

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
          entry.endStageI();
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
    // periodically check for timeout
    while (true) {
      Thread.sleep(COOLDOWN);
      srv.checkTimeout();
    }
  }
}
