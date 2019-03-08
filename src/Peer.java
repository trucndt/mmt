import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Peer
{
    private final List<PeerInfo> peerList;
    private final int peerId;
    private int serverPort;
    private int hasFile;

    private final boolean[] bitfield;
    private final Set<Integer> preferredNeighbor;
    private final AtomicInteger optimistUnchoke;
    private final Map<Integer, boolean[]> neighborBitfield;


    Peer(int peerId, List<PeerInfo> peerList)
    {
        /* Initialize peer info */
        this.peerId = peerId;
        this.peerList = peerList;

        for (PeerInfo peer : peerList)
        {
            if (peer.getPeerId() == peerId)
            {
                serverPort = peer.getPort();
                hasFile = peer.getHasFile();
                break;
            }
        }

        /* Initialize bitfield */
        int numPieces = (int)Math.ceil(MMT.FileSize*1.0/MMT.PieceSize);
        bitfield = new boolean[numPieces];

        if (hasFile == 1)
        {
            for (int i = 0; i < numPieces; i++)
                bitfield[i] = true;
        }
        else
        {
            for (int i = 0; i < numPieces; i++)
                bitfield[i] = false;
        }

        /* Initialize neighbor bitfield*/
        neighborBitfield = new HashMap<>(peerList.size() - 1);
        for (PeerInfo p : peerList)
            if (p.getPeerId() != peerId)
            {
                neighborBitfield.put(p.getPeerId(), new boolean[numPieces]);
            }

        /* Initialize preferred neighbor */
        // Assume n-1 neighbor is preferred
        preferredNeighbor = new HashSet<>(peerList.size() - 1);
        for (int i = 0; i < peerList.size() - 1; i++)
        {
            preferredNeighbor.add(peerList.get(i).getPeerId());
        }

        // The last neighbor
        optimistUnchoke = new AtomicInteger(peerList.get(peerList.size() - 1).getPeerId());
    }

    void start() throws InterruptedException, IOException
    {
        Thread serverListener = new Thread(new ServerListener(serverPort, this));
        serverListener.start();


        // Make connection to other peer
        for (PeerInfo target : peerList)
        {
            if (target.getPeerId() < peerId)
            {
                new Thread(new PeerGet(this, target)).start();
            }
        }

        // do something here
        serverListener.join();
    }

    int getPeerId()
    {
        return peerId;
    }

    int getHasFile()
    {
        return hasFile;
    }

    void setHasFile(int hasFile)
    {
        this.hasFile = hasFile;
    }

    public boolean checkPiece(int idx)
    {
        synchronized (bitfield)
        {
            return bitfield[idx];
        }
    }

    public void setHavePiece(int idx)
    {
        synchronized (bitfield)
        {
            bitfield[idx] = true;
        }
    }

    List<PeerInfo> getPeerList()
    {
        return peerList;
    }
}
