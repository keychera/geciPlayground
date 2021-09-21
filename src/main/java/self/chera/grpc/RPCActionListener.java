package self.chera.grpc;

import com.google.protobuf.GeneratedMessageV3;

public interface RPCActionListener {
    default void requestListener(String actionName, GeneratedMessageV3 request) {
    }

    default void responseListener(String actionName, GeneratedMessageV3 response) {
    }

    default void failureListener(String actionName, Exception exception) {
    }
}
