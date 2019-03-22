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
    static class LogMsg
    {
        String time;
        String content;

        public LogMsg(String time, String content)
        {
            this.time = time;
            this.content = content;
        }
    }

    private static FileWriter file;
    private static BlockingQueue<LogMsg> queue = new LinkedBlockingQueue<>();
    private static Thread thread;

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

        thread = new Thread(new Log());
        thread.start();
    }

    /**
     * print s to the log file with time
     *
     * @param logIn
     */
    public static void println(String logIn)
    {
        try
        {
            queue.put(new LogMsg(getCurrentTime(), logIn));
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
        return time.toString();
    }

    @Override
    public void run()
    {
        try
        {
            while (true)
            {
                LogMsg msg = queue.take();
                if (msg.time.equals(""))
                    break;
                file.write("[" + msg.time + "]: " + msg.content + "\n");
                file.flush();
            }
        } catch (InterruptedException | IOException e)
        {
            e.printStackTrace();
        } finally
        {
            try
            {
                file.close();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void exit()
    {
        try
        {
            queue.put(new LogMsg("", ""));
            file.close();
        } catch (InterruptedException | IOException e)
        {
            e.printStackTrace();
            thread.interrupt();
        }
    }
}