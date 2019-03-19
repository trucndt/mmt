import java.nio.ByteBuffer;

public class Misc
{
    public static final int LENGTH_HANDSHAKE = 32;
    public static final int MESSAGE_LENGTH_LENGTH = 4;
//    public static final int LENGTH_HAVE = 5;
//    public static final int LENGTH_REQUEST = 5;

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
