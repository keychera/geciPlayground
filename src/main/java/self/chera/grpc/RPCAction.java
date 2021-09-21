package self.chera.grpc;

import com.google.protobuf.GeneratedMessageV3;
import io.grpc.StatusRuntimeException;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public abstract class RPCAction<Req extends GeneratedMessageV3, Res extends GeneratedMessageV3> {
    private static List<RPCActionListener> rpcActionListeners = null;
    private String stepName;
    public UnaryOperator<String> stepNameModifier = str -> str;

    protected RPCAction() {
        if (rpcActionListeners == null) {
            rpcActionListeners = new ArrayList<>();
            var rpcActionListenerServiceLoader = ServiceLoader.load(RPCActionListener.class);
            rpcActionListenerServiceLoader.iterator().forEachRemaining(listener -> rpcActionListeners.add(listener));
        }
        stepName = getClass().getSimpleName();
    }

    public Res exec() {
        stepName = stepNameModifier.apply(stepName);
        return exec(stepName);
    }

    private Res exec(String actionName) {
        Req request = getRequest();
        rpcActionListeners.forEach(listener -> listener.requestListener(actionName, request));

        Function<Req, Res> action = getAction();
        Res response;
        try {
            response = action.apply(request);
        } catch (StatusRuntimeException sre) {
            rpcActionListeners.forEach(listener -> listener.failureListener(actionName, sre));
            throw new RPCException(sre, actionName, request);
        }
        Res finalResponse = response;
        rpcActionListeners.forEach(listener -> listener.responseListener(actionName, finalResponse));

        return response;
    }

    protected abstract Req getRequest();

    protected abstract Function<Req, Res> getAction();

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }
}