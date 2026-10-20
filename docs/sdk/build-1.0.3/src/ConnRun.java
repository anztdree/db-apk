package com.db.local;

import java.net.Socket;

/** Thread per koneksi — identik 1.0.1. */
public class ConnRun implements Runnable {

    private final Socket s;

    public ConnRun(Socket s) {
        this.s = s;
    }

    @Override
    public void run() {
        try {
            MiniServer.handle(this.s);
        } catch (Throwable ignored) {
        }
    }
}
