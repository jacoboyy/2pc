/*
 * @file   MessageBody.java
 * @author Tengda Wang <tengdaw@andrew.cmu.edu>
 * @brief
 */

import java.io.*;

public class MessageBody implements Serializable {
  /* constant variables */
  public static final String SERVER = "Server";
  public static final long TIMEOUT = 6000;

  /* */
  public int cid; // commit id
  public byte[] img; // image contents
  public String[] sources; // image sources
  public boolean isPrepare; // true for prepare stage, false for commit stage
  public boolean vote; // vote from user
  public Decision decision; // commit decision
  public long sendTime; // time the message is send
  public String dest; // destination of the message

  /* prepare message from server to users */
  public MessageBody(int cid, String dest, byte[] img, String[] sources, long sendTime) {
    this.cid = cid;
    this.dest = dest;
    this.isPrepare = true;
    this.img = img;
    this.sources = sources;
    this.sendTime = sendTime;
  }

  /* commit stage message from server to users */
  public MessageBody(int cid, String dest, String[] sources, Decision decision, long sendTime) {
    this.cid = cid;
    this.dest = dest;
    this.isPrepare = false;
    this.sources = sources;
    this.decision = decision;
    this.sendTime = sendTime;
  }

  /* vote from users to server */
  public MessageBody(int cid, boolean vote) {
    this.cid = cid;
    this.dest = SERVER;
    this.vote = vote;
    this.isPrepare = true;
  }

  /* ack from users to server */
  public MessageBody(int cid) {
    this.cid = cid;
    this.dest = SERVER;
    this.isPrepare = false;
  }

  public boolean isExpired(long curTime) {
    return curTime - this.sendTime >= TIMEOUT;
  }

  /** */
  public void updateTime(long time) {
    this.sendTime = time;
  }

  /**
   * Serialize a message and send it to destination
   */
  public void sendMessageBody(ProjectLib PL) {
    try (ByteArrayOutputStream b = new ByteArrayOutputStream();
         ObjectOutputStream o = new ObjectOutputStream(b)) {
      o.writeObject(this);
      ProjectLib.Message msg = new ProjectLib.Message(this.dest, b.toByteArray());
      PL.sendMessage(msg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Static function to deserialize a message
   */
  public static MessageBody deserialize(byte[] bytes) {
    MessageBody body = null;
    try (ByteArrayInputStream b = new ByteArrayInputStream(bytes);
         ObjectInputStream o = new ObjectInputStream(b)) {
      body = (MessageBody) o.readObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return body;
  }
}