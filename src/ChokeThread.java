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

    public OptimisticChokeThread(Peer thisPeer)
    {
        this.thisPeer = thisPeer;
        thread = new Thread(this);
    }

    @Override
    public void run()
    {
        while (true)
        {
            try
            {
                Thread.sleep(MMT.OptimisticUnchokingInterval * 1000);
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
