import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

public class PeerThread implements Runnable
{
    private final Peer thisPeer;
    private PeerInfo target;
    private Socket socket;
    private boolean initiator;

    private final DataOutputStream toNeighbor;
    private final DataInputStream fromNeighbor;

    private final RandomAccessFile file;

    private final BlockingQueue<MsgPeerSeed> toSeed;

    PeerThread(Peer thisPeer, PeerInfo target, Socket connectionSocket, boolean initiator) throws IOException
    {
        this.target = target;
        this.thisPeer = thisPeer;
        this.socket = connectionSocket;
        this.initiator = initiator;

        toNeighbor = new DataOutputStream(socket.getOutputStream());
        fromNeighbor = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        file = new RandomAccessFile(thisPeer.FILE_PATH, "rw");
        toSeed = new LinkedBlockingQueue<>();
    }

    @Override
    public void run()
    {
        try
        {
            // handshake message
            int success = handshake();
            if (success < 0)
            {
                socket.close();
                return;
            }

            // Create PeerSeed
            new Thread(new PeerSeed(thisPeer, this, toSeed)).start();

            while (true)
            {
                // wait for incoming messages
                byte[] msgLenType = new byte[Misc.MESSAGE_LENGTH_LENGTH + 1];
                fromNeighbor.readFully(msgLenType);

                int msgLen = ByteBuffer.wrap(msgLenType, 0, 4).getInt() - 1; // not including message type
                byte msgType = msgLenType[4];

                // receive payload
                byte[] payload = new byte[msgLen];
                fromNeighbor.readFully(payload);

                processReceivedMessage(msgType, payload);
            }

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
                System.err.println("Get: Cannot close socket");
            }
        }
    }

    private int handshake() throws IOException
    {
        if (initiator)
        {
            sendHandShake();
            int targetId = receiveHandShake2();

            if (targetId < 0)
            {
                socket.close();
                return -1;
            }
        }
        else
        {
            int targetId = receiveHandShake();
            if (targetId < 0)
            {
                socket.close();
                return -1;
            }

            // find peer id and assign target
            for (PeerInfo p: thisPeer.getPeerList())
            {
                if (p.getPeerId() == targetId)
                {
                    target = p;
                    break;
                }
            }

            sendHandShake();
        }

        return 0;
    }

    /**
     * Send handshake message to target
     */
    private void sendHandShake()
    {
        /* send message */
        try
        {
            String messageOut = "P2PFILESHARINGPROJ" + "0000000000" + thisPeer.getPeerId();

            toNeighbor.write(messageOut.getBytes());
            toNeighbor.flush();
            System.out.println("Get: Send Handshake Message: { " + messageOut + " } to Client " + target.getPeerId());
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }
    }

    /**
     * Non-initiator waits for handshake msg
     * @return target peer Id, -1 if failed
     * @throws IOException
     */
    private int receiveHandShake() throws IOException
    {
        byte[] buffer = new byte[Misc.LENGTH_HANDSHAKE];
        fromNeighbor.readFully(buffer);

        String rcvMsg = new String(buffer);
        System.out.println("Get: Receive msg " + rcvMsg);

        /* check handshake message */
        if (!rcvMsg.substring(0, 18).equals("P2PFILESHARINGPROJ"))
        {
            System.out.println("Get: Wrong handshake");
            return -1;
        }

        return Integer.parseInt(rcvMsg.substring(28, 32));
    }

    /**
     * Initiator waits for handshake msg
     * @return target peer Id, -1 if failed
     * @throws IOException
     */
    private int receiveHandShake2() throws IOException
    {
        byte[] buffer = new byte[Misc.LENGTH_HANDSHAKE];
        fromNeighbor.readFully(buffer);

        String rcvMsg = new String(buffer);
        System.out.println("Get: Receive msg " + rcvMsg);

        int peerId = Integer.parseInt(rcvMsg.substring(28,32));

        /* check handshake message */
        if (!rcvMsg.substring(0, 18).equals("P2PFILESHARINGPROJ") || peerId != target.getPeerId())
        {
            System.out.println("Get: Wrong handshake");
            return -1;
        }

        return peerId;
    }

    /**
     * Process message
     * @param msgType Type of msg
     * @param rcvMsg Payload
     * @throws IOException
     */
    private void processReceivedMessage(int msgType, byte[] rcvMsg) throws IOException
    {
        System.out.println("Get: Receive msg of type " + msgType + " from " + target.getPeerId());
        switch (msgType)
        {
            case Misc.TYPE_BITFIELD:
                boolean[] seedBitfield = makeBitfieldFromPayload(rcvMsg);
                thisPeer.updateNeighborBitfield(target.getPeerId(), seedBitfield);

                // if there exists an interesting piece, send INTERESTED
                if (thisPeer.selectNewPieceFromNeighbor(target.getPeerId()) != -1)
                    sendMessage(1, Misc.TYPE_INTERESTED, null);
                else
                    sendMessage(1, Misc.TYPE_NOT_INTERESTED, null);
                break;

            case Misc.TYPE_HAVE:
                //handle have
                int index = Misc.byteArrayToInt(rcvMsg);
                boolean exist = thisPeer.checkPiece(index);
                thisPeer.setNeighborBitfield(target.getPeerId(),index);

                if (exist)
                    sendMessage(1, Misc.TYPE_NOT_INTERESTED, null);
                else
                    sendMessage(1, Misc.TYPE_INTERESTED, null);

                break;

            case Misc.TYPE_UNCHOKE:
                // send REQUEST
                sendRequest();
                break;

            case Misc.TYPE_CHOKE:
                break;

            case Misc.TYPE_PIECE:
                // Write to file
                int piece = ByteBuffer.wrap(rcvMsg, 0, 4).getInt();
                writeToFile(piece, rcvMsg, 4, rcvMsg.length - 4);

                sendRequest();
                break;

            case Misc.TYPE_REQUEST:
                break;

            case Misc.TYPE_NOT_INTERESTED:
                break;

            case Misc.TYPE_INTERESTED:
                break;
        }
    }

    /**
     * send message to socket
     * @param length message length
     * @param type message type
     * @param payload payload
     */
    void sendMessage(int length, byte type, byte[] payload) throws IOException
    {
        System.out.println("Sending message of type " + type + " with length " + length + " to " + target.getPeerId());
        synchronized (toNeighbor)
        {
            toNeighbor.writeInt(length);
            toNeighbor.writeByte(type);
            toNeighbor.write(payload, 0, length  - 1);
//            toNeighbor.flush();
        }
    }

    /**
     * Send a msg to PeerSeed
     * @param type type of MsgPeerSeed
     * @param content object to send
     * @throws InterruptedException
     */
    void sendSeed(byte type, Object content) throws InterruptedException
    {
        MsgPeerSeed msg = new MsgPeerSeed(type, content);
        toSeed.put(msg);
    }

    /**
     * Find missing piece and send REQUEST msg
     * @throws IOException
     */
    private void sendRequest() throws IOException
    {
        int pieceIdx = thisPeer.selectNewPieceFromNeighbor(target.getPeerId());
        if (pieceIdx == -1)
        {
            // send not interested
            sendMessage(1, Misc.TYPE_NOT_INTERESTED, null);
            return;
        }

        // form request msg
        sendMessage(Misc.LENGTH_REQUEST, Misc.TYPE_REQUEST, Misc.intToByteArray(pieceIdx));
        thisPeer.setHavePiece(pieceIdx); //TODO: handle the case when not receiving PIECE
    }

    /**
     * Write piece to file
     * @param pieceIdx index of piece
     * @param buffer content
     * @param off the start offset
     * @param len number of bytes to write
     * @throws IOException
     */
    private void writeToFile(int pieceIdx, byte[] buffer, int off, int len) throws IOException
    {
        file.seek(pieceIdx * MMT.PieceSize);
        file.write(buffer, off, len);
    }

    /**
     * Translate payload to bitfield
     * @param payload payload (array of bytes)
     * @return bitfield
     */
    private boolean[] makeBitfieldFromPayload(byte[] payload)
    {
        boolean[] bitfield = new boolean[thisPeer.NUM_OF_PIECES];
        int byteIdx = 0;

        for (int i = 0; i < bitfield.length; i++)
        {
            bitfield[i] = (payload[byteIdx] & (0x80 >> (i % 8))) != 0;

            if ((i + 1) % 8 == 0) byteIdx++;
        }
        return bitfield;
    }

}
