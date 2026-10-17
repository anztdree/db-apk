

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/* loaded from: classes.dex */
public class Decryptor {
    public static void main(String[] args) throws Exception {
        byte[] in = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0]));
        byte[] out = new Decryptor().nationalcommunitymissing(in);
        java.nio.file.Files.write(java.nio.file.Path.of(args[1]), out);
        System.out.println("OK -> " + args[1] + " (" + out.length + " bytes)");
    }

    public final long[] cointhreat(byte[] bArr, boolean z) {
        int length = bArr.length;
        long[] jArr = new long[length < 4 ? 2 : (length >> 2) + (z ? 1 : 0)];
        for (int i = 0; i < length; i++) {
            int i2 = i >>> 2;
            jArr[i2] = jArr[i2] | ((bArr[i] & 255) << ((i & 3) << 3));
        }
        if (z) {
            int i3 = 4 - (length & 3);
            if (length < 4) {
                i3 += 4;
            }
            for (int i4 = length; i4 < length + i3; i4++) {
                int i5 = i4 >>> 2;
                jArr[i5] = jArr[i5] | (i3 << ((i4 & 3) << 3));
            }
        }
        return jArr;
    }

    public byte[] nationalcommunitymissing(byte[] bArr) {
        try {
            if (!new String(Arrays.copyOfRange(bArr, 16, 32), StandardCharsets.UTF_8).equals("onepieceencrypts")) {
                return bArr;
            }
            long[] cointhreat2 = cointhreat(Arrays.copyOfRange(bArr, 48, bArr.length), false);
            byte[] copyOfRange = Arrays.copyOfRange(bArr, 32, 48);
            try {
                byte[] bytes = "onepiecedecryptk".getBytes(StandardCharsets.UTF_8);
                int length = bytes.length;
                for (int i = 0; i < copyOfRange.length; i++) {
                    copyOfRange[i] = (byte) (copyOfRange[i] ^ bytes[i % length]);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            long[] cointhreat3 = cointhreat(copyOfRange, false);
            long j = 1 * 2654435769L;
            long j2 = (j >>> 2) & 3;
            long length2 = cointhreat2.length - 1;
            long j3 = cointhreat2[0];
            long j4 = length2;
            while (j4 > 0) {
                long j5 = j4 - 1;
                long j6 = j4;
                long[] jArr = cointhreat2;
                int i2 = (int) j6;
                j3 = jArr[i2] - singersmooth(j, j3, cointhreat2[(int) j5], j6, j2, cointhreat3);
                jArr[i2] = j3;
                length2 = length2;
                cointhreat2 = jArr;
                j4 = j5;
            }
            long j7 = length2;
            long[] jArr2 = cointhreat2;
            jArr2[0] = jArr2[0] - singersmooth(j, j3, jArr2[(int) j7], j4, j2, cointhreat3);
            int length3 = jArr2.length;
            int i3 = length3 * 4;
            byte[] bArr2 = new byte[i3];
            for (int i4 = 0; i4 < length3; i4++) {
                int i5 = i4 * 4;
                bArr2[i5] = (byte) (jArr2[i4] & 255);
                bArr2[i5 + 1] = (byte) ((jArr2[i4] >>> 8) & 255);
                bArr2[i5 + 2] = (byte) ((jArr2[i4] >>> 16) & 255);
                bArr2[i5 + 3] = (byte) (255 & (jArr2[i4] >>> 24));
            }
            return Arrays.copyOfRange(bArr2, 0, i3 - bArr2[i3 - 1]);
        } catch (Exception e2) {
            e2.printStackTrace();
            return bArr;
        }
    }

    public long seventygenom(int i) {
        return i & 4294967295L;
    }

    public final long singersmooth(long j, long j2, long j3, long j4, long j5, long[] jArr) {
        int i = (int) j3;
        int i2 = (int) j2;
        return (((int) (j2 ^ j)) + seventygenom((int) (j3 ^ jArr[(int) ((j4 & 3) ^ j5)]))) ^ (seventygenom((i >>> 5) ^ (i2 << 2)) + seventygenom((i << 4) ^ (i2 >>> 3)));
    }
}
