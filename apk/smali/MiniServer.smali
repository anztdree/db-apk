.class public Lcom/db/local/MiniServer;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;
.source "MiniServer.java"


# static fields
.field private static APP:Landroid/content/Context;

.field private static started:Z


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static handle(Ljava/net/Socket;)V
    .locals 6

    :try_all
    const/4 v1, 0x0

    const/16 v0, 0x1f40

    invoke-virtual {p0, v0}, Ljava/net/Socket;->setSoTimeout(I)V

    invoke-virtual {p0}, Ljava/net/Socket;->getInputStream()Ljava/io/InputStream;

    move-result-object v0

    invoke-static {v0}, Lcom/db/local/MiniServer;->readLine(Ljava/io/InputStream;)Ljava/lang/String;

    move-result-object v0

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

    if-eqz v1, :assets_only

    new-instance v2, Ljava/io/File;

    const-string v3, "mirror"

    invoke-direct {v2, v1, v3}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    new-instance v1, Ljava/io/File;

    invoke-direct {v1, v2, p0}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    invoke-virtual {v1}, Ljava/io/File;->isFile()Z

    move-result v2

    if-eqz v2, :assets_only

    new-instance v2, Ljava/io/FileInputStream;

    invoke-direct {v2, v1}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V

    invoke-static {v2}, Lcom/db/local/MiniServer;->readAll(Ljava/io/InputStream;)[B

    move-result-object v0

    invoke-virtual {v2}, Ljava/io/FileInputStream;->close()V

    return-object v0

    :assets_only
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
