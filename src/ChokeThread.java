import java.util.*;

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
                ArrayList<Double> rate = new ArrayList<>(thisPeer.getPeerList().size() - 1);
                ArrayList<Integer> validId = new ArrayList<>(thisPeer.getPeerList().size() - 1);
                List<PeerInfo> peerList = thisPeer.getPeerList();

                for (PeerInfo peerInfo : peerList)
                {
                    int id = peerInfo.getPeerId();
                    // Not myself and is interested
                    if (id != thisPeer.getPeerId() && thisPeer.checkInterestedNeighbor(id))
                    {
                        rate.add(thisPeer.getDownloadRate(id));
                        validId.add(id);
                    }
                }

                ArrayList<Integer> highestIdx;
                if (!thisPeer.getHasFile())
                {
                    highestIdx = findKHighestRate(rate, MMT.NumOfPreferredNeighbors);
                }
                else
                {
                    Random r = new Random();
                    //TODO remove the following line and select random index
                    highestIdx = findKHighestRate(rate, MMT.NumOfPreferredNeighbors);
                }

                // update preferred neighbor
                boolean[] isPreferred = new boolean[validId.size()];
                Arrays.fill(isPreferred, false);
                for (int idx : highestIdx)
                    isPreferred[idx] = true;

                for (int i = 0; i < validId.size(); i++)
                    thisPeer.setPreferredNeighbor(validId.get(i), isPreferred[i]);

                if (thread.isInterrupted()) break;
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
    private ArrayList<Integer> findKHighestRate(ArrayList<Double> rate, int K)
    {
        ArrayList<Integer> highestIdx = new ArrayList<>(K);

        //TODO: comment the following block, put K largest indexes into largestIdx
        {
            for (int i = 0; i < rate.size(); i++)
            {
                highestIdx.add(i);
            }
        }

        return highestIdx;
    }

    /**
     * Start this thread and the optimisticChoke
     */
    public void start()
    {
        thread.start();
        optimisticChokeThread.start();
    }

    /**
     * Exit procedure
     */
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
                Set<Integer> checkedNeighbor = new HashSet<>(peerList.size());

                while (neighborId == thisPeer.getPeerId() || neighborId == prevId
                        || thisPeer.checkPreferredNeighbor(neighborId) || !thisPeer.checkInterestedNeighbor(neighborId))
                {
                    checkedNeighbor.add(neighborId);
                    if (checkedNeighbor.size() == peerList.size())
                    {
                        neighborId = -1;
                        break;
                    }
                    neighborId = peerList.get(r.nextInt(peerList.size())).getPeerId();
                }

                if (neighborId != -1) thisPeer.setOptimistUnchoke(neighborId);

                if (thread.isInterrupted()) break;
            } catch (InterruptedException e)
            {
                e.printStackTrace();
                break;
            }
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
    }
}
