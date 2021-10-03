package self.chera.grpc;

import com.google.protobuf.GeneratedMessageV3;

public interface RPCActionListener {
    default void responseListener(String actionName, GeneratedMessageV3 request, GeneratedMessageV3 response) {
    }

    default void failureListener(String actionName, GeneratedMessageV3 request, RPCException exception) {
    }

    default void addHeaderListener(CharSequence name, Object value) {
    }

    default void setHeaderListener(CharSequence name, Object value) {
    }
}
