package self.chera.grpc;

import com.google.protobuf.GeneratedMessageV3;
import io.grpc.StatusRuntimeException;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import static self.chera.grpc.RPCGlobals.rpcActionListeners;

public abstract class RPCAction<Req extends GeneratedMessageV3, Res extends GeneratedMessageV3> {
    private String stepName;
    public RPCClientBuilder clientBuilder = new RPCClientBuilder();
    public UnaryOperator<String> stepNameModifier = str -> str;

    protected RPCAction() {
        stepName = getClass().getSimpleName();
    }

    public Res exec() {
        stepName = stepNameModifier.apply(stepName);
        return exec(stepName);
    }

    private Res exec(String actionName) {
        Req request = getRequest();
        Function<Req, Res> action = getAction();
        Res response;
        try {
            response = action.apply(request);
        } catch (StatusRuntimeException sre) {
            var exc = new RPCException(sre, actionName, request);
            rpcActionListeners.forEach(listener -> listener.failureListener(actionName, request, exc));
            throw exc;
        }
        rpcActionListeners.forEach(listener -> listener.responseListener(actionName, request, response));
        return response;
    }

    protected abstract Req getRequest();

    protected abstract Function<Req, Res> getAction();

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }
}