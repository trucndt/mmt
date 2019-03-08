import java.io.*;
import java.net.Socket;

public class PeerSeed implements Runnable
{
    private final Socket socket;
    private final Peer thisPeer;

    private PeerInfo target;

    private final DataInputStream fromGet;
    private final DataOutputStream toGet;

    PeerSeed(Socket connectionSocket, Peer thisPeer) throws IOException
    {
        this.socket = connectionSocket;
        this.thisPeer = thisPeer;

        fromGet = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        toGet = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public void run()
    {
        try
        {
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

            // Finish handshake, make a PeerGet to the target
            if (targetId > thisPeer.getPeerId())
            {
                new Thread(new PeerGet(thisPeer, target)).start();
            }

            if (thisPeer.getHasFile() == 1)
            {
                //TODO send bitfield

                //TODO wait for interested

                //TODO if interested and prefered -> unchoke
            }

            //TODO wait for having new pieces


            // sleep forever
            Thread.currentThread().join();

            socket.close();
        } catch (IOException | InterruptedException e)
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

    /**
     * send message to socket
     * @param length message length
     * @param type message type
     * @param payload payload
     */
    private void sendMessage(int length, byte type, byte[] payload)
    {
        try
        {
            toGet.writeInt(length);
            toGet.writeByte(type);
            toGet.write(payload);
            toGet.flush();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
