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

    private boolean isUnchoke;

    private final DataOutputStream toNeighbor;
    private final DataInputStream fromNeighbor;

    private final BlockingQueue<MsgPeerSeed> toSeed;

    private final PeerSeed peerSeed;

    PeerThread(Peer thisPeer, PeerInfo target, Socket connectionSocket, boolean initiator) throws IOException
    {
        this.target = target;
        this.thisPeer = thisPeer;
        this.socket = connectionSocket;
        this.initiator = initiator;

        toNeighbor = new DataOutputStream(socket.getOutputStream());
        fromNeighbor = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        toSeed = new LinkedBlockingQueue<>();

        peerSeed = new PeerSeed(thisPeer, this, toSeed);
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
            peerSeed.start();

            while (true)
            {
                // wait for incoming messages
                byte[] msgLenType = new byte[Misc.MESSAGE_LENGTH_LENGTH + 1];
                fromNeighbor.readFully(msgLenType);

                int msgLen = ByteBuffer.wrap(msgLenType, 0, 4).getInt() - 1; // not including message type
                byte msgType = msgLenType[4];

                byte[] payload = new byte[msgLen];
                if (msgType == Message.TYPE_PIECE)
                {
                    double downloadRate = readFullyAndGetRate(payload);
                    thisPeer.setDownloadRate(target.getPeerId(), downloadRate);
                } else
                {
                    fromNeighbor.readFully(payload);
                }

                processReceivedMessage(new Message(msgType, payload));
            }

        } catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                socket.close();
                peerSeed.exit();
            } catch (IOException e1)
            {
                e1.printStackTrace();
                System.err.println("Get: Cannot close socket");
            }
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

            Log.println("Peer " + thisPeer.getPeerId() + " is connected from Peer " + targetId);

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
     */
    private void processReceivedMessage(Message rcvMsg)
    {
        System.out.println("Get: Receive msg of type " + rcvMsg.getType() + " from " + target.getPeerId());
        switch (rcvMsg.getType())
        {
            case Message.TYPE_BITFIELD:
                boolean[] seedBitfield = makeBitfieldFromPayload(rcvMsg.getPayload());
                thisPeer.setNeighborBitfield(target.getPeerId(), seedBitfield);

                // if there exists an interesting piece, send INTERESTED
                for (int i = 0; i < seedBitfield.length; i++)
                    if (seedBitfield[i] && !thisPeer.checkPiece(i))
                    {
                        sendMessage(new Message(Message.TYPE_INTERESTED, null));
                        return;
                    }

                sendMessage(new Message(Message.TYPE_NOT_INTERESTED, null));
                break;

            case Message.TYPE_HAVE:
                //handle have
                int index = Misc.byteArrayToInt(rcvMsg.getPayload());
                boolean exist = thisPeer.checkPiece(index);
                thisPeer.setNeighborBitfield(target.getPeerId(),index);
                Log.println("Peer " + thisPeer.getPeerId() + " received the 'have' message from " + target.getPeerId() +
                        " for the piece " + index);

                if (!exist)
                    sendMessage(new Message(Message.TYPE_INTERESTED, null));

                break;

            case Message.TYPE_UNCHOKE:
                isUnchoke = true;
                Log.println("Peer " + thisPeer.getPeerId() + " is unchoked by " + target.getPeerId());
                sendRequest();
                break;

            case Message.TYPE_CHOKE:
                isUnchoke = false;
                Log.println("Peer " + thisPeer.getPeerId() + " is choked by " + target.getPeerId());
                break;

            case Message.TYPE_PIECE:
                // Write to file
                byte[] payload = rcvMsg.getPayload();
                int piece = ByteBuffer.wrap(payload, 0, 4).getInt();
                thisPeer.handleRcvNewPiece(piece, target.getPeerId(), payload, 4, payload.length - 4);

                if (isUnchoke) sendRequest();
                break;

            case Message.TYPE_INTERESTED:
                thisPeer.setInterestedNeighbor(target.getPeerId(), true);
                Log.println("Peer " + thisPeer.getPeerId() + " received the 'interested' message from " +
                        target.getPeerId());
                break;

            case Message.TYPE_NOT_INTERESTED:
                thisPeer.setInterestedNeighbor(target.getPeerId(), false);
                Log.println("Peer " + thisPeer.getPeerId() + " received the 'not interested' message from " +
                        target.getPeerId());
                break;

            default: // send to PeerSeed other messages
                sendSeed(MsgPeerSeed.TYPE_MSG, rcvMsg);
        }
    }

    /**
     * Send message to socket
     * @param msg Message object
     */
    void sendMessage(Message msg)
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
            try
            {
                toNeighbor.writeInt(length);
                toNeighbor.writeByte(msg.getType());
                toNeighbor.write(payload, 0, length - 1);
            }catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Send a msg to PeerSeed
     * @param type type of MsgPeerSeed
     * @param content object to send
     */
    void sendSeed(byte type, Object content)
    {
        MsgPeerSeed msg = new MsgPeerSeed(type, content);
        try
        {
            toSeed.put(msg);
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Find missing piece and send REQUEST msg
     */
    private void sendRequest()
    {
        if (thisPeer.getHasFile()) return;

        int pieceIdx = thisPeer.selectNewPieceFromNeighbor(target.getPeerId());
        if (pieceIdx < 0)
        {
            // send not interested
            if (pieceIdx == -2)
                sendMessage(new Message(Message.TYPE_NOT_INTERESTED, null));
            return;
        }

        // form request msg
        sendMessage(new Message(Message.TYPE_REQUEST, Misc.intToByteArray(pieceIdx)));
        Log.println("Request " + pieceIdx + " from neighbor " + target.getPeerId());
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

    /**
     * Read data from socket and return download rate
     * @param buffer the buffer into which the data is read
     * @return download rate
     * @throws IOException
     */
    private double readFullyAndGetRate(byte[] buffer) throws IOException
    {
        // TODO: nanoTime doesn't capture wall-clock time
        long start = System.nanoTime();
        fromNeighbor.readFully(buffer);
        long cost = System.nanoTime() - start;

        double estRate = 0.875;
        double downloadRate;
        double estimateDownloadRate = 0.0;

        if (cost > 0)
        {
            downloadRate = buffer.length * 1.0 / cost;
            //TODO: do we need this?
            estimateDownloadRate = estRate*thisPeer.getDownloadRate(target.getPeerId()) + (1-estRate)*downloadRate;
            Log.println("Rate of neighbor " + target.getPeerId() + " is: " + estimateDownloadRate + '\t' + cost);
        }

        return estimateDownloadRate;
    }

    public PeerInfo getTarget()
    {
        return target;
    }

    public void start()
    {
        new Thread(this).start();
    }

    /**
     * exit procedure for PeerThread
     * @throws IOException
     */
    public void exit() throws IOException
    {
        peerSeed.exit(); // wait until PeerSeed exit
        synchronized (toNeighbor)
        {
            toNeighbor.flush();
        }
        socket.close();
    }

}
