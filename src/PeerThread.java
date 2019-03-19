import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PeerThread implements Runnable
{
    private final Peer thisPeer;
    private PeerInfo target;
    private Socket socket;
    private boolean initiator;

    private final DataOutputStream toNeighbor;
    private final DataInputStream fromNeighbor;

    private final BlockingQueue<MsgPeerSeed> toSeed;

    PeerThread(Peer thisPeer, PeerInfo target, Socket connectionSocket, boolean initiator) throws IOException
    {
        this.target = target;
        this.thisPeer = thisPeer;
        this.socket = connectionSocket;
        this.initiator = initiator;

        toNeighbor = new DataOutputStream(socket.getOutputStream());
        fromNeighbor = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

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

                processReceivedMessage(new Message(msgType, payload));
            }

        } catch (IOException e)
        {
            e.printStackTrace();
            try
            {
                socket.close();
            } catch (IOException e1)
            {
                e1.printStackTrace();
                System.err.println("Get: Cannot close socket");
            }
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Handshake procedure
     * @return 0 if success, -1 otherwise
     * @throws IOException
     */
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
     * @param rcvMsg Message object of the received msg
     * @throws IOException
     */
    private void processReceivedMessage(Message rcvMsg) throws IOException, InterruptedException
    {
        System.out.println("Get: Receive msg of type " + rcvMsg.getType() + " from " + target.getPeerId());
        switch (rcvMsg.getType())
        {
            case Message.TYPE_BITFIELD:
                boolean[] seedBitfield = makeBitfieldFromPayload(rcvMsg.getPayload());
                thisPeer.updateNeighborBitfield(target.getPeerId(), seedBitfield);

                // if there exists an interesting piece, send INTERESTED
                if (thisPeer.selectNewPieceFromNeighbor(target.getPeerId()) != -1)
                    sendMessage(new Message(Message.TYPE_INTERESTED, null));
                else
                    sendMessage(new Message(Message.TYPE_NOT_INTERESTED, null));
                break;

            case Message.TYPE_HAVE:
                //handle have
                int index = Misc.byteArrayToInt(rcvMsg.getPayload());
                boolean exist = thisPeer.checkPiece(index);
                thisPeer.setNeighborBitfield(target.getPeerId(),index);

                if (exist)
                    sendMessage(new Message(Message.TYPE_NOT_INTERESTED, null));
                else
                    sendMessage(new Message(Message.TYPE_INTERESTED, null));

                break;

            case Message.TYPE_UNCHOKE:
                // send REQUEST
                sendRequest();
                break;

            case Message.TYPE_CHOKE:
                break;

            case Message.TYPE_PIECE:
                // Write to file
                byte[] payload = rcvMsg.getPayload();
                int piece = ByteBuffer.wrap(payload, 0, 4).getInt();
                new WriteFileThread(thisPeer.FILE_PATH, piece, payload, 4, payload.length - 4);

                sendRequest();
                break;

            default: // send to PeerSeed other messages
                sendSeed(MsgPeerSeed.TYPE_MSG, rcvMsg);
        }
    }

    /**
     * Send message to socket
     * @param msg Message object
     * @throws IOException
     */
    void sendMessage(Message msg) throws IOException
    {
        byte[] payload = msg.getPayload();
        int length;

        if (payload == null)
            length = 1;
        else
            length = 1 + msg.getPayload().length;

        System.out.println("Sending message of type " + msg.getType() + " with length " + length + " to " + target.getPeerId());

        synchronized (toNeighbor)
        {
            toNeighbor.writeInt(length);
            toNeighbor.writeByte(msg.getType());
            toNeighbor.write(payload, 0, length - 1);
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
            sendMessage(new Message(Message.TYPE_NOT_INTERESTED, null));
            return;
        }

        // form request msg
        sendMessage(new Message(Message.TYPE_REQUEST, Misc.intToByteArray(pieceIdx)));
        thisPeer.setHavePiece(pieceIdx); //TODO: handle the case when not receiving PIECE
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
