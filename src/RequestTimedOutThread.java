import java.util.Iterator;
import java.util.LinkedList;

public class RequestTimedOutThread implements Runnable
{
    /**
     * Store index and timestamp of a requested piece
     */
    private class RequestInfo
    {
        private final int pieceIdx;
        private final long timestamp_ms;

        public RequestInfo(int pieceIdx, long timestamp_ms)
        {
            this.pieceIdx = pieceIdx;
            this.timestamp_ms = timestamp_ms;
        }
    }

    private static final int MAX_TIMEOUT_SECOND = 2;
    private final Peer peer;
    private final Thread thread;

    private final LinkedList<RequestInfo> pieceIdxList;

    RequestTimedOutThread(Peer peer)
    {
        this.peer = peer;
        pieceIdxList = new LinkedList<>();

        thread = new Thread(this);
    }

    @Override
    public void run()
    {
        while (!thread.isInterrupted())
        {
            try
            {
                Thread.sleep(1000);
                long curTime = System.currentTimeMillis();

                synchronized (pieceIdxList)
                {
                    Iterator<RequestInfo> it = pieceIdxList.iterator();
                    while (it.hasNext())
                    {
                        RequestInfo r = it.next();
                        if (curTime - r.timestamp_ms >= MAX_TIMEOUT_SECOND*1000)
                        {
                            peer.requestTimeoutHandle(r.pieceIdx);
                            it.remove();
                        }
                    }
                }

            } catch (InterruptedException e)
            {
                e.printStackTrace();
                break;
            }
        }
    }

    /**
     * Add a requested piece to monitor it
     * @param pieceIdx index of piece
     */
    public void addRequestingPiece(int pieceIdx)
    {
        RequestInfo piece = new RequestInfo(pieceIdx, System.currentTimeMillis());
        synchronized (pieceIdxList)
        {
            pieceIdxList.add(piece);
        }
    }

    /**
     * Start this thread
     */
    public void start()
    {
        thread.start();
    }

    /**
     * Exit procedure
     */
    public void exit()
    {
        thread.interrupt();

        try
        {
            thread.join();
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

}
