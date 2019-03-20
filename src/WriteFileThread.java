import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicInteger;

public class WriteFileThread implements Runnable
{
    private static final int MAX_THREADS = 10; // do not open more threads than this
    private static final AtomicInteger numThreads = new AtomicInteger(0);

    private final String filePath;
    private final int pieceIdx;
    private final int offset;
    private final int length;
    private final byte[] buffer;
    private final Peer thisPeer;

    /**
     * Start a thread to write piece to file, then set the corresponding bitfield
     * @param filePath path to file
     * @param pieceIdx piece index
     * @param buffer data
     * @param offset the start offset in the data
     * @param length number of bytes to write
     * @param thisPeer reference to Peer object
     */
    public WriteFileThread(String filePath, int pieceIdx, byte[] buffer, int offset, int length, Peer thisPeer)
    {
        this.filePath = filePath;
        this.pieceIdx = pieceIdx;
        this.offset = offset;
        this.length = length;
        this.buffer = buffer;
        this.thisPeer = thisPeer;

        synchronized (numThreads)
        {
            while (numThreads.get() >= MAX_THREADS)
            {
                try
                {
                    numThreads.wait();
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

            new Thread(this).start();
            numThreads.incrementAndGet();
        }
    }

    @Override
    public void run()
    {
        try
        {
            RandomAccessFile file = new RandomAccessFile(filePath, "rw");
            file.seek(pieceIdx * MMT.PieceSize);
            file.write(buffer, offset, length);
            file.close();
            thisPeer.setBitfield(pieceIdx, (byte)1);

            synchronized (numThreads)
            {
                numThreads.decrementAndGet();
                numThreads.notifyAll();
            }
        } catch (java.io.IOException e)
        {
            e.printStackTrace();
        }
    }
}
