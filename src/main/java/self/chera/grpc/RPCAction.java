package self.chera.grpc;

import com.google.protobuf.GeneratedMessageV3;
import java.util.function.Function;

public abstract class RPCAction<Req extends GeneratedMessageV3, Res extends GeneratedMessageV3> {
    private String stepName;

    protected RPCAction() {
        stepName = getClass().getSimpleName();
    }

    public Res exec() {
        return exec(stepName);
    }

    private Res exec(String actionName) {
        Req request = getRequest();
        Function<Req, Res> action = getAction();
        return action.apply(request);
    }

    protected abstract Req getRequest();

    protected abstract Function<Req, Res> getAction();

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }
}
