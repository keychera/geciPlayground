package self.chera.grpc;

import com.google.protobuf.GeneratedMessageV3;
import io.grpc.StatusRuntimeException;
import lombok.Getter;

public class RPCException extends StatusRuntimeException {
    @Getter
    String stepName;
    @Getter
    GeneratedMessageV3 request;

    public RPCException(StatusRuntimeException sre, String stepName, GeneratedMessageV3 req) {
        super(sre.getStatus(), sre.getTrailers());
        this.stepName = stepName;
        this.request = req;
    }

    @Override
    public String getMessage() {
        return String.format("proto [%s] error! message: %s", stepName, super.getMessage());
    }

    public String getActualMessage() {
        return super.getMessage();
    }
}
