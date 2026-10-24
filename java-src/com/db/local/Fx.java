package com.db.local;

public final class Fx {

    private Fx() {
    }

    public static String sdkStatic(String str) {
        try {
            if (str == null) {
                return Db.okGeneric();
            }
            String path;
            if (str.contains("v1/user/ckregvistor")) {
                path = "qsdk/v1/user/ckRegVistor";
            } else if (str.contains("v1/user/getserveragreement")) {
                path = "qsdk/v1/user/getServerAgreement";
            } else if (str.contains("v1/system/getserveragreement")) {
                path = "qsdk/v1/user/getServerAgreement";
            } else if (str.contains("v1/system/init")) {
                path = "qsdk/v1/system/init";
            } else if (str.contains("v1/system/getnotice")) {
                path = "qsdk/v1/system/getNotice";
            } else if (str.contains("v1/system/dmsg")) {
                path = "qsdk/v1/system/dmsg";
            } else if (str.contains("v1/system/onnewtoken")) {
                path = "qsdk/v1/system/onNewToken";
            } else if (str.contains("v1/system/getagreement")) {
                path = "qsdk/v1/system/getAgreement";
            } else {
                return Db.okGeneric();
            }
            String tpl = Db.template(path);
            if (tpl != null && tpl.length() >= 5) {
                return tpl;
            }
            return Db.okGeneric();
        } catch (Throwable t) {
            return Db.okGeneric();
        }
    }
}
