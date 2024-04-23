/*
 * @file   MessageBody.java
 * @author Tengda Wang <tengdaw@andrew.cmu.edu>
 *
 * This file defines the communication protocol between the server and the user nodes. There are in
 * total four types of messages: proposal from the server to users, reply to proposal from a user to
 * the server, commit message from the server to users, and ack to the commit from a user to the
 * server. Different message types are created by overloading the constructor. A message will
 * contain all information necessary for the receiver to operate on it accordingly. The
 * serialization and deserialization methods in order to send/receive the message over the network
 * are also provided.
 *
 *
 */

import java.io.*;

public class MessageBody implements Serializable {
  public static final String SERVER = "Server";
  public static final long TIMEOUT = 6000;

  public int cid; // commit id
  public byte[] img; // image contents
  public String[] sources; // image sources
  public boolean isPrepare; // true for prepare stage, false for commit stage
  public boolean vote; // vote from user
  public Decision decision; // commit decision
  public long sendTime; // timestamp when the message is sent, used only on the server
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

  /** check whether a message on the server side has timed out */
  public boolean isExpired(long curTime) {
    return curTime - this.sendTime >= TIMEOUT;
  }

  /** update the message sent time, used when the server resend a expired message*/
  public void updateTime(long time) {
    this.sendTime = time;
  }

  /** serialize a message and send it to destination */
  public void sendMessageBody(ProjectLib PL) {
    try (ByteArrayOutputStream b = new ByteArrayOutputStream();
         ObjectOutputStream o = new ObjectOutputStream(b)) {
      o.writeObject(this);
      // encapsulate as a ProjectLib.Message and send using PL.sendMessage()
      ProjectLib.Message msg = new ProjectLib.Message(this.dest, b.toByteArray());
      PL.sendMessage(msg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /** static function to deserialize a message */
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