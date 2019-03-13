import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

public class ServerListener implements Runnable
{
    private ServerSocket welcomeSocket = null;
    private final Peer thisPeer;
    private final int serverPort;

    public final List<PeerSeed> listOfPeerSeeds;

    ServerListener(int port, Peer thisPeer) throws IOException
    {
        this.thisPeer = thisPeer;
        this.serverPort = port;

        welcomeSocket = new ServerSocket(port);

        listOfPeerSeeds = new LinkedList<>();
    }

    @Override
    public void run()
    {
        try
        {
            System.out.println(serverPort);
            System.out.println("Waiting for clients at " + serverPort);

            while (true)
            {
                Socket connectionSocket = welcomeSocket.accept();
                System.out.println("new connection");

                PeerSeed ps = new PeerSeed(connectionSocket, thisPeer);
                new Thread(ps).start();

                listOfPeerSeeds.add(ps);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
