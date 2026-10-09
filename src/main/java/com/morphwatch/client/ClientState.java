package com.morphwatch.client;

/** Little bits of client-only state: the flash, the R charge, the dial and the slam shake. */
public final class ClientState {
    public static final int FLASH_TICKS = 12;
    public static final int SHAKE_TICKS = 8;

    public static int flashTicks = 0;
    public static int chargeTicks = 0;
    public static boolean charging = false;

    public static boolean dialOpen = false;
    public static int dialIndex = 0;
    public static int dialPrevIndex = 0;
    public static long dialOpenedAt = 0;
    public static long dialChangedAt = 0;

    public static int shakeTicks = 0;

    private ClientState() {}

    public static void flash() {
        flashTicks = FLASH_TICKS;
    }

    public static void reset() {
        flashTicks = 0;
        chargeTicks = 0;
        charging = false;
        dialOpen = false;
        dialIndex = 0;
        shakeTicks = 0;
    }
}
