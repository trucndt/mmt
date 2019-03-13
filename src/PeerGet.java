import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeerGet implements Runnable
{
    private final Peer thisPeer;
    private final PeerInfo target;

    private Socket socket;

    private final DataOutputStream toSeed;
    private final DataInputStream fromSeed;

    public final AtomicBoolean finishHandshake;

    private final RandomAccessFile file;

    PeerGet(Peer thisPeer, PeerInfo target) throws IOException
    {
        this.target = target;
        this.thisPeer = thisPeer;

        makeConnection();

        toSeed = new DataOutputStream(socket.getOutputStream());
        fromSeed = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        finishHandshake = new AtomicBoolean(false);

        file = new RandomAccessFile(thisPeer.FILE_PATH, "rw");
    }

    @Override
    public void run()
    {
        try
        {
            sendHandShake(toSeed);

            // Notify other threads that it has finished handshake
            synchronized (finishHandshake)
            {
                finishHandshake.set(true);
                finishHandshake.notifyAll();
            }

            if (thisPeer.getHasFile() == 1)
            {
                socket.close();
                return;
            }

            while (true)
            {
                // wait for incoming messages
                byte[] msgLenType = new byte[Misc.MESSAGE_LENGTH_LENGTH + 1];
                fromSeed.readFully(msgLenType);

                int msgLen = ByteBuffer.wrap(msgLenType, 0, 4).getInt() - 1; // not including message type
                byte msgType = msgLenType[4];

                // receive payload
                byte[] payload = new byte[msgLen];
                fromSeed.readFully(payload);

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

    private void makeConnection()
    {
        try
        {
            System.out.println("Get: Make connection to " + target.getPeerId());

            // make connection to target
            socket = new Socket(target.getHostname(), target.getPort());
            System.out.println("Get: Connected to " + target.getPeerId() + " in port " + target.getPort());
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
    }

    private void sendHandShake(DataOutputStream toSeed)
    {
        /* send message */
        try
        {
            String messageOut = "P2PFILESHARINGPROJ" + "0000000000" + thisPeer.getPeerId();

            toSeed.write(messageOut.getBytes());
            toSeed.flush();
            System.out.println("Get: Send Handshake Message: { " + messageOut + " } to Client " + target.getPeerId());
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }
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
                // TODO handle bitfield
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

            case Misc.TYPE_PIECE:
                // Write to file
                int piece = ByteBuffer.wrap(rcvMsg, 0, 4).getInt();
                writeToFile(piece, rcvMsg, 4, rcvMsg.length - 4);

                sendRequest();
                break;

        }
    }

    /**
     * send message to socket
     * @param length message length
     * @param type message type
     * @param payload payload
     */
    private void sendMessage(int length, byte type, byte[] payload) throws IOException
    {
        System.out.println("Get: Sending message of type " + type + " with length " + length + " to " + target.getPeerId());
        toSeed.writeInt(length);
        toSeed.writeByte(type);
        toSeed.write(payload, 0, length  - 1);
        toSeed.flush();
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

}
