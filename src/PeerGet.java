import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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

    PeerGet(Peer thisPeer, PeerInfo target) throws IOException
    {
        this.target = target;
        this.thisPeer = thisPeer;

        makeConnection();

        toSeed = new DataOutputStream(socket.getOutputStream());
        fromSeed = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

        finishHandshake = new AtomicBoolean(false);
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
            } catch (IOException e1)
            {
                e1.printStackTrace();
                System.err.println("Cannot close socket");
            }
        }
    }

    private void makeConnection()
    {
        try
        {
            System.out.println("Make connection to " + target.getPeerId());

            // make connection to target
            socket = new Socket(target.getHostname(), target.getPort());
            System.out.println("Connected to " + target.getPeerId() + " in port " + target.getPort());
        }
        catch (ConnectException e)
        {
            System.err.println("Connection refused. You need to initiate a server first.");
        }
        catch(UnknownHostException unknownHost)
        {
            System.err.println("You are trying to connect to an unknown host!");
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
            System.out.println("Send Handshake Message: { " + messageOut + " } to Client " + target.getPeerId());
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }
    }

    private void processReceivedMessage(int msgType, byte[] rcvMsg) throws IOException
    {
        System.out.println("Receive msg of type " + msgType + " from " + target.getPeerId());
        switch (msgType)
        {
            case Misc.TYPE_BITFIELD:
                // TODO handle bitfield
                break;

            case Misc.TYPE_HAVE:
                //handle have
                int index = Misc.byteArrayToInt(rcvMsg);
                boolean exist = thisPeer.checkPiece(index);
                System.out.println(exist);
                if (exist)
                    sendMessage(1, Misc.TYPE_NOT_INTERESTED, null);
                else
                    sendMessage(1, Misc.TYPE_INTERESTED, null);
                break;

            case Misc.TYPE_UNCHOKE:
                break;

            case Misc.TYPE_PIECE:
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
        System.out.println("Sending message of type " + type + " with length " + length + " to " + target.getPeerId());
        toSeed.writeInt(length);
        toSeed.writeByte(type);
        toSeed.write(payload, 0, length  - 1);
        toSeed.flush();
    }

}
