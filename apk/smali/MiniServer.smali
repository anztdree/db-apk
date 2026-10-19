.class public Lcom/db/local/MiniServer;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;
.source "MiniServer.java"


# static fields
.field private static APP:Landroid/content/Context;

.field private static DEADLINE:J

.field private static started:Z


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static handle(Ljava/net/Socket;)V
    .locals 7

    :try_all
    const/4 v1, 0x0

    const/16 v0, 0x1f40

    invoke-virtual {p0, v0}, Ljava/net/Socket;->setSoTimeout(I)V

    invoke-virtual {p0}, Ljava/net/Socket;->getInputStream()Ljava/io/InputStream;

    move-result-object v0

    move-object v6, v0

    invoke-static {v0}, Lcom/db/local/MiniServer;->readLine(Ljava/io/InputStream;)Ljava/lang/String;

    move-result-object v0

    # fix test6: habiskan sisa header request sampai baris kosong/EOF,
    # agar close() mengirim FIN (bukan RST) dan tidak memusnahkan data response
    :drain_loop

    invoke-static {v6}, Lcom/db/local/MiniServer;->readLine(Ljava/io/InputStream;)Ljava/lang/String;

    move-result-object v5

    if-eqz v5, :drain_done

    invoke-virtual {v5}, Ljava/lang/String;->length()I

    move-result v5

    if-nez v5, :drain_loop

    :drain_done
    if-eqz v0, :bad

    invoke-static {v0}, Lcom/db/local/MiniServer;->parsePath(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    if-eqz v1, :bad

    invoke-static {v1}, Lcom/db/local/MiniServer;->resolve(Ljava/lang/String;)[B

    move-result-object v2

    if-eqz v2, :bad

    invoke-static {v1}, Lcom/db/local/MiniServer;->mimeFor(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    const/4 v4, 0x1

    invoke-static {p0, v4, v2, v3}, Lcom/db/local/MiniServer;->respond(Ljava/net/Socket;I[BLjava/lang/String;)V

    const-string v4, "200"

    invoke-static {v4, v1}, Lcom/db/local/MiniServer;->logReq(Ljava/lang/String;Ljava/lang/String;)V

    goto :finish

    :bad
    invoke-static {p0}, Lcom/db/local/MiniServer;->respond404(Ljava/net/Socket;)V

    const-string v4, "404"

    if-eqz v1, :bad_np

    move-object v5, v1

    goto :bad_log

    :bad_np
    const-string v5, "(malformed)"

    :bad_log
    invoke-static {v4, v5}, Lcom/db/local/MiniServer;->logReq(Ljava/lang/String;Ljava/lang/String;)V

    :finish
    :try_all_end
    goto :close_it

    :catch_all
    move-exception v0

    :close_it
    :try_close
    # fix test6: shutdownOutput dulu agar data yang masih dalam antrean kirim
    # dikirim dengan FIN normal sebelum socket ditutup
    invoke-virtual {p0}, Ljava/net/Socket;->shutdownOutput()V

    invoke-virtual {p0}, Ljava/net/Socket;->close()V

    :try_close_end
    return-void

    :catch_close
    return-void

    .catch Ljava/lang/Throwable; {:try_all .. :try_all_end} :catch_all
    .catch Ljava/lang/Throwable; {:try_close .. :try_close_end} :catch_close
.end method

.method public static logReq(Ljava/lang/String;Ljava/lang/String;)V
    .locals 3

    :try_all
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, " "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "MiniServer"

    invoke-static {v1, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    :try_all_end
    return-void

    :catch_all
    return-void

    .catch Ljava/lang/Throwable; {:try_all .. :try_all_end} :catch_all
.end method

.method private static mimeFor(Ljava/lang/String;)Ljava/lang/String;
    .locals 2

    const-string v0, ".json"

    invoke-virtual {p0, v0}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :m1

    const-string v0, "application/json"

    return-object v0

    :m1
    const-string v0, ".html"

    invoke-virtual {p0, v0}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :m2

    const-string v0, "text/html"

    return-object v0

    :m2
    const-string v0, ".js"

    invoke-virtual {p0, v0}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :m3

    const-string v0, "application/javascript"

    return-object v0

    :m3
    const-string v0, ".version"

    invoke-virtual {p0, v0}, Ljava/lang/String;->endsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :m4

    const-string v0, "text/plain"

    return-object v0

    :m4
    const-string v0, "application/octet-stream"

    return-object v0
.end method

.method private static parsePath(Ljava/lang/String;)Ljava/lang/String;
    .locals 4

    :try_all
    const-string v0, " "

    invoke-virtual {p0, v0}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v0

    array-length v1, v0

    const/4 v2, 0x2

    if-lt v1, v2, :badpath

    const/4 v1, 0x1

    aget-object v1, v0, v1

    const/16 v3, 0x3f

    invoke-virtual {v1, v3}, Ljava/lang/String;->indexOf(I)I

    move-result v3

    if-ltz v3, :noquery

    const/4 v0, 0x0

    invoke-virtual {v1, v0, v3}, Ljava/lang/String;->substring(II)Ljava/lang/String;

    move-result-object v1

    :noquery
    invoke-virtual {v1}, Ljava/lang/String;->length()I

    move-result v0

    if-lez v0, :badpath

    const/4 v0, 0x0

    invoke-virtual {v1, v0}, Ljava/lang/String;->charAt(I)C

    move-result v2

    const/16 v3, 0x2f

    if-ne v2, v3, :ready

    const/4 v0, 0x1

    invoke-virtual {v1, v0}, Ljava/lang/String;->substring(I)Ljava/lang/String;

    move-result-object v1

    :ready
    invoke-virtual {v1}, Ljava/lang/String;->isEmpty()Z

    move-result v0

    if-nez v0, :badpath

    const-string v0, ".."

    invoke-virtual {v1, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v0

    if-eqz v0, :good

    goto :badpath

    :good
    return-object v1

    :badpath
    const/4 v0, 0x0

    :try_all_end
    return-object v0

    :catch_all
    move-exception v0

    const/4 v0, 0x0

    return-object v0

    .catch Ljava/lang/Throwable; {:try_all .. :try_all_end} :catch_all
.end method

.method private static readAll(Ljava/io/InputStream;)[B
    .locals 4

    new-instance v0, Ljava/io/ByteArrayOutputStream;

    invoke-direct {v0}, Ljava/io/ByteArrayOutputStream;-><init>()V

    const/16 v1, 0x2000

    new-array v1, v1, [B

    :loop
    invoke-virtual {p0, v1}, Ljava/io/InputStream;->read([B)I

    move-result v2

    const/4 v3, -0x1

    if-eq v2, v3, :done

    const/4 v3, 0x0

    invoke-virtual {v0, v1, v3, v2}, Ljava/io/ByteArrayOutputStream;->write([BII)V

    goto :loop

    :done
    invoke-virtual {v0}, Ljava/io/ByteArrayOutputStream;->toByteArray()[B

    move-result-object v0

    return-object v0
.end method

.method private static readLine(Ljava/io/InputStream;)Ljava/lang/String;
    .locals 4

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    :loop
    invoke-virtual {p0}, Ljava/io/InputStream;->read()I

    move-result v1

    const/4 v2, -0x1

    if-eq v1, v2, :eof

    const/16 v2, 0xa

    if-eq v1, v2, :done

    const/16 v2, 0xd

    if-eq v1, v2, :loop

    int-to-char v1, v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;

    goto :loop

    :eof
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->length()I

    move-result v1

    if-nez v1, :done

    const/4 v0, 0x0

    return-object v0

    :done
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method private static resolve(Ljava/lang/String;)[B
    .locals 5

    :try_all
    sget-object v0, Lcom/db/local/MiniServer;->APP:Landroid/content/Context;

    if-eqz v0, :null_ret

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/content/Context;->getExternalFilesDir(Ljava/lang/String;)Ljava/io/File;

    move-result-object v1

    if-eqz v1, :null_ret

    new-instance v2, Ljava/io/File;

    const-string v3, "server"

    invoke-direct {v2, v1, v3}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    new-instance v1, Ljava/io/File;

    invoke-direct {v1, v2, p0}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    invoke-virtual {v1}, Ljava/io/File;->isFile()Z

    move-result v2

    if-eqz v2, :try_assets

    new-instance v2, Ljava/io/FileInputStream;

    invoke-direct {v2, v1}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V

    invoke-static {v2}, Lcom/db/local/MiniServer;->readAll(Ljava/io/InputStream;)[B

    move-result-object v0

    invoke-virtual {v2}, Ljava/io/FileInputStream;->close()V

    return-object v0

    # test8: fallback assets (semantik test6 yang terbukti di perangkat) —
    # dipakai hanya bila file belum tersalin di Android/data/files/server.
    # Sumber byte identik dengan seed, tak terlihat oleh aplikasi.
    :try_assets
    sget-object v0, Lcom/db/local/MiniServer;->APP:Landroid/content/Context;

    if-eqz v0, :null_ret

    invoke-virtual {v0}, Landroid/content/Context;->getAssets()Landroid/content/res/AssetManager;

    move-result-object v0

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "mirror/"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/content/res/AssetManager;->open(Ljava/lang/String;)Ljava/io/InputStream;

    move-result-object v0

    invoke-static {v0}, Lcom/db/local/MiniServer;->readAll(Ljava/io/InputStream;)[B

    move-result-object v1

    invoke-virtual {v0}, Ljava/io/InputStream;->close()V

    return-object v1

    :null_ret
    const/4 v0, 0x0

    :try_all_end
    return-object v0

    :catch_all
    move-exception v0

    const/4 v0, 0x0

    return-object v0

    .catch Ljava/lang/Throwable; {:try_all .. :try_all_end} :catch_all
.end method

.method private static respond(Ljava/net/Socket;I[BLjava/lang/String;)V
    .locals 4

    invoke-virtual {p0}, Ljava/net/Socket;->getOutputStream()Ljava/io/OutputStream;

    move-result-object v0

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const/4 v2, 0x1

    if-eq p1, v2, :ok

    const-string v2, "HTTP/1.1 404 Not Found"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    goto :hdr

    :ok
    const-string v2, "HTTP/1.1 200 OK"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    :hdr
    const-string v2, "\r\nContent-Type: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, p3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, "\r\nContent-Length: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    array-length v2, p2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v2, "\r\nConnection: close\r\n\r\n"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    const-string v2, "UTF-8"

    invoke-virtual {v1, v2}, Ljava/lang/String;->getBytes(Ljava/lang/String;)[B

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/io/OutputStream;->write([B)V

    invoke-virtual {v0, p2}, Ljava/io/OutputStream;->write([B)V

    invoke-virtual {v0}, Ljava/io/OutputStream;->flush()V

    return-void
.end method

.method private static respond404(Ljava/net/Socket;)V
    .locals 3

    :try_all
    const/4 v0, 0x0

    new-array v0, v0, [B

    const-string v1, "application/octet-stream"

    const/4 v2, 0x0

    invoke-static {p0, v2, v0, v1}, Lcom/db/local/MiniServer;->respond(Ljava/net/Socket;I[BLjava/lang/String;)V

    :try_all_end
    return-void

    :catch_all
    return-void

    .catch Ljava/lang/Throwable; {:try_all .. :try_all_end} :catch_all
.end method

.method public static start(Landroid/content/Context;)V
    .locals 3

    :try_all
    sget-boolean v0, Lcom/db/local/MiniServer;->started:Z

    if-eqz v0, :do_start

    return-void

    :do_start
    const/4 v0, 0x1

    sput-boolean v0, Lcom/db/local/MiniServer;->started:Z

    invoke-virtual {p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    sput-object v0, Lcom/db/local/MiniServer;->APP:Landroid/content/Context;

    # test7: gerbang siap — ekstraksi seed ke Android/data/files/server SINKRON
    # (di thread utama, sebelum server bind) agar request pertama pasti dilayani
    # dari path server lokal resmi. Di dalamnya ada try/catch + cap waktu.

    invoke-static {v0}, Lcom/db/local/MiniServer;->bootstrapSync(Landroid/content/Context;)V

    new-instance v1, Lcom/db/local/MiniServer;

    invoke-direct {v1}, Lcom/db/local/MiniServer;-><init>()V

    new-instance v2, Ljava/lang/Thread;

    invoke-direct {v2, v1}, Ljava/lang/Thread;-><init>(Ljava/lang/Runnable;)V

    const/4 v0, 0x1

    invoke-virtual {v2, v0}, Ljava/lang/Thread;->setDaemon(Z)V

    invoke-virtual {v2}, Ljava/lang/Thread;->start()V

    :try_all_end
    return-void

    :catch_all
    return-void

    .catch Ljava/lang/Throwable; {:try_all .. :try_all_end} :catch_all
.end method

.method public static bootstrapSync(Landroid/content/Context;)V
    .locals 8

    :try_all
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v0

    const-wide/16 v2, 0x1f40

    add-long/2addr v0, v2

    sput-wide v0, Lcom/db/local/MiniServer;->DEADLINE:J

    invoke-virtual {p0}, Landroid/content/Context;->getExternalFilesDir(Ljava/lang/String;)Ljava/io/File;

    move-result-object v0

    if-eqz v0, :fail

    new-instance v1, Ljava/io/File;

    const-string v2, "server"

    invoke-direct {v1, v0, v2}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    invoke-virtual {v1}, Ljava/io/File;->mkdirs()Z

    invoke-virtual {p0}, Landroid/content/Context;->getAssets()Landroid/content/res/AssetManager;

    move-result-object v2

    const-string v3, "mirror"

    invoke-static {v2, v3, v1}, Lcom/db/local/MiniServer;->copyDir(Landroid/content/res/AssetManager;Ljava/lang/String;Ljava/io/File;)I

    move-result v0

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "bootstrap: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v2, " file disalin"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    const-string v1, "MiniServer"

    invoke-static {v1, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    goto :fin

    :fail
    const-string v0, "MiniServer"

    const-string v1, "bootstrap: storage eksternal null"

    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    :fin
    :try_all_end
    return-void

    :catch_all
    move-exception v0

    return-void

    .catch Ljava/lang/Throwable; {:try_all .. :try_all_end} :catch_all
.end method

.method public static copyDir(Landroid/content/res/AssetManager;Ljava/lang/String;Ljava/io/File;)I
    .locals 9

    const/4 v8, 0x0

    :try_all
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v0

    sget-wide v2, Lcom/db/local/MiniServer;->DEADLINE:J

    cmp-long v4, v0, v2

    if-lez v4, :wd_ok

    return v8

    :wd_ok
    invoke-virtual {p0, p1}, Landroid/content/res/AssetManager;->list(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :ret

    array-length v1, v0

    if-lez v1, :is_file

    const/4 v1, 0x0

    :loop_kids
    array-length v2, v0

    if-ge v1, v2, :ret

    aget-object v2, v0, v1

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v3, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v4, "/"

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    new-instance v4, Ljava/io/File;

    invoke-direct {v4, p2, v2}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    invoke-static {p0, v3, v4}, Lcom/db/local/MiniServer;->copyDir(Landroid/content/res/AssetManager;Ljava/lang/String;Ljava/io/File;)I

    move-result v5

    add-int/2addr v8, v5

    add-int/lit8 v1, v1, 0x1

    goto :loop_kids

    :is_file
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v0

    sget-wide v2, Lcom/db/local/MiniServer;->DEADLINE:J

    cmp-long v4, v0, v2

    if-lez v4, :wd_ok2

    return v8

    :wd_ok2
    invoke-virtual {p2}, Ljava/io/File;->exists()Z

    move-result v0

    if-nez v0, :ret

    invoke-virtual {p2}, Ljava/io/File;->getParentFile()Ljava/io/File;

    move-result-object v0

    if-eqz v0, :mk_ok

    invoke-virtual {v0}, Ljava/io/File;->mkdirs()Z

    :mk_ok
    new-instance v0, Ljava/io/File;

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    const-string v2, ".tmp"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-direct {v0, v1}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    invoke-virtual {p0, p1}, Landroid/content/res/AssetManager;->open(Ljava/lang/String;)Ljava/io/InputStream;

    move-result-object v1

    new-instance v2, Ljava/io/FileOutputStream;

    invoke-direct {v2, v0}, Ljava/io/FileOutputStream;-><init>(Ljava/io/File;)V

    const/16 v3, 0x2000

    new-array v3, v3, [B

    :loop_copy
    invoke-virtual {v1, v3}, Ljava/io/InputStream;->read([B)I

    move-result v4

    const/4 v5, -0x1

    if-eq v4, v5, :copy_done

    const/4 v5, 0x0

    invoke-virtual {v2, v3, v5, v4}, Ljava/io/FileOutputStream;->write([BII)V

    goto :loop_copy

    :copy_done
    invoke-virtual {v2}, Ljava/io/FileOutputStream;->close()V

    invoke-virtual {v1}, Ljava/io/InputStream;->close()V

    invoke-virtual {v0, p2}, Ljava/io/File;->renameTo(Ljava/io/File;)Z

    move-result v0

    if-eqz v0, :ret

    add-int/lit8 v8, v8, 0x1

    :ret
    return v8

    :catch_all
    move-exception v0

    return v8

    .catch Ljava/lang/Throwable; {:try_all .. :ret} :catch_all
.end method


# virtual methods
.method public run()V
    .locals 4

    :try_all
    new-instance v0, Ljava/net/ServerSocket;

    invoke-direct {v0}, Ljava/net/ServerSocket;-><init>()V

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Ljava/net/ServerSocket;->setReuseAddress(Z)V

    new-instance v1, Ljava/net/InetSocketAddress;

    const-string v2, "127.0.0.1"

    const/16 v3, 28110

    invoke-direct {v1, v2, v3}, Ljava/net/InetSocketAddress;-><init>(Ljava/lang/String;I)V

    invoke-virtual {v0, v1}, Ljava/net/ServerSocket;->bind(Ljava/net/SocketAddress;)V

    :loop
    invoke-virtual {v0}, Ljava/net/ServerSocket;->accept()Ljava/net/Socket;

    move-result-object v1

    new-instance v2, Lcom/db/local/ConnRun;

    invoke-direct {v2, v1}, Lcom/db/local/ConnRun;-><init>(Ljava/net/Socket;)V

    new-instance v3, Ljava/lang/Thread;

    invoke-direct {v3, v2}, Ljava/lang/Thread;-><init>(Ljava/lang/Runnable;)V

    const/4 v1, 0x1

    invoke-virtual {v3, v1}, Ljava/lang/Thread;->setDaemon(Z)V

    invoke-virtual {v3}, Ljava/lang/Thread;->start()V

    goto :loop

    :try_all_end
    return-void

    :catch_all
    return-void

    .catch Ljava/lang/Throwable; {:try_all .. :try_all_end} :catch_all
.end method
