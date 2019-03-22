import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class WriteFileThread implements Runnable
{
    class PieceInfo
    {
        int pieceIdx;
        int offset;
        int length;
        byte[] buffer;

        public PieceInfo(int pieceIdx, int offset, int length, byte[] buffer)
        {
            this.pieceIdx = pieceIdx;
            this.offset = offset;
            this.length = length;
            this.buffer = buffer;
        }
    }

    private final Thread thread;
    private final RandomAccessFile file;
    private final BlockingQueue<PieceInfo> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean done = new AtomicBoolean(false);

    public WriteFileThread(String filePath) throws FileNotFoundException
    {
        thread = new Thread(this);
        file = new RandomAccessFile(filePath, "rw");
    }

    /**
     * Start thread
     */
    public void start()
    {
        thread.start();
    }

    /**
     * Tell thread to write a piece to file
     * @param pieceIdx piece index
     * @param buffer data
     * @param offset the start offset in the data
     * @param length number of bytes to write
     */
    public void writeFile(int pieceIdx, byte[] buffer, int offset, int length)
    {
        try
        {
            queue.put(new PieceInfo(pieceIdx, offset, length, buffer));
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void run()
    {
        try
        {
            while (true)
            {
                PieceInfo p = queue.take();
                if (p.pieceIdx == -1)
                    break;

                file.seek(p.pieceIdx * MMT.PieceSize);
                file.write(p.buffer, p.offset, p.length);
            }
        } catch (IOException | InterruptedException e)
        {
            e.printStackTrace();
        } finally
        {
            try
            {
                file.close();
            } catch (IOException e1)
            {
                e1.printStackTrace();
            }
            synchronized (done)
            {
                done.set(true);
                done.notifyAll();
            }
        }
    }

    /**
     * Exit procedure
     */
    public void exit()
    {
        try
        {
            queue.put(new PieceInfo(-1, -1, -1, null));
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        synchronized (done)
        {
            while (!done.get())
            {
                try
                {
                    done.wait();
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}
