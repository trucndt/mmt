import java.nio.ByteBuffer;

public class Misc
{
    public static final byte TYPE_CHOKE = 0;
    public static final byte TYPE_UNCHOKE = 1;
    public static final byte TYPE_INTERESTED = 2;
    public static final byte TYPE_NOT_INTERESTED = 3;
    public static final byte TYPE_HAVE = 4;
    public static final byte TYPE_BITFIELD = 5;
    public static final byte TYPE_REQUEST = 6;
    public static final byte TYPE_PIECE = 7;

    public static final int LENGTH_HANDSHAKE = 32;
    public static final int MESSAGE_LENGTH_LENGTH = 4;
    public static final int LENGTH_HAVE = 5;
    public static final int LENGTH_REQUEST = 5;

    /**
     * Convert byte[] to int
     * @param bytes 4 bytes
     * @return int
     */
    public static int byteArrayToInt(byte[] bytes)
    {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    /**
     * Convert int to byte[]
     * @param num the 4-byte integer number
     * @return the byte array
     */
    public static byte[] intToByteArray(int num)
    {
        return ByteBuffer.allocate(4).putInt(num).array();
    }
}
