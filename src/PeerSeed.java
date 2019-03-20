import java.io.*;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeerSeed implements Runnable
{
    private final Peer thisPeer;
    private final PeerThread peerThread;

    private final RandomAccessFile file;
    private final BlockingQueue<MsgPeerSeed> toSeed;

    private final AtomicBoolean done = new AtomicBoolean(false);

    PeerSeed(Peer thisPeer, PeerThread peerThread, BlockingQueue<MsgPeerSeed> toSeed) throws IOException
    {
        this.thisPeer = thisPeer;
        this.peerThread = peerThread;
        this.toSeed = toSeed;

        file = new RandomAccessFile(thisPeer.FILE_PATH, "rw");
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
                done.set(false);

                switch (msg.getEventType())
                {
                    case MsgPeerSeed.TYPE_MSG:
                        Message rcvMsg = (Message)msg.getContent();
                        processReceivedMessage(rcvMsg);
                        break;

                    case MsgPeerSeed.TYPE_NEW_PIECE:
                        sendHave((int)msg.getContent());
                        break;
                }

                synchronized (done)
                {
                    done.set(true);
                    done.notifyAll();
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
            synchronized (done)
            {
                done.set(true);
                done.notifyAll();
            }
        }
    }



    private void sendHave(int idx) throws IOException
    {
        /* send message */
        byte[] payload = Misc.intToByteArray(idx);
        System.out.println("Sending HAVE " + idx + " to " + peerThread.target.getPeerId());
        peerThread.sendMessage(new Message(Message.TYPE_HAVE, payload));
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
                int pieceIdx = Misc.byteArrayToInt(rcvMsg.getPayload());
                System.out.println("Seed: Piece requested: " + pieceIdx);
                sendPiece(pieceIdx);
                break;

            case Message.TYPE_INTERESTED:
                //TODO: NOTE only for testing REQUEST/PIECE
                peerThread.sendMessage(new Message(Message.TYPE_UNCHOKE, null));
                break;

            case Message.TYPE_NOT_INTERESTED:
                break;
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
        int offset = (pieceIdx == numPiece - 1)? (int)(MMT.FileSize - (numPiece - 1) * MMT.PieceSize) : MMT.PieceSize;
        long filePtr = MMT.PieceSize * pieceIdx;

        /* WARNING: flaws if offset is bigger than int */
        byte[] buffer = new byte[4 + offset];
        System.arraycopy(Misc.intToByteArray(pieceIdx), 0, buffer, 0, 4);

        file.seek(filePtr);
        file.readFully(buffer, 4, offset); /* WARNING: flaws if offset is bigger than int */

        // Form PIECE msg
        peerThread.sendMessage(new Message(Message.TYPE_PIECE, buffer));
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

    public void exit()
    {
        synchronized (done)
        {
            while (!done.get())
            {
                try
                {
                    done.wait();
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}
