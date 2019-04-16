import java.io.*;
import java.util.*;

/**
 * The StartRemotePeers class begins remote peer processes.
 * It reads configuration file PeerInfo.cfg and starts remote peer processes.
 */
public class StartRemotePeers
{

    /**
     * Store id, port, address of remote peerId
     */
    class RemotePeerInfo
    {
        public String peerId;
        public String peerAddress;
        public String peerPort;

        public RemotePeerInfo(String pId, String pAddress, String pPort)
        {
            peerId = pId;
            peerAddress = pAddress;
            peerPort = pPort;
        }
    }

    public Vector<RemotePeerInfo> peerInfoVector;

    /**
     * Read PeerInfo.cfg
     */
    public void getConfiguration()
    {
        String st;
        peerInfoVector = new Vector<>();
        try
        {
            BufferedReader in = new BufferedReader(new FileReader("PeerInfo.cfg"));
            while ((st = in.readLine()) != null)
            {

                String[] tokens = st.split("\\s+");

                peerInfoVector.addElement(new RemotePeerInfo(tokens[0], tokens[1], tokens[2]));
            }

            in.close();
        } catch (Exception ex)
        {
            System.out.println(ex.toString());
        }
    }

    /**
     * Start remote peers at specified ssh username and path to src/
     * @param args username and path
     */
    public static void main(String[] args)
    {
        try
        {
            StartRemotePeers myStart = new StartRemotePeers();
            myStart.getConfiguration();

            // get current path
            String path = System.getProperty("user.dir");
            String user = "trnguyen";

            if (args.length > 0)
            {
                user = args[0];
                if (args.length > 1)
                    path = args[1];
            } else
            {
                System.out.println("No command line arguments found");
            }

            // start clients at remote hosts
            for (int i = 0; i < myStart.peerInfoVector.size(); i++)
            {
                RemotePeerInfo pInfo = myStart.peerInfoVector.elementAt(i);

                System.out.println("Start remote peer " + pInfo.peerId + " at " + pInfo.peerAddress);

                Runtime.getRuntime().exec("ssh " + user + "@" + pInfo.peerAddress + " cd " + path +
                        "&& make && java peerProcess " + pInfo.peerId);
            }
            System.out.println("Starting all remote peers has done.");

        } catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

}
