package com.db.local.server;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Engine dragonball.db via JDBC (org.xerial sqlite-jdbc) — HANYA untuk harness
 * desktop (sandbox): SQL-nya PERSIS DbAndroid sehingga harness menguji SQL
 * sungguhan, bukan tiruan. Class ini TIDAK dimasukkan ke classes2.dex
 * (build.sh menyaringnya) — di device jalur utama tetap DbAndroid.
 */
final class DbJdbc implements Db.Impl {

    private final Connection conn;

    DbJdbc(File file) throws Exception {
        Class.forName("org.sqlite.JDBC");
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        Statement st = conn.createStatement();
        try {
            for (String sql : Db.SCHEMA) {
                st.execute(sql);
            }
        } finally {
            st.close();
        }
    }

    // ---------------- accounts ----------------

    @Override
    public String[] account(String username) {
        if (username == null || username.length() == 0) {
            return null;
        }
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(
                    "SELECT pass_md5, created_at, last_login, login_count FROM accounts WHERE username=?");
            ps.setString(1, username);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return new String[]{nz(rs.getString(1)), nz(rs.getString(2)),
                    nz(rs.getString(3)), String.valueOf(rs.getLong(4))};
        } catch (Exception e) {
            return null;
        } finally {
            close(rs, ps);
        }
    }

    @Override
    public void upsertAccount(String username, String passMd5, String createdAt,
                              String lastLogin, long loginCount) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO accounts (username, pass_md5, created_at,"
                            + " last_login, login_count) VALUES (?,?,?,?,?)");
            ps.setString(1, username);
            ps.setString(2, nz(passMd5));
            ps.setString(3, nz(createdAt));
            ps.setString(4, nz(lastLogin));
            ps.setLong(5, loginCount);
            ps.executeUpdate();
            ps.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public int accountCount() {
        return count("SELECT COUNT(*) FROM accounts");
    }

    @Override
    public String[] accountNames() {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        Statement st = null;
        ResultSet rs = null;
        try {
            st = conn.createStatement();
            rs = st.executeQuery("SELECT username FROM accounts ORDER BY rowid");
            while (rs.next()) {
                out.add(nz(rs.getString(1)));
            }
        } catch (Exception ignored) {
        } finally {
            close(rs, st);
        }
        return out.toArray(new String[out.size()]);
    }

    // ---------------- guests ----------------

    @Override
    public String guestUid(String deviceId) {
        if (deviceId == null || deviceId.length() == 0) {
            return null;
        }
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement("SELECT uid FROM sdk_guests WHERE device_id=?");
            ps.setString(1, deviceId);
            rs = ps.executeQuery();
            return rs.next() ? nz(rs.getString(1)) : null;
        } catch (Exception e) {
            return null;
        } finally {
            close(rs, ps);
        }
    }

    @Override
    public void putGuest(String deviceId, String uid, String when) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO sdk_guests (device_id, uid, created_at) VALUES (?,?,?)");
            ps.setString(1, deviceId);
            ps.setString(2, uid);
            ps.setString(3, nz(when));
            ps.executeUpdate();
            ps.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public int guestCount() {
        return count("SELECT COUNT(*) FROM sdk_guests");
    }

    @Override
    public String[] guestRows() {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        Statement st = null;
        ResultSet rs = null;
        try {
            st = conn.createStatement();
            rs = st.executeQuery("SELECT device_id, uid FROM sdk_guests ORDER BY rowid");
            while (rs.next()) {
                out.add(nz(rs.getString(1)) + "\t" + nz(rs.getString(2)));
            }
        } catch (Exception ignored) {
        } finally {
            close(rs, st);
        }
        return out.toArray(new String[out.size()]);
    }

    // ---------------- sessions ----------------

    @Override
    public void putSession(String authToken, String uid, boolean guest, String when) {
        if (authToken == null || authToken.length() == 0 || uid == null || uid.length() == 0) {
            return;
        }
        try {
            // pola PERSIS DbAndroid (SELECT → UPDATE/INSERT) — tanpa ON CONFLICT
            // (SQLite di Android API lama tidak mendukung upsert DO UPDATE)
            PreparedStatement q = conn.prepareStatement(
                    "SELECT auth_token FROM sdk_sessions WHERE auth_token=?");
            q.setString(1, authToken);
            ResultSet r = q.executeQuery();
            boolean exists = r.next();
            r.close();
            q.close();
            PreparedStatement ps;
            if (exists) {
                ps = conn.prepareStatement("UPDATE sdk_sessions SET uid=?, is_guest=?,"
                        + " last_seen=? WHERE auth_token=?");
                ps.setString(1, uid);
                ps.setInt(2, guest ? 1 : 0);
                ps.setString(3, nz(when));
                ps.setString(4, authToken);
            } else {
                ps = conn.prepareStatement(
                        "INSERT INTO sdk_sessions (auth_token, uid, is_guest, created_at, last_seen)"
                                + " VALUES (?,?,?,?,?)");
                ps.setString(1, authToken);
                ps.setString(2, uid);
                ps.setInt(3, guest ? 1 : 0);
                ps.setString(4, nz(when));
                ps.setString(5, nz(when));
            }
            ps.executeUpdate();
            ps.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public String session(String authToken) {
        if (authToken == null || authToken.length() == 0) {
            return null;
        }
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement("SELECT uid, is_guest FROM sdk_sessions WHERE auth_token=?");
            ps.setString(1, authToken);
            rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return nz(rs.getString(1)) + "\t" + (rs.getInt(2) != 0 ? "1" : "0");
        } catch (Exception e) {
            return null;
        } finally {
            close(rs, ps);
        }
    }

    @Override
    public String lastSessionUid() {
        Statement st = null;
        ResultSet rs = null;
        try {
            st = conn.createStatement();
            rs = st.executeQuery(
                    "SELECT uid FROM sdk_sessions ORDER BY last_seen DESC, rowid DESC LIMIT 1");
            return rs.next() ? nz(rs.getString(1)) : null;
        } catch (Exception e) {
            return null;
        } finally {
            close(rs, st);
        }
    }

    // ---------------- kv ----------------

    @Override
    public String kv(String key) {
        if (key == null) {
            return null;
        }
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement("SELECT value FROM kv WHERE key=?");
            ps.setString(1, key);
            rs = ps.executeQuery();
            return rs.next() ? nz(rs.getString(1)) : null;
        } catch (Exception e) {
            return null;
        } finally {
            close(rs, ps);
        }
    }

    @Override
    public void kvPut(String key, String value) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO kv (key, value) VALUES (?,?)");
            ps.setString(1, key);
            ps.setString(2, value == null ? "" : value);
            ps.executeUpdate();
            ps.close();
        } catch (Exception ignored) {
        }
    }

    // ---------------- meta ----------------

    @Override
    public boolean sqlite() {
        return true;
    }

    // ---------------- util ----------------

    private int count(String sql) {
        Statement st = null;
        ResultSet rs = null;
        try {
            st = conn.createStatement();
            rs = st.executeQuery(sql);
            return rs.next() ? (int) rs.getLong(1) : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            close(rs, st);
        }
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static void close(AutoCloseable a, AutoCloseable b) {
        try {
            if (a != null) {
                a.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (b != null) {
                b.close();
            }
        } catch (Exception ignored) {
        }
    }
}
