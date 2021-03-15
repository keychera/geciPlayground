package self.chera.grpc;

import com.google.protobuf.GeneratedMessageV3;
import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.Clients;
import self.chera.proto.Chera;
import self.chera.proto.CheraHandlerGrpc.CheraHandlerBlockingStub;

import java.util.function.Function;

public class CheraManualRPC {
    public static abstract class CheraRPCAction<Req extends GeneratedMessageV3, Res extends GeneratedMessageV3> extends RPCAction<Req, Res> {
        public ClientBuilder clientBuilder = Clients.builder("gproto+https://chera.me");

        public CheraHandlerBlockingStub getClient() {
            CheraHandlerBlockingStub client = clientBuilder.build(CheraHandlerBlockingStub.class);
            clientBuilder = Clients.builder("gproto+https://chera.me");
            return client;
        }

    }

    public static class CreateUniverse extends CheraRPCAction<Chera.UniverseRequest, Chera.UniverseResponse>{
        public Chera.UniverseRequest.Builder requestBuilder = Chera.UniverseRequest.newBuilder();

        @Override
        protected Chera.UniverseRequest getRequest() {
            return requestBuilder.build();
        }

        @Override
        protected Function<Chera.UniverseRequest, Chera.UniverseResponse> getAction() {
            return (req) -> getClient().createUniverse(req);
        }
    }

    public static class DestroyUniverse extends CheraRPCAction<Chera.UniverseRequest, Chera.UniverseResponse>{
        public Chera.UniverseRequest.Builder requestBuilder = Chera.UniverseRequest.newBuilder();

        @Override
        protected Chera.UniverseRequest getRequest() {
            return requestBuilder.build();
        }

        @Override
        protected Function<Chera.UniverseRequest, Chera.UniverseResponse> getAction() {
            return (req) -> getClient().destroyUniverse(req);
        }
    }
}
