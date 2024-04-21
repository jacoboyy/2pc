import java.io.*;

public class MessageBody implements Serializable {
  public int cid; // commit id
  public byte[] img; // image contents
  public String[] sources; // image sources
  public boolean isPrepare; // true for prepare stage, false for commit stage
  public boolean vote; // vote from user
  public Decision decision; // commit decision

  // prepare message from server to users
  public MessageBody(int cid, byte[] img, String[] sources) {
    this.cid = cid;
    this.isPrepare = true;
    this.img = img;
    this.sources = sources;
  }

  // commit stage message from server to users
  public MessageBody(int cid, String[] sources, Decision decision) {
    this.cid = cid;
    this.isPrepare = false;
    this.sources = sources;
    this.decision = decision;
  }

  // vote from users to server
  public MessageBody(int cid, boolean vote) {
    this.cid = cid;
    this.vote = vote;
    this.isPrepare = true;
  }

  // ack from users to server
  public MessageBody(int cid) {
    this.cid = cid;
    this.isPrepare = false;
  }

  public void sendMessageBody(ProjectLib PL, String dest) {
    try (ByteArrayOutputStream b = new ByteArrayOutputStream();
         ObjectOutputStream o = new ObjectOutputStream(b)) {
      o.writeObject(this);
      o.flush();
      ProjectLib.Message msg = new ProjectLib.Message(dest, b.toByteArray());
      PL.sendMessage(msg);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

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