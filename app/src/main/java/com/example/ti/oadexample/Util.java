package com.example.ti.oadexample;

/**
 * Utility class
 */
public class Util
{
    // Choose  loglevel
    public static int LOGLEVEL = 1;
    public static boolean ERROR = LOGLEVEL > 0;
    public static boolean WARN = LOGLEVEL > 1;
    public static boolean INFO = LOGLEVEL > 2;
    public static boolean DEBUG = LOGLEVEL > 3;

    /**
     * Get lower byte of an uint16
     */
    public static byte loUint16(short v) {
        return (byte) (v & 0xFF);
    }

    /**
     * Get high byte of an uint16
     */
    public static byte hiUint16(short v) {
        return (byte) (v >> 8);
    }

    /**
     * Build a uint16 from two bytes
     */
    public static short buildUint16(byte hi, byte lo) {
        return (short) ((hi << 8) + (lo & 0xff));
    }
}
