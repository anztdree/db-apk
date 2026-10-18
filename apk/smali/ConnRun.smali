.class public Lcom/db/local/ConnRun;
.super Ljava/lang/Object;
.implements Ljava/lang/Runnable;
.source "ConnRun.java"


# instance fields
.field private final s:Ljava/net/Socket;


# direct methods
.method public constructor <init>(Ljava/net/Socket;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lcom/db/local/ConnRun;->s:Ljava/net/Socket;

    return-void
.end method


# virtual methods
.method public run()V
    .locals 1

    :try_all
    iget-object v0, p0, Lcom/db/local/ConnRun;->s:Ljava/net/Socket;

    invoke-static {v0}, Lcom/db/local/MiniServer;->handle(Ljava/net/Socket;)V

    :try_all_end
    return-void

    :catch_all
    return-void

    .catch Ljava/lang/Throwable; {:try_all .. :try_all_end} :catch_all
.end method
