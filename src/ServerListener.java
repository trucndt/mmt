import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerListener implements Runnable
{
    private ServerSocket welcomeSocket = null;
    private final Peer thisPeer;
    private final int serverPort;

    ServerListener(int port, Peer thisPeer)
    {
        this.thisPeer = thisPeer;
        this.serverPort = port;
        try
        {
            welcomeSocket = new ServerSocket(port);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
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
                Thread serverThread = new Thread(new PeerSeed(connectionSocket, thisPeer));
                serverThread.start();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
