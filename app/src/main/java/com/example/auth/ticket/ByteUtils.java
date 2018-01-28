package com.example.auth.ticket;

/**
 * Created by phillip on 10.12.2017.
 */

public abstract class ByteUtils {

    public static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= 8;
        }
        return result;
    }


    public static void longToBytesSmall(byte[] dst,int startPos,long l) {
        for (int i = 3; i >= 0; i--) {
            dst[i+startPos] = (byte)(l & 0xFF);
            l >>= 8;
        }
    }

    public static long bytesToLong(byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }

    public static long bytesToLongSmall(byte[] b, int start) {
        long result = 0;
        for (int i = 0; i <4; i++) {
            result <<= 8;
            result |= (b[i+start] & 0xFF);
        }
        return result;
    }

    public static void intToBytes(byte[] b,int startPos,int length,int toConvert) {
        for (int i = length-1; i >= 0; i--) {
            b[i+startPos] = (byte)(toConvert & 0xFF);
            toConvert >>= 8;
        }
    }

    public static int bytesToInt(byte[] b,int startPos, int length) {
        int result = 0;
        for (int i = 0; i < length; i++) {
            result <<= 8;
            result |= (b[startPos+i] & 0xFF);
        }
        return result;
    }

}
