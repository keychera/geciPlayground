package self.chera.grpc;

import com.google.protobuf.GeneratedMessageV3;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.awaitility.Awaitility.await;

public abstract class RPCStream<Req extends GeneratedMessageV3, Res extends GeneratedMessageV3> {
    public RPCClientBuilder clientBuilder = new RPCClientBuilder();

    private final AtomicBoolean streamComplete = new AtomicBoolean(false);
    private boolean isReady = false;
    private StreamObserver<Req> requestStream;
    private Res response;

    final StreamObserver<Res> responseStream = new StreamObserver<>() {
        @Override
        public void onNext(Res res) {
            response = res;
        }

        @Override
        public void onError(Throwable throwable) {
            // Should never reach here.
            throw new Error(throwable);
        }

        @Override
        public void onCompleted() {
            streamComplete.set(true);
        }
    };

    public void prepareStream() {
        requestStream = getRequestStreamAction().apply(responseStream);
        streamComplete.set(false);
        isReady = true;
    }

    public void supplyRequestToStream() {
        if (isReady) {
            requestStream.onNext(getRequest());
        } else {
            throw new RuntimeException("The stream has not been prepared, run `prepareStream()` first!");
        }
    }

    public Res completeStream() {
        if (isReady) {
            requestStream.onCompleted();
            await().untilTrue(streamComplete);
            isReady = false;
            if (streamComplete.get()) {
                return response;
            } else {
                throw new RuntimeException("The stream is not complete somehow!");
            }
        } else {
            throw new RuntimeException("The stream has not been prepared, run `prepareStream()` first!");
        }
    }

    protected abstract Req getRequest();

    protected abstract Function<StreamObserver<Res>, StreamObserver<Req>> getRequestStreamAction();

}
