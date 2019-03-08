import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class PeerGet implements Runnable
{
    private final Peer thisPeer;
    private final PeerInfo target;

    private Socket socket;

    PeerGet(Peer thisPeer, PeerInfo target)
    {
        this.target = target;
        this.thisPeer = thisPeer;
    }

    @Override
    public void run()
    {
        makeConnection();

        try
        {
            DataOutputStream toSeed = new DataOutputStream(socket.getOutputStream());
            DataInputStream fromSeed = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            sendHandShake(toSeed);


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
                byte[] rcvMsg = new byte[msgLen];
                fromSeed.readFully(rcvMsg);

                switch (msgType)
                {
                    case Misc.TYPE_BITFIELD:
                        // TODO handle bitfield
                        break;

                    case Misc.TYPE_HAVE:
                        // TODO handle have
                        break;

                }
            }

        } catch (IOException e)
        {
            e.printStackTrace();
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

    /**
     * send message to socket
     * @param outStream output stream of the socket
     * @param length message length
     * @param type message type
     * @param payload payload
     */
    private void sendMessage(DataOutputStream outStream, int length, byte type, byte[] payload)
    {
        try
        {
            outStream.writeInt(length);
            outStream.writeByte(type);
            outStream.write(payload);
            outStream.flush();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
