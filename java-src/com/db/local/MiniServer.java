package com.db.local;

import android.content.Context;

public final class MiniServer {

    private MiniServer() {
    }

    public static void start(Context context) {
        Core.start(context);
        try {
            checkOverlay(context);
        } catch (Throwable t) {
        }
    }

    public static void checkOverlay(Context context) {
        try {
            Dbg.checkOverlay(context);
        } catch (Throwable t) {
        }
    }
}
