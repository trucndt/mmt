import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Peer
{
    private final List<PeerInfo> peerList;
    private final int peerId;
    private int serverPort;
    private AtomicBoolean hasFile = new AtomicBoolean(false);

    private final byte[] bitfield; // yes=1, no=0, requested=2

    private final Map<Integer, AtomicBoolean> preferredNeighbor;
    private final Map<Integer, AtomicBoolean> interestedNeighbor;

    private final AtomicInteger optimistUnchoke = new AtomicInteger();
    private final Map<Integer, boolean[]> neighborBitfield;

    private final LinkedList<PeerThread> peerThreads = new LinkedList<>();
    private final ReadWriteLock lock_PeerThreads = new ReentrantReadWriteLock();

    private final Map<Integer, Double> downloadRate;

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
                if (peer.getHasFile() == 1) hasFile.set(true);
                break;
            }
        }

        /* Initialize bitfield */
        NUM_OF_PIECES = (int)Math.ceil(MMT.FileSize*1.0/MMT.PieceSize);
        bitfield = new byte[NUM_OF_PIECES];

        if (hasFile.get())
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
        preferredNeighbor = new HashMap<>(peerList.size() - 1);
        interestedNeighbor = new HashMap<>(peerList.size() - 1);
        for (PeerInfo p : peerList)
        {
            if (p.getPeerId() != peerId)
            {
                preferredNeighbor.put(p.getPeerId(), new AtomicBoolean(false));
                interestedNeighbor.put(p.getPeerId(), new AtomicBoolean(false));
            }
        }

        // Initialize download rate
        downloadRate = new HashMap<>(peerList.size() - 1);
        for (PeerInfo p : peerList)
        {
            if (p.getPeerId() != peerId)
                downloadRate.put(p.getPeerId(), 0.0);
        }

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
                peerThread.start();

                addPeerThread(peerThread);
            }
        }

        // Choke thread
        ChokeThread chokeThread = new ChokeThread(this);
        chokeThread.start();

        if (!hasFile.get())
        {
            waitUntilBitfieldFull();
        }

        hasFile.set(true);

        waitUntilNeighborBitfieldFull();

        // close all sockets
        serverListener.exit();
        lock_PeerThreads.readLock().lock();
        for (PeerThread p : peerThreads) p.exit();
        lock_PeerThreads.readLock().unlock();
        chokeThread.exit();
    }

    /**
     * Wait until bitfield is full
     * @throws InterruptedException
     */
    private void waitUntilBitfieldFull() throws InterruptedException
    {
        for (int i = 0; i < bitfield.length; i++)
        {
            synchronized (bitfield)
            {
                while (bitfield[i] != 1)
                    bitfield.wait();
            }
        }
    }

    /**
     * Wait until neighbors' bitfield is full
     * @throws InterruptedException
     */
    private void waitUntilNeighborBitfieldFull() throws InterruptedException
    {
        synchronized (neighborBitfield)
        {
            for (Map.Entry<Integer, boolean[]> item : neighborBitfield.entrySet())
            {
                for (int i = 0; i < item.getValue().length; i++)
                {
                    while (!item.getValue()[i])
                        neighborBitfield.wait();
                }
            }
        }
    }

    int getPeerId()
    {
        return peerId;
    }

    boolean getHasFile()
    {
        return hasFile.get();
    }

    /**
     * Set a neighbor to be optimist unchoke
     * @param peerId
     */
    public void setOptimistUnchoke(int peerId)
    {
        System.out.println("Optimistic unchoke " + peerId);
        optimistUnchoke.set(peerId);
    }

    /**
     * Getter for OptimistUnchoke
     * @return
     */
    public int getOptimistUnchoke()
    {
        return optimistUnchoke.get();
    }

    /**
     * Set a neighbor to be interested or not interested
     * @param peerId neighbor's peer id
     * @param isInterested true if interested
     */
    public void setInterestedNeighbor(int peerId, boolean isInterested)
    {
        interestedNeighbor.get(peerId).set(isInterested);
    }

    /**
     * Check if a neighbor is interested
     * @param peerId neighbor's peer ID
     * @return true if interested
     */
    public boolean checkInterestedNeighbot(int peerId)
    {
        return interestedNeighbor.get(peerId).get();
    }

    /**
     * Get download rate of a peer
     * @param peerId peer ID
     * @return download rate
     */
    public double getDownloadRate(int peerId)
    {
        synchronized (downloadRate.get(peerId))
        {
            return downloadRate.get(peerId);
        }
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
     * Set a value in bitfield and notify all PeerSeed
     * @param idx index of piece
     * @param val value
     */
    public void setBitfield(int idx, byte val)
    {
        synchronized (bitfield)
        {
            bitfield[idx] = val;
        }

        if (val == 1) notifyNewPiece(idx);

        synchronized (bitfield)
        {
            bitfield.notifyAll();
        }
    }

    /**
     * Set a value in bitfield and notify all PeerSeed
     * @param idx index of piece
     * @param val value
     * @return a copy of bitfield
     */
    public byte[] setAndGetBitfield(int idx, byte val)
    {
        byte[] copy;
        synchronized (bitfield)
        {
            bitfield[idx] = val;
            copy = Arrays.copyOf(bitfield, bitfield.length);
        }

        if (val == 1) notifyNewPiece(idx);

        synchronized (bitfield)
        {
            bitfield.notifyAll();
        }

        return copy;
    }

    /**
     * Notify all PeerSeed of having a new piece
     * @param idx piece index
     */
    private void notifyNewPiece(int idx)
    {
        lock_PeerThreads.readLock().lock();
        for (PeerThread p : peerThreads)
        {
            p.sendSeed(MsgPeerSeed.TYPE_NEW_PIECE, idx);
        }
        lock_PeerThreads.readLock().unlock();
    }

    /**
     * Getter for bitfield
     * @return a copy of current bitfield
     */
    public byte[] getBitfield()
    {
        synchronized (bitfield)
        {
            return bitfield.clone();
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
        LinkedList<Integer> notIdx = new LinkedList<>();
        Random r = new Random();

        synchronized (bitfield)
        {
            // save all missing piece indexes
            for (int i = 0; i < bitfield.length; i++)
                if (bitfield[i] == 0) notIdx.add(i);

            ArrayList<Integer> sameIdx = new ArrayList<>(notIdx.size());
            synchronized (neighborBitfield)
            {
                for (int j : notIdx)
                    if (neighborBitfield.get(neighborId)[j]) sameIdx.add(j); //save valid indexes

                if (sameIdx.size() == 0)
                    return -1;

                int idx = sameIdx.get(r.nextInt(sameIdx.size()));
                bitfield[idx] = 2;
                return idx;
            }

        }
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
        lock_PeerThreads.writeLock().lock();
        peerThreads.add(pt);
        lock_PeerThreads.writeLock().unlock();
    }
}
