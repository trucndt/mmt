import java.io.*;
import java.net.Socket;

public class PeerSeed implements Runnable
{
    private final Socket socket;
    private final Peer thisPeer;

    private PeerInfo target;

    PeerSeed(Socket connectionSocket, Peer thisPeer)
    {
        this.socket = connectionSocket;
        this.thisPeer = thisPeer;
    }

    @Override
    public void run()
    {
        /*
        Receive msg
         */
        try
        {
            DataInputStream fromGet = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream toGet = new DataOutputStream(socket.getOutputStream());

            int targetId = receiveHandShake(fromGet);
            if (targetId < 0) return;

            for (PeerInfo p: thisPeer.getPeerList())
            {
                if (p.getPeerId() == targetId)
                {
                    target = p;
                    break;
                }
            }

            sendHandShake(toGet);


            socket.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    private int receiveHandShake(DataInputStream fromGet) throws IOException
    {
        byte[] buffer = new byte[Misc.HANDSHAKE_LENGTH];
        fromGet.readFully(buffer);

        String rcvMsg = new String(buffer);
        System.out.println("Receive msg " + rcvMsg);

        /* check handshake message */
        if (!rcvMsg.substring(0, 18).equals("P2PFILESHARINGPROJ"))
        {
            System.out.println("Wrong handshake");
            return -1;
        }

        return Integer.parseInt(rcvMsg.substring(28, 32));

    }

    private void sendHandShake(DataOutputStream toGet)
    {
        /* send message */
        try
        {
            String messageOut = "P2PFILESHARINGPROJ" + "0000000000" + thisPeer.getPeerId();

            toGet.write(messageOut.getBytes());
            toGet.flush();
            System.out.println("Send Handshake Message: { " + messageOut + " } to Client " + target.getPeerId());
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }
    }
}
