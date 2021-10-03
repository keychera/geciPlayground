package self.chera.grpc;

import java.util.ArrayList;
import java.util.List;

public class RPCGlobals {
    public static String defaultUrl = "[UNSET]";
    public static final List<RPCActionListener> rpcActionListeners = new ArrayList<>();

    public static void withDefaultLogToConsole() {
        rpcActionListeners.add(new RPCDebuggingListener());
    }

    public static void registerListener(RPCActionListener listener) {
        rpcActionListeners.add(listener);
    }
}
