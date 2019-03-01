import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class Peer
{
    private List<PeerInfo> peerList;
    private int peerId;
    private int serverPort;
    private int hasFile;


    public Peer(int peerId, List<PeerInfo> peerList)
    {
        this.peerId = peerId;
        this.peerList = peerList;

        for (PeerInfo peer : peerList)
        {
            if (peer.getPeerId() == peerId)
            {
                serverPort = peer.getPort();
                hasFile = peer.getHasFile();
                break;
            }
        }
    }

    public void start()
    {
        Thread serverListener = new Thread(new ServerListener(serverPort, this));
        serverListener.start();

        while (true)
        {
            // Make connection to other peer
            for (PeerInfo target : peerList)
            {
                if (target.getPeerId() < peerId)
                {
                    Thread peerGet = new Thread(new PeerGet(this, target));
                    peerGet.start();
                }
            }

            // do something here
            try
            {
                serverListener.join();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    public int getPeerId()
    {
        return peerId;
    }

    public int getHasFile()
    {
        return hasFile;
    }

    public void setHasFile(int hasFile)
    {
        this.hasFile = hasFile;
    }
}
