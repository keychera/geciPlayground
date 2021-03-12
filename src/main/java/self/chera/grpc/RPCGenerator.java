package self.chera.grpc;

import com.google.protobuf.GeneratedMessageV3;
import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.Clients;
import javax0.geci.api.Source;
import javax0.geci.tools.AbstractJavaGenerator;
import javax0.geci.tools.CompoundParams;
import self.chera.proto.CheraHandlerGrpc;

public class RPCGenerator extends AbstractJavaGenerator {
    @Override
    public void process(Source source, Class<?> aClass, CompoundParams compoundParams) throws Exception {
        try (var segment = source.open("ParentRPCAction")) {
            Class<GeneratedMessageV3> payloadClass = GeneratedMessageV3.class;
            Class<Clients> clientClass = Clients.class;
            Class<ClientBuilder> clientBuilderClass = ClientBuilder.class;
            Class<CheraHandlerGrpc.CheraHandlerBlockingStub> stubClass =
                    CheraHandlerGrpc.CheraHandlerBlockingStub.class;
            segment.write_r(String.format(
                    "public static abstract class CheraRPCAction<Req extends %1$s, Res extends %1$s> extends RPCAction<Req, Res> {",
                    payloadClass.getCanonicalName()));
            segment.write(String.format(
                    "public %1$s clientBuilder = %2$s.builder(\"gproto+https://chera.me\");\n" + "\n"
                    + "public %3$s getClient() {\n"
                    + "    %3$s client = clientBuilder.build(%3$s.class);\n"
                    + "    clientBuilder = %2$s.builder(\"gproto+https://chera.me\");\n"
                    + "    return client;\n" + "}",
                        clientBuilderClass.getCanonicalName(), clientClass.getCanonicalName(), stubClass.getCanonicalName()
                    ));
            segment.write_l("}");
        }
    }

    @Override
    public String mnemonic() {
        return "RPCGenerator";
    }
}
