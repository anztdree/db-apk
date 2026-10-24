package com.db.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

public final class Ws {

    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final Object writeLock = new Object();
    private volatile boolean open = true;

    public final SessionOwner owner;
    public final OutputStream out;

    public Ws(SessionOwner owner, OutputStream out) {
        this.owner = owner;
        this.out = out;
    }

    public interface SessionOwner {

        void onWsClosed(Ws ws);

        void onWsPacket(Ws ws, String text);
    }

    public static String acceptKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(Texts.utf8(key.trim() + WS_MAGIC));
            return Texts.b64encode(digest);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean handshake(OutputStream out, String key) {
        try {
            String accept = acceptKey(key);
            StringBuilder sb = new StringBuilder(
                    "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
            sb.append(accept);
            sb.append("\r\n\r\n");
            byte[] resp = Texts.utf8(sb.toString());
            out.write(resp);
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void sendClose() {
        sendControl(0x8, new byte[0]);
    }

    private void sendControl(int op, byte[] payload) {
        try {
            synchronized (this.writeLock) {
                this.out.write(op | 0x80);
                this.out.write(payload.length);
                this.out.write(payload);
                this.out.flush();
            }
        } catch (IOException e) {
        }
    }

    public void readLoop(InputStream in) {
        close: do {
            try {
                if (!this.open) {
                    break close;
                }
                int op = in.read();
                if (op < 0) {
                    break close;
                }
                int b2 = in.read();
                if (b2 < 0) {
                    break close;
                }
                boolean masked = (b2 & 0x80) != 0;
                int len = b2 & 0x7f;
                if (len == 0x7e) {
                    int hi = in.read();
                    int lo = in.read();
                    if (hi < 0) {
                        break close;
                    }
                    if (lo < 0) {
                        break close;
                    }
                    len = ((hi & 0xff) << 8) | (lo & 0xff);
                } else if (len == 0x7f) {
                    long size = 0L;
                    int i = 0;
                    while (i < 8) {
                        int c = in.read();
                        if (c < 0) {
                            break close;
                        }
                        size = (size << 8) | ((long) (c & 0xff));
                        i = i + 1;
                    }
                    if (size > 0x800000L) {
                        break close;
                    }
                    len = (int) size;
                }
                byte[] mask = new byte[4];
                if (masked) {
                    for (int i = 0; i < 4; i = i + 1) {
                        int c = in.read();
                        if (c < 0) {
                            break close;
                        }
                        mask[i] = (byte) c;
                    }
                }
                byte[] payload = new byte[len];
                for (int i = 0; i < len; ) {
                    int n = in.read(payload, i, len - i);
                    if (n < 0) {
                        break close;
                    }
                    i = i + n;
                }
                if (masked) {
                    for (int i = 0; i < len; i = i + 1) {
                        payload[i] = (byte) (payload[i] ^ mask[i & 3]);
                    }
                }
                op = op & 0xf;
                if (op == 0x8) {
                    this.sendClose();
                    break close;
                } else if (op == 0x9) {
                    this.sendControl(0xa, payload);
                } else if (op != 0xa && op == 0x1) {
                    String text = Texts.utf8(payload);
                    this.owner.onWsPacket(this, text);
                }
            } catch (IOException e) {
                break close;
            } catch (Throwable t) {
                this.open = false;
                this.owner.onWsClosed(this);
                throw t;
            }
        } while (true);
        this.open = false;
        this.owner.onWsClosed(this);
    }

    public void sendText(String text) {
        if (!this.open) {
            return;
        }
        try {
            byte[] data = Texts.utf8(text);
            ByteArrayOutputStream head = new ByteArrayOutputStream(data.length + 10);
            head.write(0x81);
            if (data.length < 0x7e) {
                head.write(data.length);
            } else if (data.length <= 0xffff) {
                head.write(0x7e);
                head.write((data.length >>> 8) & 0xff);
                head.write(data.length & 0xff);
            } else {
                head.write(0x7f);
                for (int i = 7; i >= 0; i = i - 1) {
                    head.write((int) ((((long) data.length) >>> (i * 8)) & 0xffL));
                }
            }
            synchronized (this.writeLock) {
                this.out.write(head.toByteArray());
                this.out.write(data);
                this.out.flush();
            }
        } catch (IOException e) {
            this.open = false;
        }
    }
}
