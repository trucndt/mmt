import java.io.*;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

public class PeerSeed implements Runnable
{
    private final Peer thisPeer;
    private final PeerThread peerThread;

    private final RandomAccessFile file;
    private final BlockingQueue<MsgPeerSeed> toSeed;

    PeerSeed(Peer thisPeer, PeerThread peerThread, BlockingQueue<MsgPeerSeed> toSeed) throws IOException
    {
        this.thisPeer = thisPeer;
        this.peerThread = peerThread;
        this.toSeed = toSeed;

        file = new RandomAccessFile(thisPeer.FILE_PATH, "r");
    }

    @Override
    public void run()
    {
        try
        {
            if (thisPeer.getHasFile() == 1)
            {
                //send bitfield
                byte[] bitfieldMsg = makeBitfieldMsg(thisPeer.getBitfield());
                peerThread.sendMessage(new Message(Message.TYPE_BITFIELD, bitfieldMsg));
            }

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
                        sendHave();
                        break;
                }
            }

        } catch (IOException e)
        {
            e.printStackTrace();
            try
            {
                file.close();
            } catch (IOException e1)
            {
                e1.printStackTrace();
                System.err.println("Seed: Cannot close socket");
            }
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }



    private void sendHave() throws IOException
    {
        /* send message */
        byte[] payload = new byte[]{0,0,0,0};
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
    private byte[] makeBitfieldMsg(boolean[] bitfield)
    {
        byte[] data = new byte[(int)Math.ceil(bitfield.length*1.0/8)];
        Arrays.fill(data, (byte) 0);

        int byteIdx = 0;
        for (int i = 0; i < bitfield.length ; i++)
        {
            if (bitfield[i])
                data[byteIdx] |= 0x80 >> (i % 8);

            if ((i + 1) % 8 == 0) byteIdx++;
        }

        return data;
    }

}
