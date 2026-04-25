package org.mammoth.stdlib;

/**
 * Mammoth standard library runtime.
 * Provides core utility functions available to all mammoth programs at runtime.
 */
public final class MammothRuntime {

    private MammothRuntime() {}

    public static void print(String s) {
        System.out.print(s);
    }

    public static void print(int i) {
        System.out.print(i);
    }

    public static void print(long l) {
        System.out.print(l);
    }

    public static void print(double d) {
        System.out.print(d);
    }

    public static void print(boolean b) {
        System.out.print(b);
    }

    public static void println(String s) {
        System.out.println(s);
    }

    public static void println(int i) {
        System.out.println(i);
    }

    public static void println(long l) {
        System.out.println(l);
    }

    public static void println(double d) {
        System.out.println(d);
    }

    public static void println(boolean b) {
        System.out.println(b);
    }

    public static void printf(String format, Object... args) {
        System.out.printf(format, args);
    }
}
