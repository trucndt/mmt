import java.io.FileNotFoundException;
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
    private final Map<Integer, boolean[]> neighborBitfield;

    private final Map<Integer, AtomicBoolean> preferredNeighbor;
    private final Map<Integer, AtomicBoolean> interestedNeighbor;
    private final AtomicInteger optimistUnchoke;

    private final LinkedList<PeerThread> peerThreads = new LinkedList<>();
    private final ReadWriteLock lock_PeerThreads = new ReentrantReadWriteLock();

    private final Map<Integer, Double> downloadRate;

    private final WriteFileThread writeFileThread;
    private final RequestTimedOutThread requestTimedOutThread;

    public final String FILE_PATH;
    public final int NUM_OF_PIECES;

    Peer(int peerId, List<PeerInfo> peerList) throws FileNotFoundException
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
        NUM_OF_PIECES = (int)Math.ceil(peerProcess.FileSize*1.0/ peerProcess.PieceSize);
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

        // Initialize optimistic unchoke
        optimistUnchoke = new AtomicInteger(-1);

        // Initialize download rate
        downloadRate = new HashMap<>(peerList.size() - 1);
        for (PeerInfo p : peerList)
        {
            if (p.getPeerId() != peerId)
                downloadRate.put(p.getPeerId(), 0.0);
        }

        // Set file path
        FILE_PATH = "peer_" + peerId + "/" + peerProcess.FileName;

        writeFileThread = new WriteFileThread(FILE_PATH);
        requestTimedOutThread = new RequestTimedOutThread(this);
    }

    void start() throws InterruptedException, IOException
    {
        ServerListener serverListener = new ServerListener(serverPort, this);
        new Thread(serverListener).start();

        writeFileThread.start(); // start WriteFileThread
        requestTimedOutThread.start(); // start RequestTimedOutThread

        // Make connection to other peer
        for (PeerInfo target : peerList)
        {
            if (target.getPeerId() == peerId) break;
            else
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
            Log.println("Peer " + this.peerId + " has downloaded the complete file");
            hasFile.set(true);
        }

        waitUntilNeighborBitfieldFull();

        // close all sockets and exit threads
        chokeThread.exit();
        serverListener.exit();
        lock_PeerThreads.writeLock().lock();
        for (PeerThread p : peerThreads) p.exit();
        peerThreads.clear();
        lock_PeerThreads.writeLock().unlock();
        requestTimedOutThread.exit();
        writeFileThread.exit();
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
        Log.println("Peer " + this.peerId + " has the optimistically unchoked neighbor " + peerId);

        // Choke the previous one if it is not preferred
        int prevId = optimistUnchoke.get();
        if (prevId != -1 && !checkPreferredNeighbor(prevId))
            notifyChokeUnchoke(prevId, MsgPeerSeed.TYPE_CHOKE);

        optimistUnchoke.set(peerId);

        // notify corresponding peerseed
        notifyChokeUnchoke(peerId, MsgPeerSeed.TYPE_UNCHOKE);
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
    public boolean checkInterestedNeighbor(int peerId)
    {
        return interestedNeighbor.get(peerId).get();
    }

    /**
     * Check if a neighbor is preferred
     * @param peerId neighbor's peer ID
     * @return true if preferred
     */
    public boolean checkPreferredNeighbor(int peerId)
    {
        return preferredNeighbor.get(peerId).get();
    }

    /**
     * Set a neighbor to be preferred or not
     * @param peerId neighbor's peer id
     * @param isPreferred true if preferred
     */
    public void setPreferredNeighbor(int peerId, boolean isPreferred)
    {
        boolean curPreferred = preferredNeighbor.get(peerId).get();
        preferredNeighbor.get(peerId).set(isPreferred);

        if (!curPreferred && peerId != getOptimistUnchoke() && isPreferred)
            notifyChokeUnchoke(peerId, MsgPeerSeed.TYPE_UNCHOKE);
        else if (curPreferred && !isPreferred && peerId != getOptimistUnchoke())
            notifyChokeUnchoke(peerId, MsgPeerSeed.TYPE_CHOKE);
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
     * Set a value in download rate of a peer
     * @param peerId peer ID
     * @param rate download rate
     */
    public void setDownloadRate(int peerId, Double rate)
    {
        synchronized (downloadRate.get(peerId))
        {
            downloadRate.put(peerId,rate);
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
     * When a requested piece is timed out
     * @param idx index of piece
     */
    public void requestTimeoutHandle(int idx)
    {
        boolean notify = false;
        synchronized (bitfield)
        {
            if (bitfield[idx] == 2) // if it is still being requested
            {
                bitfield[idx] = 0;
                notify = true;
                Log.println("Request timed out: " + idx);
            }
        }

        if (notify)
            notifyTimeout(idx);
    }

    /**
     * Procedure for receiving a new piece
     * @param idx index of piece
     * @param neighborId peer id of neighbor that sent this piece
     * @param buffer data
     * @param offset the start offset in the data
     * @param length number of bytes to write
     */
    public void handleRcvNewPiece(int idx, int neighborId, byte[] buffer, int offset, int length)
    {
        synchronized (bitfield)
        {
            if (bitfield[idx] == 1)
                return;

            bitfield[idx] = 1;
            bitfield.notifyAll();
            Log.println("Peer " + peerId + " has downloaded the piece " + idx + " from "
                    + neighborId + ". Now the number of pieces it has is " + Misc.countPieces(bitfield));
        }

        writeFileThread.writeFile(idx, buffer, offset, length);
        notifyNewPiece(idx);
    }

    /**
     * Notify all PeerSeed of timeout
     * @param idx piece index
     */
    private void notifyTimeout(int idx)
    {
        lock_PeerThreads.readLock().lock();
        for (PeerThread p : peerThreads)
        {
            p.sendSeed(MsgPeerSeed.TYPE_TIMEOUT, idx);
        }
        lock_PeerThreads.readLock().unlock();
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
     * Notify the corresponding PeerSeed for sending choke/unchoke
     * @param neighborId neighbor's peer ID
     * @param eventType CHOKE/UNCHOKE
     */
    private void notifyChokeUnchoke(int neighborId, byte eventType)
    {
        lock_PeerThreads.readLock().lock();
        for (PeerThread p : peerThreads)
        {
            if (p.getTarget().getPeerId() == neighborId)
            {
                p.sendSeed(eventType, null);
                break;
            }
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

    /**
     * Getter for neighborbitfield
     * @param neighborId neighbor id
     * @return copy of the neighbor's bitfield
     */
    public boolean[] getNeighborBitfield(int neighborId)
    {
        synchronized (neighborBitfield)
        {
            return neighborBitfield.get(neighborId).clone();
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
     * Randomly select a piece that neighbor has but I don't and report whether I should still be interested in this
     * neighbor
     * @param neighborId peer ID of neighbor
     * @return
     * <ul>
     *     <li>The piece index</li>
     *     <li>-1 if can't select</li>
     * </ul>
     */
    public int selectNewPieceFromNeighbor(int neighborId)
    {
        LinkedList<Integer> hasIdx = new LinkedList<>();
        Random r = new Random();

        synchronized (neighborBitfield)
        {
            final boolean[] bf = neighborBitfield.get(neighborId);
            for (int i = 0; i < bf.length; i++)
                if (bf[i]) hasIdx.add(i); //save pieces index of neighbor
        }

        ArrayList<Integer> sameIdx = new ArrayList<>(hasIdx.size());

        int idx = -1;
        synchronized (bitfield)
        {
            // check for valid pieces index
            for (int i : hasIdx)
                if (bitfield[i] == 0) sameIdx.add(i);

            if (sameIdx.size() == 0) return -1;

            // Select a random one
            idx = sameIdx.get(r.nextInt(sameIdx.size()));
            bitfield[idx] = 2;
        }

        requestTimedOutThread.addRequestingPiece(idx); // monitoring timeout
        return idx;
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
