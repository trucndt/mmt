import java.io.*;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

public class PeerSeed implements Runnable
{
    private final Peer thisPeer;
    private final PeerThread peerThread;

    private final RandomAccessFile file;
    private final BlockingQueue<MsgPeerSeed> toSeed;

    private final Thread thread;
    private boolean requesting = false;
    private boolean isUnchoke = false;

    /**
     * Keep a local bitfield and update whenever it has to send the HAVE message.
     * The purpose of this variable is to make sure that when checkNotInterested() is executed, it reads the exact current
     * state of bitfield
     */
    private final byte[] localBitfield;

    PeerSeed(Peer thisPeer, PeerThread peerThread, BlockingQueue<MsgPeerSeed> toSeed) throws IOException
    {
        this.thisPeer = thisPeer;
        this.peerThread = peerThread;
        this.toSeed = toSeed;
        localBitfield = thisPeer.getBitfield();

        file = new RandomAccessFile(thisPeer.FILE_PATH, "rw");
        thread = new Thread(this);
    }

    /**
     * Start this thread
     */
    public void start()
    {
        thread.start();
    }

    @Override
    public void run()
    {
        try
        {
            //send bitfield
            byte[] bitfieldMsg = makeBitfieldMsg(thisPeer.getBitfield());
            peerThread.sendMessage(new Message(Message.TYPE_BITFIELD, bitfieldMsg));

            //wait for new events
            while (true)
            {
                MsgPeerSeed msg = toSeed.take();

                switch (msg.getEventType())
                {
                    case MsgPeerSeed.TYPE_MSG:
                        Message rcvMsg = (Message)msg.getContent();
                        processReceivedMessage(rcvMsg);
                        break;

                    case MsgPeerSeed.TYPE_NEW_PIECE:
                        sendHave((int)msg.getContent());
                        if (checkNotInterested())
                            peerThread.sendMessage(new Message(Message.TYPE_NOT_INTERESTED, null));
                        break;

                    case MsgPeerSeed.TYPE_UNCHOKE:
                        peerThread.sendMessage(new Message(Message.TYPE_UNCHOKE, null));
                        break;

                    case MsgPeerSeed.TYPE_CHOKE:
                        peerThread.sendMessage(new Message(Message.TYPE_CHOKE, null));
                        break;

                    case MsgPeerSeed.TYPE_TIMEOUT:
                        if (!requesting && isUnchoke)
                            sendRequest();
                        break;

                    case MsgPeerSeed.TYPE_REQUEST:
                        sendRequest();
                        break;

                    case MsgPeerSeed.TYPE_EXIT:
                        return;
                }
            }

        } catch (IOException | InterruptedException e)
        {
            e.printStackTrace();

        } finally
        {
            try
            {
                file.close();
            } catch (IOException e1)
            {
                e1.printStackTrace();
                System.err.println("Seed: Cannot close file");
            }
        }
    }

    /**
     * Send HAVE message
     * @param idx index of piece
     */
    private void sendHave(int idx)
    {
        localBitfield[idx] = 1;
        /* send message */
        byte[] payload = Misc.intToByteArray(idx);
//        Log.println("Sending HAVE " + idx + " to " + peerThread.getTarget().getPeerId());
        peerThread.sendMessage(new Message(Message.TYPE_HAVE, payload));
    }

    /**
     * Check whether or not to send 'not interested' to the target
     * @return true if should send not interested, false otherwise
     */
    private boolean checkNotInterested()
    {
        boolean[] neighborBitfield = thisPeer.getNeighborBitfield(peerThread.getTarget().getPeerId());

        for (int i = 0; i < localBitfield.length; i++)
        {
            if (localBitfield[i] != 1 && neighborBitfield[i])
                return false;
        }

        return true;
    }

    /**
     * Process message
     * @param rcvMsg Message object of the received msg
     * @throws IOException
     */
    private void processReceivedMessage(Message rcvMsg) throws IOException
    {
        switch (rcvMsg.getType())
        {
            case Message.TYPE_REQUEST:
                if (!thisPeer.checkPreferredNeighbor(peerThread.getTarget().getPeerId())
                        && thisPeer.getOptimistUnchoke() != peerThread.getTarget().getPeerId()) return;

                int pieceIdx = Misc.byteArrayToInt(rcvMsg.getPayload());
                System.out.println("Seed: Piece requested: " + pieceIdx);
                sendPiece(pieceIdx);
                break;

            // HAVE should be handled by PeerSeed to prevent race condition of the neighbor's bitfield
            // and the (not)interested messages
            case Message.TYPE_HAVE:
                int index = Misc.byteArrayToInt(rcvMsg.getPayload());
                boolean exist = thisPeer.checkPiece(index);
                thisPeer.setNeighborBitfield(peerThread.getTarget().getPeerId(),index);
                Log.println("Peer " + thisPeer.getPeerId() + " received the 'have' message from " +
                        peerThread.getTarget().getPeerId() + " for the piece " + index);

                if (!exist)
                {
                    peerThread.sendMessage(new Message(Message.TYPE_INTERESTED, null));
                    if (!requesting && isUnchoke)
                        sendRequest();
                }

                break;

            case Message.TYPE_UNCHOKE:
                isUnchoke = true;
                Log.println("Peer " + thisPeer.getPeerId() + " is unchoked by " + peerThread.getTarget().getPeerId());
                sendRequest();
                break;

            case Message.TYPE_CHOKE:
                isUnchoke = false;
                Log.println("Peer " + thisPeer.getPeerId() + " is choked by " + peerThread.getTarget().getPeerId());
                break;

            default:
                System.out.println("PeerSeed receives invalid message");
        }
    }

    /**
     * Send PIECE msg
     * @param pieceIdx index of piece
     * @throws IOException
     */
    private void sendPiece(int pieceIdx) throws IOException
    {
        int numPiece = thisPeer.NUM_OF_PIECES;
        int offset = (pieceIdx == numPiece - 1)? (int)(peerProcess.FileSize - (numPiece - 1) * peerProcess.PieceSize) : peerProcess.PieceSize;
        long filePtr = peerProcess.PieceSize * pieceIdx;

        /* WARNING: flaws if offset is bigger than int */
        byte[] buffer = new byte[4 + offset];
        System.arraycopy(Misc.intToByteArray(pieceIdx), 0, buffer, 0, 4);

        file.seek(filePtr);
        file.readFully(buffer, 4, offset); /* WARNING: flaws if offset is bigger than int */

        // Form PIECE msg
        peerThread.sendMessage(new Message(Message.TYPE_PIECE, buffer));
    }

    /**
     * Find missing piece and send REQUEST msg
     */
    private void sendRequest()
    {
        requesting = false;
        if (thisPeer.getHasFile() || !isUnchoke)
        {
            return;
        }

        int pieceIdx = thisPeer.selectNewPieceFromNeighbor(peerThread.getTarget().getPeerId());
        if (pieceIdx < 0)
        {
            return;
        }

        // form request msg
        peerThread.sendMessage(new Message(Message.TYPE_REQUEST, Misc.intToByteArray(pieceIdx)));
//        Log.println("Request " + pieceIdx + " from neighbor " + peerThread.getTarget().getPeerId());
        requesting = true;
    }

    /**
     * Translate bitfield to payload, MSB -> index 0
     * @param bitfield bitfield
     * @return bitfield payload
     */
    private byte[] makeBitfieldMsg(byte[] bitfield)
    {
        byte[] data = new byte[(int)Math.ceil(bitfield.length*1.0/8)];
        Arrays.fill(data, (byte) 0);

        int byteIdx = 0;
        for (int i = 0; i < bitfield.length ; i++)
        {
            if (bitfield[i] == 1)
                data[byteIdx] |= 0x80 >> (i % 8);

            if ((i + 1) % 8 == 0) byteIdx++;
        }

        return data;
    }

    /**
     * exit procedure for PeerSeed
     */
    public void exit()
    {
        while (true)
        {
            try
            {
                toSeed.put(new MsgPeerSeed(MsgPeerSeed.TYPE_EXIT, null));
                break;
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        try
        {
            thread.join();
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
}
