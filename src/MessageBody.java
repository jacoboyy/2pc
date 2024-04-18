// define message format
import java.io.*;

public class MessageBody implements Serializable {
  public int cid; // commit id
  public byte[] img; // image contents
  public String[] sources; // image sources
  public boolean isPrepare; // true for prepare stage, false for commit stage
  public boolean decision; // commit decision

  // prepare message from server to users
  public MessageBody(int cid, byte[] img, String[] sources) {
    this.cid = cid;
    this.isPrepare = true;
    this.img = img;
    this.sources = sources;
  }

  // commit/abort message from server to users
  public MessageBody(int cid, String[] sources, boolean decision) {
    this.cid = cid;
    this.isPrepare = false;
    this.sources = sources;
    this.decision = decision;
  }

  // prepare response from users to server
  public MessageBody(int cid, boolean decision) {
    this.cid = cid;
    this.decision = decision;
  }
}