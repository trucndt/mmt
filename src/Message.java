public class Message
{
    public static final byte TYPE_CHOKE = 0;
    public static final byte TYPE_UNCHOKE = 1;
    public static final byte TYPE_INTERESTED = 2;
    public static final byte TYPE_NOT_INTERESTED = 3;
    public static final byte TYPE_HAVE = 4;
    public static final byte TYPE_BITFIELD = 5;
    public static final byte TYPE_REQUEST = 6;
    public static final byte TYPE_PIECE = 7;

    private final int type;
    private final byte[] payload;

    public Message(int type, byte[] payload)
    {
        this.type = type;
        this.payload = payload;
    }

    public int getType()
    {
        return type;
    }

    public byte[] getPayload()
    {
        return payload;
    }
}
