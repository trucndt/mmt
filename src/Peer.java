import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Peer
{
    private final List<PeerInfo> peerList;
    private final int peerId;
    private int serverPort;
    private int hasFile;

    private final byte[] bitfield; // yes=1, no=0, requested=2
    private final Set<Integer> preferredNeighbor;
    private final AtomicInteger optimistUnchoke;
    private final Map<Integer, boolean[]> neighborBitfield;

    private final LinkedList<PeerThread> peerThreads = new LinkedList<>();
    private final ReadWriteLock lockPeerThreads = new ReentrantReadWriteLock();

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
        bitfield = new byte[NUM_OF_PIECES];

        if (hasFile == 1)
        {
            for (int i = 0; i < NUM_OF_PIECES; i++)
                bitfield[i] = 1;
        }
        else
        {
            for (int i = 0; i < NUM_OF_PIECES; i++)
                bitfield[i] = 0;
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
        ServerListener serverListener = new ServerListener(serverPort, this);
        new Thread(serverListener).start();

        // Make connection to other peer
        for (PeerInfo target : peerList)
        {
            if (target.getPeerId() < peerId)
            {
                Socket connectionSocket = makeConnection(target);
                if (connectionSocket == null) continue;

                PeerThread peerThread = new PeerThread(this, target, connectionSocket, true);
                new Thread(peerThread).start();

                addPeerThread(peerThread);
            }
        }

        if (hasFile == 0)
        {
            waitUntilBitfieldFull();
        }

        waitUntilNeighborBitfieldFull();

        // close all sockets
        serverListener.exit();
        lockPeerThreads.readLock().lock();
        for (PeerThread p : peerThreads) p.exit();
        lockPeerThreads.readLock().unlock();
    }

    private void waitUntilBitfieldFull() throws InterruptedException
    {
        synchronized (bitfield)
        {
            while (true)
            {
                boolean full = true;
                for (byte b : bitfield)
                    if (b != 1)
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

    /**
     * Check if we already have a piece
     * @param idx index of piece
     * @return true if already have, false otherwise
     */
    public boolean checkPiece(int idx)
    {
        synchronized (bitfield)
        {
            return bitfield[idx] == 1;
        }
    }

    /**
     * Set a value in bitfield
     * @param idx index of piece
     * @param val value
     */
    public void setBitfield(int idx, byte val)
    {
        synchronized (bitfield)
        {
            bitfield[idx] = val;
        }

        if (val == 1)
        {
            // notify all PeerSeed
            lockPeerThreads.readLock().lock();
            for (PeerThread p : peerThreads)
            {
                p.sendSeed(MsgPeerSeed.TYPE_NEW_PIECE, idx);
            }
            lockPeerThreads.readLock().unlock();
        }

        synchronized (bitfield)
        {
            bitfield.notifyAll();
        }
    }

    /**
     * Getter for bitfield
     * @return a copy of current bitfield
     */
    public byte[] getBitfield()
    {
        synchronized (bitfield)
        {
            return Arrays.copyOf(bitfield, bitfield.length);
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
    public void setNeighborBitfield(int neighborId, boolean[] bf)
    {
        synchronized (neighborBitfield)
        {
            neighborBitfield.put(neighborId, bf);
            neighborBitfield.notifyAll();
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
            for (int i = 0; i < bitfield.length; i++)
            {
                if (bitfield[i] == 0)
                {
                    synchronized (neighborBitfield)
                    {
                        if (neighborBitfield.get(neighborId)[i])
                        {
                            bitfield[i] = 2;
                            return i;
                        }
                    }
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

    /**
     * Make TCP connection
     * @param target neighbor PeerInfo
     * @return socket if successful, null otherwise
     */
    private Socket makeConnection(PeerInfo target)
    {
        try
        {
            System.out.println("Get: Make connection to " + target.getPeerId());
            Log.println("Peer " + peerId + " makes a connection to Peer " + target.getPeerId());

            // make connection to target
            Socket socket = new Socket(target.getHostname(), target.getPort());
            System.out.println("Get: Connected to " + target.getPeerId() + " in port " + target.getPort());

            return socket;
        }
        catch (ConnectException e)
        {
            System.err.println("Get: Connection refused. You need to initiate a server first.");
        }
        catch(UnknownHostException unknownHost)
        {
            System.err.println("Get: You are trying to connect to an unknown host!");
        }
        catch(IOException ioException)
        {
            ioException.printStackTrace();
        }

        return null;
    }

    /**
     * Add a PeerThread object to list of PeerThreads
     * @param pt PeerThread object
     */
    public void addPeerThread(PeerThread pt)
    {
        lockPeerThreads.writeLock().lock();
        peerThreads.add(pt);
        lockPeerThreads.writeLock().unlock();
    }
}
