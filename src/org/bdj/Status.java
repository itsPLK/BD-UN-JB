package org.bdj;

public class Status {
    private static RemoteLogger LOGGER;
    private static volatile boolean WINDOWBOOL = false;
    private static volatile boolean LOGGERBOOL = false;
    private static int lastPercent = 0;

    public static void setScreenOutputEnabled(boolean windowbool) {
        WINDOWBOOL = windowbool;
    }

    public static void setNetworkLoggerEnabled(boolean networkbool) {
        LOGGERBOOL = networkbool;
    }

    private static synchronized void initLogger() {
        if (LOGGER == null) {
            LOGGER = new RemoteLogger(18194, 1000);
            LOGGER.start();
            try {
                //Give some time for log client
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void close() {
        synchronized (Status.class) {
            if (LOGGER != null) {
                LOGGER.stop();
                LOGGER = null;
            }
        }
    }

    public static void println(String msg) {
        info(msg);
    }

    public static void info(String msg) {
        String finalMsg = msg;
        if (LOGGERBOOL) {
            initLogger();
            LOGGER.println("INFO: " + finalMsg);
        }
        if (WINDOWBOOL) {
            ProgressUI.getInstance().log(finalMsg);
        }
    }

    public static void success(String msg) {
        String finalMsg = msg;
        if (LOGGERBOOL) {
            initLogger();
            LOGGER.println("SUCCESS: " + finalMsg);
        }
        if (WINDOWBOOL) {
            ProgressUI.getInstance().logSuccess(finalMsg);
        }
    }

    public static void warning(String msg) {
        String finalMsg = msg;
        if (LOGGERBOOL) {
            initLogger();
            LOGGER.println("WARNING: " + finalMsg);
        }
        if (WINDOWBOOL) {
            ProgressUI.getInstance().logWarning(finalMsg);
        }
    }

    public static void error(String msg) {
        String finalMsg = msg;
        if (LOGGERBOOL) {
            initLogger();
            LOGGER.println("ERROR: " + finalMsg);
        }
        if (WINDOWBOOL) {
            ProgressUI.getInstance().logError(finalMsg);
            ProgressUI.getInstance().setProgress(lastPercent, "ERROR");
        }
    }

    public static void setProgress(int percent, String label) {
        lastPercent = percent;
        if (WINDOWBOOL) {
            ProgressUI.getInstance().setProgress(percent, label);
        }
    }

    public static void printStackTrace(String msg, Throwable e) {
        String finalMsg = msg;
        if (LOGGERBOOL) {
            initLogger();
            LOGGER.printStackTrace("ERROR: " + finalMsg, e);
        }
        if (WINDOWBOOL) {
            ProgressUI.getInstance().logError(finalMsg);
            ProgressUI.getInstance().setProgress(lastPercent, "ERROR");
            Screen.getInstance().printStackTrace(e);
        }
    }

}