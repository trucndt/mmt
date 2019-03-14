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

    public final String FILE_PATH;
    public final int NUM_OF_PIECES;

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
        NUM_OF_PIECES = (int)Math.ceil(MMT.FileSize*1.0/MMT.PieceSize);
        bitfield = new boolean[NUM_OF_PIECES];

        if (hasFile == 1)
        {
            for (int i = 0; i < NUM_OF_PIECES; i++)
                bitfield[i] = true;
        }
        else
        {
            for (int i = 0; i < NUM_OF_PIECES; i++)
                bitfield[i] = false;
        }

        /* Initialize neighbor bitfield*/
        neighborBitfield = new HashMap<>(peerList.size() - 1);
        for (PeerInfo p : peerList)
            if (p.getPeerId() != peerId)
            {
                neighborBitfield.put(p.getPeerId(), new boolean[NUM_OF_PIECES]);
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

        // Set file path
        FILE_PATH = "peer_" + peerId + "/" + MMT.FileName;
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

        if (hasFile == 0)
        {
            waitUntilBitfieldFull();
        }

        waitUntilNeighborBitfieldFull();
    }

    private void waitUntilBitfieldFull() throws InterruptedException
    {
        synchronized (bitfield)
        {
            while (true)
            {
                boolean full = true;
                for (boolean b : bitfield)
                    if (!b)
                    {
                        full = false;
                        break;
                    }

                if (full) break;
                else bitfield.wait();
            }
        }
    }

    private void waitUntilNeighborBitfieldFull() throws InterruptedException
    {
        // TODO: optimize this code
        synchronized (neighborBitfield)
        {
            while (true)
            {
                boolean full = true;
                for (Map.Entry<Integer, boolean[]> item : neighborBitfield.entrySet())
                {
                    for (boolean b : item.getValue())
                        if (!b)
                        {
                            full = false;
                            break;
                        }
                    if (!full) break;
                }

                if (full) break;
                else neighborBitfield.wait();
            }
        }
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
            bitfield.notifyAll();
        }
    }

    public boolean[] getBitfield()
    {
        synchronized (bitfield)
        {
            return bitfield;
        }
    }


    List<PeerInfo> getPeerList()
    {
        return peerList;
    }

    /**
     * Update neighbor's bitfield
     * @param neighborId peer ID of neighbor
     * @param bf bitfield
     */
    public void updateNeighborBitfield(int neighborId, boolean[] bf)
    {
        synchronized (neighborBitfield)
        {
            neighborBitfield.put(neighborId, bf);
        }
    }

    /**
     * Randomly select a piece that neighbor has but I don't
     * @param neighborId peer ID of neighbor
     * @return The piece index or -1 if can't select
     */
    public int selectNewPieceFromNeighbor(int neighborId)
    {
        synchronized (bitfield)
        {
            synchronized (neighborBitfield)
            {
                for (int i = 0; i < bitfield.length; i++)
                {
                    if (!bitfield[i] && neighborBitfield.get(neighborId)[i])
                        return i;
                }
            }
        }

        return -1;
    }

    /**
     * Update neighbor's bitfield
     * @param neighborId Peer ID of neighbor
     * @param index Piece index
     */
    public void setNeighborBitfield(int neighborId, int index)
    {
        synchronized (neighborBitfield)
        {
            neighborBitfield.get(neighborId)[index] = true;
            neighborBitfield.notifyAll();
        }
    }

    /**
     * Print out bitfield of each neighbor
     */
    public void printNeighborBitfield()
    {
        for (int i : neighborBitfield.keySet())
        {
            System.out.println("key: " + i + " value: " + Arrays.toString(neighborBitfield.get(i)));
        }
    }
}
