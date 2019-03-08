import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;

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





            socket.close();
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
}
