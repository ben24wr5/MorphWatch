package com.morphwatch.client;

import com.morphwatch.MorphForm;

/** Little bits of client-only state: the flash, the R charge, the dial and the slam shake. */
public final class ClientState {
    public static final int FLASH_TICKS = 12;
    public static final int SHAKE_TICKS = 8;

    public static int flashTicks = 0;
    public static int chargeTicks = 0;
    public static boolean charging = false;

    public static boolean dialOpen = false;
    /** The mob the dial shows, and the one it showed before the last turn (null = start of the list). */
    public static MorphForm dialForm = null;
    public static MorphForm dialPrevForm = null;
    public static long dialOpenedAt = 0;
    public static long dialChangedAt = 0;
    /** When the dial was last closed (it takes a moment to fold away). */
    public static long dialClosedAt = -1000;
    /** How many clicks the dial has been turned in total (each click turns the dial face 30 degrees). */
    public static int dialTurnSteps = 0;
    public static int dialTurnPrevSteps = 0;

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
        dialClosedAt = -100000;
        dialForm = null;
        dialPrevForm = null;
        shakeTicks = 0;
    }
}
