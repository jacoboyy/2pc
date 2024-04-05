/* Skeleton code for UserNode */
import java.util.*;
import java.io.*;
import java.util.concurrent.locks.*;


public class UserNode implements ProjectLib.MessageHandling {
	public final String myId;
	public HashMap<String, Lock> fileLocks;
	public ProjectLib userPL;

	public UserNode( String id ) {
		myId = id;
		fileLocks = new HashMap<>();
	}

	public void assignPL(ProjectLib PL) {
		userPL = PL;
	}

	public MessageBody prepare(int cid, byte[] img, String[] sources) {
		System.out.println(myId + " prepare response for " + cid + " starts");
		boolean decision = true;
		// grab locks
		for (String source: sources) {
			if (!fileLocks.containsKey(source))
				fileLocks.put(source, new ReentrantLock());
			fileLocks.get(source).lock();
		}
		// check if file exists
		for (String source: sources) {
			File file = new File(source);
			if (!file.exists())
				decision = false;
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
		for (String source: sources) {
			File file = new File(source);
			file.delete();
			fileLocks.get(source).unlock();
		}
	}

	public void abort(int cid, String[] sources) {
		System.out.println(myId + " abort " + cid + " starts");
		// unlock resources
		for (String source: sources)
			fileLocks.get(source).unlock();
	}

	public boolean deliverMessage( ProjectLib.Message msg ) {
		try {
			MessageBody body = Serializer.deserialize(msg.body);
			int cid = body.cid;
			if (body.isPrepare) {
				MessageBody replyBody = prepare(cid, body.img, body.sources);
				ProjectLib.Message reply = new ProjectLib.Message("Server", Serializer.serialize(replyBody));
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
	
	public static void main ( String args[] ) throws Exception {
		if (args.length != 2) throw new Exception("Need 2 args: <port> <id>");
		UserNode UN = new UserNode(args[1]);
		ProjectLib PL = new ProjectLib( Integer.parseInt(args[0]), args[1], UN );
		UN.assignPL(PL);
	}
}

