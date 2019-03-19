import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerListener implements Runnable
{
    private ServerSocket welcomeSocket = null;
    private final Peer thisPeer;
    private final int serverPort;

    ServerListener(int port, Peer thisPeer) throws IOException
    {
        this.thisPeer = thisPeer;
        this.serverPort = port;

        welcomeSocket = new ServerSocket(port);
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

                new Thread(new PeerThread(thisPeer, null, connectionSocket, false)).start();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
