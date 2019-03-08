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

    private void sendMessage(DataOutputStream outStream, int length, byte type, byte[] payload)
    {
        try
        {
            outStream.writeInt(length);
            outStream.writeByte(type);
            outStream.write(payload);
            outStream.flush();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
