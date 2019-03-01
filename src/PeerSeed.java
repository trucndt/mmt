import java.net.Socket;

public class PeerSeed implements Runnable
{
    protected final Socket connectionSocket;
    protected final Peer thisPeer;

    public PeerSeed(Socket connectionSocket, Peer thisPeer)
    {
        this.connectionSocket = connectionSocket;
        this.thisPeer = thisPeer;
    }

    @Override
    public void run()
    {
        /*
        Receive handshake
         */

        /*
        Send handshake
         */
    }

    private void sendHandShake()
    {

    }
}
