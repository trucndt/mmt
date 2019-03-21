public class MsgPeerSeed
{
    static final byte TYPE_MSG = 0;
    static final byte TYPE_NEW_PIECE = 1;
    static final byte TYPE_EXIT = 2;
    static final byte TYPE_UNCHOKE = 3;
    static final byte TYPE_CHOKE = 4;

    private final byte eventType;
    private final Object content;

    public MsgPeerSeed(byte eventType, Object content)
    {
        this.eventType = eventType;
        this.content = content;
    }

    public byte getEventType()
    {
        return eventType;
    }

    public Object getContent()
    {
        return content;
    }
}
