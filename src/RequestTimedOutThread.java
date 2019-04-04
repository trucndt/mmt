public class RequestTimedOutThread implements Runnable
{
    private static final int MAX_TIMEOUT_SECOND = 3;
    private final Peer peer;
    private final int pieceIdx;

    RequestTimedOutThread(Peer peer, int pieceIdx)
    {
        this.peer = peer;
        this.pieceIdx = pieceIdx;
    }

    public void start()
    {
        new Thread(this).start();
    }

    @Override
    public void run()
    {

    }
}
