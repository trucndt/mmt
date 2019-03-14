import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class PeerSeed implements Runnable
{
    private final Socket socket;
    private final Peer thisPeer;

    private PeerInfo target;

    private final DataInputStream fromGet;
    private final DataOutputStream toGet;

    private final RandomAccessFile file;

    PeerSeed(Socket connectionSocket, Peer thisPeer) throws IOException
    {
        this.socket = connectionSocket;
        this.thisPeer = thisPeer;

        fromGet = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        toGet = new DataOutputStream(socket.getOutputStream());

        file = new RandomAccessFile(thisPeer.FILE_PATH, "r");
    }

    @Override
    public void run()
    {
        try
        {
            int targetId = receiveHandShake(fromGet);
            if (targetId < 0) return;

            for (PeerInfo p: thisPeer.getPeerList())
            {
                if (p.getPeerId() == targetId)
                {
                    target = p;
                    break;
                }
            }

            // Finish handshake, make a PeerGet to the target
            if (targetId > thisPeer.getPeerId())
            {
                createPeerGet();
            }

            if (thisPeer.getHasFile() == 1)
            {
                //send bitfield
                byte[] bitfieldMsg = makeBitfieldMsg(thisPeer.getBitfield());
                sendMessage(bitfieldMsg.length + 1, Misc.TYPE_BITFIELD, bitfieldMsg);

                //wait for interested
                waitForIncomingMessage();
            }

            // NOTE only for testing REQUEST/PIECE
//            sendMessage(1, Misc.TYPE_UNCHOKE, null);
            while (true)
            {
                waitForIncomingMessage();
            }

            //TODO wait for having new pieces

            //NOTE only for testing the HAVE message
//            sendHave();
        } catch (IOException e)
        {
            e.printStackTrace();
            try
            {
                socket.close();
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

    private int receiveHandShake(DataInputStream fromGet) throws IOException
    {
        byte[] buffer = new byte[Misc.LENGTH_HANDSHAKE];
        fromGet.readFully(buffer);

        String rcvMsg = new String(buffer);
        System.out.println("Seed: Receive msg " + rcvMsg);

        /* check handshake message */
        if (!rcvMsg.substring(0, 18).equals("P2PFILESHARINGPROJ"))
        {
            System.out.println("Seed: Wrong handshake");
            return -1;
        }

        return Integer.parseInt(rcvMsg.substring(28, 32));

    }

    /**
     * send message to socket
     * @param length message length
     * @param type message type
     * @param payload payload
     */
    private void sendMessage(int length, byte type, byte[] payload) throws IOException
    {
        System.out.println("Seed: Sending message of type " + type + " with length " + length + " to " + target.getPeerId());
        toGet.writeInt(length);
        toGet.writeByte(type);
        toGet.write(payload, 0, length  - 1);
        toGet.flush();
    }

    /**
     * Create a PeerGet thread and wait until it finished handshake
     * @throws IOException
     * @throws InterruptedException
     */
    private void createPeerGet() throws IOException, InterruptedException
    {
        PeerGet peerGet = new PeerGet(thisPeer, target);
        new Thread(peerGet).start();

        // wait until peerGet finish handshake
        synchronized (peerGet.finishHandshake)
        {
            while (!peerGet.finishHandshake.get())
            {
                peerGet.finishHandshake.wait();
            }
        }
    }

    private void sendHave() throws IOException
    {
        /* send message */
        byte[] payload = new byte[]{0,0,0,0};
        sendMessage(Misc.LENGTH_HAVE, Misc.TYPE_HAVE, payload);
    }

    /**
     * Wait on socket until a new message arrives
     * @throws IOException
     */
    private void waitForIncomingMessage() throws IOException
    {
        byte[] msgLenType = new byte[Misc.MESSAGE_LENGTH_LENGTH + 1];
        fromGet.readFully(msgLenType);

        int msgLen = ByteBuffer.wrap(msgLenType, 0, 4).getInt() - 1; // not including message type
        byte msgType = msgLenType[4];

        // receive payload
        byte[] payload = new byte[msgLen];
        fromGet.readFully(payload);

        processReceivedMessage(msgType, payload);
    }

    /**
     * Process message
     * @param msgType Type of msg
     * @param rcvMsg Payload
     * @throws IOException
     */
    private void processReceivedMessage(int msgType, byte[] rcvMsg) throws IOException
    {
        System.out.println("Seed: Receive msg of type " + msgType + " from " + target.getPeerId());

        switch (msgType)
        {
            case Misc.TYPE_REQUEST:
                int pieceIdx = Misc.byteArrayToInt(rcvMsg);
                System.out.println("Seed: Piece requested: " + pieceIdx);
                sendPiece(pieceIdx);
                break;

            case Misc.TYPE_INTERESTED:
                //TODO: NOTE only for testing REQUEST/PIECE
                sendMessage(1, Misc.TYPE_UNCHOKE, null);
                break;

            case Misc.TYPE_NOT_INTERESTED:
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
        sendMessage(1 + buffer.length, Misc.TYPE_PIECE, buffer);
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
