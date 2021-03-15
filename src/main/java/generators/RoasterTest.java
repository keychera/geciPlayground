package generators;

import com.google.protobuf.GeneratedMessageV3;
import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.Clients;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaClass;
import org.jboss.forge.roaster.model.source.ExtendableSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.TypeVariableSource;
import self.chera.grpc.RPCAction;
import self.chera.proto.CheraHandlerGrpc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

public class RoasterTest {
    private static final String GEN_LOC = "target/generated-sources/rpc-actions";

    public static void main(String[] args) {
        prepareGeneratedFolder();
        String className = "CheraRPC";

        final JavaClassSource rpcActionsClass = Roaster.create(JavaClassSource.class);
        rpcActionsClass.setPackage("generated.grpc").setName("CheraRPC");

        final JavaClassSource parentRpcAction = Roaster.create(JavaClassSource.class);

        parentRpcAction.setName("CheraRPCAction")
                .setPublic().setStatic(true).setAbstract(true)
                .addTypeVariable("Req").setBounds(GeneratedMessageV3.class)
                .getOrigin()
                .addTypeVariable("Res").setBounds(GeneratedMessageV3.class)
                .getOrigin()
                .setSuperType("RPCAction<Req, Res>")
                .addField()
                    .setPublic().setType(ClientBuilder.class).setName("clientBuilder")
                    .setLiteralInitializer("Clients.builder(\"gproto+https://chera.me\");")
                .getOrigin()
                .addMethod()
                    .setPublic().setReturnType(CheraHandlerGrpc.CheraHandlerBlockingStub.class).setName("getClient")
                    .setBody("CheraHandlerBlockingStub client = clientBuilder.build(CheraHandlerBlockingStub.class);\n"
                            + "            clientBuilder = Clients.builder(\"gproto+https://chera.me\");\n"
                            + "            return client;")
        ;

        rpcActionsClass.addImport(GeneratedMessageV3.class);
        rpcActionsClass.addImport(RPCAction.class);
        rpcActionsClass.addImport(ClientBuilder.class);
        rpcActionsClass.addImport(Clients.class);
        rpcActionsClass.addImport(CheraHandlerGrpc.CheraHandlerBlockingStub.class);
        rpcActionsClass.addNestedType(parentRpcAction);

        String filename = String.format("%s/%s.java", GEN_LOC, className);
        try {
            File myObj = new File(filename);
            if (myObj.createNewFile()) {
                System.out.println("File created: " + myObj.getName());
            } else {
                System.out.println("File already exists.");
            }
            FileWriter myWriter = new FileWriter(filename);
            myWriter.write(Roaster.format(rpcActionsClass.toString()));
            myWriter.close();
            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static void prepareGeneratedFolder() {
        try {
            Files.createDirectories(Path.of(GEN_LOC));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
