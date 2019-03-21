import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class ChokeThread implements Runnable
{
    private final Peer thisPeer;
    private final Thread thread;
    private final OptimisticChokeThread optimisticChokeThread;

    public ChokeThread(Peer thisPeer)
    {
        this.thisPeer = thisPeer;
        thread = new Thread(this);
        optimisticChokeThread = new OptimisticChokeThread(thisPeer);
    }

    @Override
    public void run()
    {
        while (true)
        {
            try
            {
                Thread.sleep(MMT.UnchokingInterval * 1000);

                // get data
                double[] rate = new double[thisPeer.getPeerList().size() - 1];
                int[] peerId = new int[thisPeer.getPeerList().size() - 1];
                List<PeerInfo> peerList = thisPeer.getPeerList();

                for (int i = 0; i < rate.length; i++)
                {
                    int id = peerList.get(i).getPeerId();
                    if (id != thisPeer.getPeerId())
                    {
                        rate[i] = thisPeer.getDownloadRate(id);
                        peerId[i] = id;
                    }
                }

                ArrayList<Integer> peerIdx;
                if (!thisPeer.getHasFile())
                {
                    peerIdx = findKHighestRate(rate, MMT.NumOfPreferredNeighbors);
                }
                else
                {
                    Random r = new Random();
                    //TODO select random index
                }

                // update preferred neighbor

            } catch (InterruptedException e)
            {
                e.printStackTrace();
                break;
            }
        }
    }

    /**
     * Find indexes of K highest rates
     * @param rate array of rates
     * @param K K
     * @return list of indexes of K highest rates
     */
    private ArrayList<Integer> findKHighestRate(double[] rate, int K)
    {
        ArrayList<Integer> largestIdx = new ArrayList<>(K);

        //TODO: comment the following block, put K largest indexes into largestIdx
        {
            for (int i = 0; i < rate.length; i++)
            {
                largestIdx.add(i);
            }
        }

        return largestIdx;
    }

    public void start()
    {
        thread.start();
        optimisticChokeThread.start();
    }

    public void exit()
    {
        thread.interrupt();
        optimisticChokeThread.exit();
    }
}

class OptimisticChokeThread implements Runnable
{
    private final Peer thisPeer;
    private final Thread thread;
    private final Random r = new Random();

    public OptimisticChokeThread(Peer thisPeer)
    {
        this.thisPeer = thisPeer;
        thread = new Thread(this);
    }

    @Override
    public void run()
    {
        List<PeerInfo> peerList = thisPeer.getPeerList();
        while (true)
        {
            try
            {
                Thread.sleep(MMT.OptimisticUnchokingInterval * 1000);

                int prevId = thisPeer.getOptimistUnchoke();
                int neighborId = prevId;

                while (neighborId == thisPeer.getPeerId() || neighborId == prevId)
                {
                    neighborId = peerList.get(r.nextInt(peerList.size())).getPeerId();
                }

                thisPeer.setOptimistUnchoke(neighborId);
            } catch (InterruptedException e)
            {
                e.printStackTrace();
                break;
            }
        }
    }

    public void start()
    {
        thread.start();
    }

    public void exit()
    {
        thread.interrupt();
    }
}
