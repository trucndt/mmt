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
                createPeerGet();
            }

            if (thisPeer.getHasFile() == 1)
            {
                //TODO send bitfield

                //TODO wait for interested

                //TODO if interested and prefered -> unchoke
            }

            //NOTE only for testing the HAVE message
            sendHave();


            //TODO wait for having new pieces

            // sleep forever
            Thread.currentThread().join();

            socket.close();
        } catch (IOException e)
        {
            e.printStackTrace();
            try
            {
                socket.close();
            } catch (IOException e1)
            {
                e1.printStackTrace();
                System.err.println("Cannot close socket");
            }
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    private int receiveHandShake(DataInputStream fromGet) throws IOException
    {
        byte[] buffer = new byte[Misc.LENGTH_HANDSHAKE];
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
    private void sendMessage(int length, byte type, byte[] payload) throws IOException
    {
        System.out.println("Sending message of type " + type + " with length " + length + " to " + target.getPeerId());
        toGet.writeInt(length);
        toGet.writeByte(type);
        toGet.write(payload, 0, length  - 1);
        toGet.flush();
    }

    /**
     * Create a PeerGet thread and wait until it finished handshake
     * @throws IOException
     * @throws InterruptedException
     */
    private void createPeerGet() throws IOException, InterruptedException
    {
        PeerGet peerGet = new PeerGet(thisPeer, target);
        new Thread(peerGet).start();

        // wait until peerGet finish handshake
        while (!peerGet.finishHandshake.get())
        {
            synchronized (peerGet.finishHandshake)
            {
                peerGet.finishHandshake.wait();
            }
        }
    }

    private void sendHave() throws IOException
    {
        /* send message */
        byte[] payload = new byte[]{0,0,0,1};
        sendMessage(Misc.LENGTH_HAVE, Misc.TYPE_HAVE, payload);
    }

}
