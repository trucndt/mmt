import java.io.FileNotFoundException;
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

    /**
     * Start a thread to write piece to file
     * @param filePath path to file
     * @param pieceIdx piece index
     * @param buffer data
     * @param offset the start offset in the data
     * @param length number of bytes to write
     */
    public WriteFileThread(String filePath, int pieceIdx, byte[] buffer, int offset, int length)
    {
        this.filePath = filePath;
        this.pieceIdx = pieceIdx;
        this.offset = offset;
        this.length = length;
        this.buffer = buffer;

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
