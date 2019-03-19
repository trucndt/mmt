import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A thread waits for string and then write to log file
 */
public class Log implements Runnable
{
    private static FileWriter file;
    private static BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    /**
     * Create log file, initialize peer ID
     *
     * @param peerId
     * @throws IOException
     */
    public static void initialization(int peerId) throws IOException
    {
        String fileName = "log_peer_" + peerId + ".log";
        file = new FileWriter(fileName, false);
    }

    /**
     * print s to the log file with time
     *
     * @param logIn
     */
    public synchronized static void println(String logIn)
    {
        try
        {
            queue.put(logIn);
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Get current time
     * @return time as String
     */
    private static String getCurrentTime()
    {
        LocalDateTime time = LocalDateTime.now();
        return String.valueOf(time);
    }

    @Override
    public void run()
    {
        while (true)
        {
            try
            {
                String logIn = queue.take();
                file.write("[" + getCurrentTime() + "]: " + logIn + "\n");
                file.flush();
            } catch (InterruptedException | IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}