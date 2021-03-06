import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Main class of the program
 */
public class peerProcess
{
    public static int NumOfPreferredNeighbors = 2;
    public static int UnchokingInterval = 5;
    public static int OptimisticUnchokingInterval = 15;
    public static String FileName = "TheFile.dat";
    public static long FileSize = 10000232;
    public static int PieceSize = 32768;

    private static final String COMMON_CFG_PATH = "Common.cfg";
    private static final String PEERINFO_CFG_PATH = "PeerInfo.cfg";

    /**
     * Start from here
     * @param args specifies peerId
     */
    public static void main(String[] args) throws IOException, InterruptedException
    {
        int peerId = 1001;
        if (args.length > 0)
        {
            peerId = Integer.parseInt(args[0]);
        }
        else
        {
            System.out.println("No command line arguments found");
        }

        createPeerDir(peerId);
        Log.initialization(peerId);

        readCommonCfg();
        List<PeerInfo> peerList = readPeerCfg();

        Peer peer = new Peer(peerId, peerList);
        peer.start();

        Log.exit();
    }

    /**
     * read Common.cfg and update the attributes
     */
    private static void readCommonCfg() throws IOException
    {
        BufferedReader reader = new BufferedReader(new FileReader(COMMON_CFG_PATH));

        String st = reader.readLine();
        String[] tokens = st.split("\\s+");
        NumOfPreferredNeighbors = Integer.parseInt(tokens[1]);

        st = reader.readLine();
        tokens = st.split("\\s+");
        UnchokingInterval = Integer.parseInt(tokens[1]);

        st = reader.readLine();
        tokens = st.split("\\s+");
        OptimisticUnchokingInterval = Integer.parseInt(tokens[1]);

        st = reader.readLine();
        tokens = st.split("\\s+");
        FileName = tokens[1];

        st = reader.readLine();
        tokens = st.split("\\s+");
        FileSize = Integer.parseInt(tokens[1]);

        st = reader.readLine();
        tokens = st.split("\\s+");
        PieceSize = Integer.parseInt(tokens[1]);

        reader.close();
    }

    /**
     * Print out the common cfg
     */
    private static void printCommonCfg()
    {
        System.out.println("Number of Preferred Neighbors:" + NumOfPreferredNeighbors);
        System.out.println("Unchoking Interval:" + UnchokingInterval);
        System.out.println("Optimistic Unchoking Interval:" + OptimisticUnchokingInterval);
        System.out.println("File Name:" + FileName);
        System.out.println("File Size:" + FileSize);
        System.out.println("Piece Size:" + PieceSize);
    }

    /**
     * Read PeerInfo.cfg
     * @return list of PeerInfo
     */
    private static List<PeerInfo> readPeerCfg() throws IOException
    {
        int peerID;
        int listeningPort;
        InetAddress ipAddress;
        int fileState;

        List<PeerInfo> peerInfoList = new ArrayList<>();

        /* Read cfg file and add each peer to peerInfoList */
        // read each peer
        // create an object peerInfo for each
        // Add that peer to peerInfoList
        BufferedReader reader = new BufferedReader(new FileReader(PEERINFO_CFG_PATH));

        String st;
        while ((st = reader.readLine()) != null)
        {
            final String[] tokens = st.split("\\s+");
            peerID = Integer.parseInt(tokens[0]);
            ipAddress = InetAddress.getByName(tokens[1]);
            listeningPort = Integer.parseInt(tokens[2]);
            fileState = Integer.parseInt(tokens[3]);
            PeerInfo pi = new PeerInfo(peerID, ipAddress.getHostAddress(), listeningPort, fileState);
            peerInfoList.add(pi);
        }
        reader.close();

        return peerInfoList;
    }

    /**
     * Create peer subdirectory if not exist
     * @param peerId this peer ID
     * @throws IOException
     */
    private static void createPeerDir(int peerId) throws IOException
    {
        File peerDir = new File("peer_" + peerId);
        if (!peerDir.exists())
            if (!peerDir.mkdir())
                throw new IOException("Can't create peer directory");
    }
}
