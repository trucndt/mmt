import java.util.ArrayList;
import java.util.List;

/**
 * Main class of the program
 */
public class MMT
{
    public static int NumOfPreferredNeighbors = 2;
    public static int UnchokingInterval = 5;
    public static int OptimisticUnchokingInterval = 15;
    public static String FileName = "TheFile.dat";
    public static long FileSize = 10000232;
    public static long PieceSize = 32768;

    private static final String COMMON_CFG_PATH = "Common.cfg";
    private static final String PEERINFO_CFG_PATH = "PeerInfo.cfg";

    /**
     * Start from here
     * @param args specifies peerId
     */
    public static void main(String[] args)
    {
        int peerId = 1000;
        if (args.length > 0)
        {
            peerId = Integer.parseInt(args[0]);
        }
        else
        {
            System.out.println("No command line arguments found");
        }

        readCommonCfg();
        List<PeerInfo> peerList = readPeerCfg();

        for (PeerInfo p : peerList)
        {
            p.printInfo();
        }

        Peer peer = new Peer(peerId, peerList);
        peer.start();
    }

    /**
     * read Common.cfg and update the attributes
     */
    private static void readCommonCfg()
    {

    }

    /**
     * Read PeerInfo.cfg
     * @return list of PeerInfo
     */
    private static List<PeerInfo> readPeerCfg()
    {
        List<PeerInfo> peerInfoList = new ArrayList<>();

        PeerInfo ran = new PeerInfo(1000, "127.0.0.1", 12382, 1);
        peerInfoList.add(ran);

        ran = new PeerInfo(1001, "127.0.0.1", 12383, 1);
        peerInfoList.add(ran);
        /* Read cfg file and add each peer to peerInfoList */
        // read each peer
        // create an object peerInfo for each
        // Add that peer to peerInfoList


        return peerInfoList;
    }
}
