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

                if (validId.size() == 0) continue;

                int numOfNeighborsToSelect = Math.min(MMT.NumOfPreferredNeighbors, validId.size());

                ArrayList<Integer> highestIdx;
                if (!thisPeer.getHasFile())
                {
                    // get indexes of top k highest download rates
                    highestIdx = findKHighestRate(rate, numOfNeighborsToSelect);
                }
                else
                {
                    // select random indexes
                    Random r = new Random();
                    highestIdx = new ArrayList<>(numOfNeighborsToSelect);

                    while (highestIdx.size() < numOfNeighborsToSelect)
                    {
                        int idx = r.nextInt(validId.size());
                        if (!highestIdx.contains(idx))
                            highestIdx.add(idx);
                    }
                }

                // update preferred neighbor
                boolean[] isPreferred = new boolean[validId.size()];
                Arrays.fill(isPreferred, false);
                for (int idx : highestIdx)
                    isPreferred[idx] = true;

                StringBuilder logPreferred = new StringBuilder();
                for (int i = 0; i < validId.size(); i++)
                {
                    thisPeer.setPreferredNeighbor(validId.get(i), isPreferred[i]);
                    if (isPreferred[i])
                        logPreferred.append(", ").append(validId.get(i));
                    Log.println("Neighbor " + validId.get(i) + " is preferred: " + isPreferred[i]);
                }

                Log.println("Peer "+ thisPeer.getPeerId() + " has the preferred neighbors " + logPreferred.substring(2));

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
     * @param k K
     * @return list of indexes of K highest rates
     */
    private ArrayList<Integer> findKHighestRate(ArrayList<Double> rate, int k)
    {
        Double[] arr = rate.toArray(new Double[0]);

        ArrayList<Integer> result = new ArrayList<>(k);
        int n = arr.length;
        // Build index array

        int[] arr_index = new int[n];
        for (int i = 0; i < n; i++)
        {
            arr_index[i] = i;
        }

        if (n > k)
        {
            // Build heap (rearrange array)
            for (int i = n / 2 - 1; i >= 0; i--)
                heapify(arr, arr_index, n, i);

            // One by one extract an element from heap
            for (int i = n - 1; i >= n - k; i--)
            {
                //            result.add(arr[0]);
                result.add(arr_index[0]);
                // Move current root to end
                Double temp = arr[0];
                arr[0] = arr[i];
                arr[i] = temp;
                int temp_index = arr_index[0];
                arr_index[0] = arr_index[i];
                arr_index[i] = temp_index;

                // call max heapify on the reduced heap
                heapify(arr, arr_index, i, 0);
            }
        } else
        {
            for (int i = n - 1; i >= 0; i--)
            {
                result.add(arr_index[i]);
            }
        }

        return result;
    }

    /**
     * heapify a subtree rooted with node i which is an index in arr[]
     * @param arr heap
     * @param arr_index
     * @param n size of heap
     * @param i root node index
     */
    private static void heapify(Double[] arr, int[] arr_index, int n, int i)
    {
        int largest = i; // Initialize largest as root
        int l = 2 * i + 1; // left = 2*i + 1
        int r = 2 * i + 2; // right = 2*i + 2

        // If left child is larger than root
        if (l < n && arr[l] > arr[largest])
            largest = l;

        // If right child is larger than largest so far
        if (r < n && arr[r] > arr[largest])
            largest = r;

        // If largest is not root
        if (largest != i)
        {
            Double swap = arr[i];
            arr[i] = arr[largest];
            arr[largest] = swap;
            int swap_index = arr_index[i];
            arr_index[i] = arr_index[largest];
            arr_index[largest] = swap_index;

            // Recursively heapify the affected sub-tree
            heapify(arr, arr_index, n, largest);
        }
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
        try
        {
            thread.join();
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
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
        try
        {
            thread.join();
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
}
