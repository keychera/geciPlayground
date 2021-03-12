package self.chera.grpc;

import javax0.geci.api.Source;
import javax0.geci.tools.AbstractJavaGenerator;
import javax0.geci.tools.CompoundParams;

public class RPCGenerator extends AbstractJavaGenerator {
    @Override
    public void process(Source source, Class<?> aClass, CompoundParams compoundParams) throws Exception {
        try(var segment = source.open("ParentRPCAction")){
            segment.write_r("public static abstract class CheraRPCAction<Req extends GeneratedMessageV3, Res extends GeneratedMessageV3> extends RPCAction<Req, Res> {");
            segment.write(
                    "public ClientBuilder clientBuilder = Clients.builder(\"gproto+https://chera.me\");\n" + "\n"
                    + "public CheraHandlerBlockingStub getClient() {\n"
                    + "    CheraHandlerBlockingStub client = clientBuilder.build(CheraHandlerBlockingStub.class);\n"
                    + "    clientBuilder = Clients.builder(\"gproto+https://chera.me\");\n"
                    + "    return client;\n" + "}");
            segment.write_l("}");
        }
    }

    @Override
    public String mnemonic() {
        return "RPCGenerator";
    }
}
