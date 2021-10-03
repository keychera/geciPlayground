package self.chera;

import com.linecorp.armeria.client.Clients;
import io.grpc.ManagedChannelBuilder;
import org.junit.Test;
import self.chera.generated.grpc.TakaraKaneGrpc;
import self.chera.grpc.RPCGlobals;
import self.chera.proto.TakaraKaneHandlerGrpc;
import self.chera.proto.Takarakane;

import java.util.List;

import static self.chera.generated.grpc.TakaraKaneGrpc.takaraKaneGrpc;

public class TakaraKaneTest {
    DummyDecorator dummyDecorator = new DummyDecorator();
    DummyListener dummyListener = new DummyListener();

    @Test
    public void setGlobals() {
        RPCGlobals.defaultUrl = "something";
        RPCGlobals.withDefaultLogToConsole();
        RPCGlobals.registerListener(dummyListener);
    }

    @Test
    public void usingStaticEntrypoint() {
        var response = takaraKaneGrpc()
                .decorator(dummyDecorator)
                .addHeader("authorization", "Bearer exampleToken")
                .addHeader("deviceid", "chera")
                .sailThe(List.of(1L, 2L), List.of("houshou", "marine"), null, null, null, null, null, null, null, null);

        assert response != null;
    }

    @Test
    public void usingCustomClassDeclaration() {
        var getAccessCode = new TakaraKaneGrpc.SailThe();
        getAccessCode.clientBuilder
                .decorator(dummyDecorator)
                .addHeader("authorization", "Bearer exampleToken")
                .addHeader("deviceid", "chera");

        getAccessCode.requestBuilder
                .setId(0, 1L)
                .setId(1, 2L)
                .setName(0, "houshou")
                .setName(0, "marine");
        var response = getAccessCode.exec();

        assert response != null;
    }

    @Test
    public void usingArmeria() {
        var ocean = Takarakane.Ocean.newBuilder()
                .setId(0, 1L)
                .setId(1, 2L)
                .setName(0, "houshou")
                .setName(0, "marine").build();
        var client = Clients.builder(RPCGlobals.defaultUrl)
                .decorator(dummyDecorator)
                .addHeader("authorization", "Bearer exampleToken")
                .addHeader("deviceid", "chera")
                .responseTimeoutMillis(10000)
                .build(TakaraKaneHandlerGrpc.TakaraKaneHandlerBlockingStub.class);
        var response = client.sailThe(ocean);

        assert response != null;
    }

    @Test
    public void usingProtoJava() {
        var channel = ManagedChannelBuilder
                .forAddress(RPCGlobals.defaultUrl, 6565)
                .usePlaintext()
                .build();

        var client = TakaraKaneHandlerGrpc.newBlockingStub(channel);

        var ocean = Takarakane.Ocean.newBuilder()
                .setId(0, 1L)
                .setId(1, 2L)
                .setName(0, "houshou")
                .setName(0, "marine").build();

        // I don't know how to add headers using this method
        var response = client.sailThe(ocean);

        assert response != null;
    }
}
