public class PeerInfo implements Cloneable
{
    private int peerId;
    private String hostname;
    private int port;
    private int hasFile;

    public PeerInfo(int peerId, String hostname, int port, int hasFile)
    {
        this.peerId = peerId;
        this.hostname = hostname;
        this.port = port;
        this.hasFile = hasFile;
    }

    public int getPeerId()
    {
        return peerId;
    }

    public void setPeerId(int peerId)
    {
        this.peerId = peerId;
    }

    public String getHostname()
    {
        return hostname;
    }

    public void setHostname(String hostname)
    {
        this.hostname = hostname;
    }

    public int getPort()
    {
        return port;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    public int getHasFile()
    {
        return hasFile;
    }

    public void setHasFile(int hasFile)
    {
        this.hasFile = hasFile;
    }

    public void printInfo()
    {
        System.out.println("ID: " + peerId);
        System.out.println("Host name: " + hostname);
        System.out.println("Port: " + port);
        System.out.println("Has file: " + hasFile);
    }

    @Override
    public PeerInfo clone()
    {
        try
        {
            return (PeerInfo) super.clone();
        } catch (CloneNotSupportedException e)
        {
            e.printStackTrace();
            return null;
        }
    }
}
