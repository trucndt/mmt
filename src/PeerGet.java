import java.net.Socket;

public class PeerGet implements Runnable
{
    protected final Peer thisPeer;
    private final PeerInfo target;

    public PeerGet(Peer thisPeer, PeerInfo target)
    {
        this.target = target;
        this.thisPeer = thisPeer;
    }

    @Override
    public void run()
    {
        System.out.println("Make connection to " + target.getPeerId());
        // make connection to target

        // send handshake
    }
}
