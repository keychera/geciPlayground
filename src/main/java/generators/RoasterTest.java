package generators;

import com.google.protobuf.GeneratedMessageV3;
import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.Clients;
import io.grpc.MethodDescriptor;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import self.chera.grpc.RPCAction;
import self.chera.proto.CheraHandlerGrpc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RoasterTest {
    private static final String GEN_LOC = "target/generated-sources/rpc-actions";
    private static final String PACKAGE_NAME = "self.chera.generated.grpc";

    public static void main(String[] args) {
        prepareGeneratedFolder();
        String className = "CheraRPC";

        final JavaClassSource rpcActionsClass = Roaster.create(JavaClassSource.class);
        rpcActionsClass.setPackage(PACKAGE_NAME).setName("CheraRPC");

        final JavaClassSource parentRpcAction = Roaster.create(JavaClassSource.class);

        parentRpcAction.setName("CheraRPCAction")
                .setPublic().setStatic(true).setAbstract(true)
                .addTypeVariable("Req").setBounds(GeneratedMessageV3.class)
                .getOrigin()
                .addTypeVariable("Res").setBounds(GeneratedMessageV3.class)
                .getOrigin()
                .setSuperType("RPCAction<Req, Res>")
                .addField().setName("clientBuilder")
                    .setPublic().setType(ClientBuilder.class)
                    .setLiteralInitializer("Clients.builder(\"gproto+https://chera.me\");")
                .getOrigin()
                .addMethod().setName("getClient")
                    .setPublic().setReturnType(CheraHandlerGrpc.CheraHandlerBlockingStub.class)
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

        Class<CheraHandlerGrpc> handler = CheraHandlerGrpc.class;
        List<Field> allMethodDescriptor = Arrays.stream(handler.getDeclaredFields())
                .filter(t -> t.getType().isAssignableFrom(MethodDescriptor.class)).collect(Collectors.toList());
        for (Field declaredField : allMethodDescriptor) {
            ParameterizedType types;
            String serviceName;
            try {
                MethodDescriptor<?, ?> service = (MethodDescriptor<?, ?>) declaredField.get(handler);
                serviceName = service.getBareMethodName();
                types = (ParameterizedType) declaredField.getGenericType();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            }
            assert serviceName != null;
            String requestTypeName = types.getActualTypeArguments()[0].getTypeName().replace("$", ".");
            String responseTypeName = types.getActualTypeArguments()[1].getTypeName().replace("$", ".");
            System.out.printf("Adding service class %s, req: %s res: %s%n", serviceName, requestTypeName,
                    responseTypeName);

            Class<?> requestType = (Class<?>) types.getActualTypeArguments()[0];
            List<Class<?>> buffer =
                    Arrays.stream(requestType.getNestMembers()).filter(c -> {
                        System.out.println(c.getName().replace("$", "."));
                        return c.getName().replace("$", ".").equals(String.format("%s.Builder", requestTypeName));
                    })
                            .collect(Collectors.toList());
            Class<?> builderClass = buffer.get(0);

            final JavaClassSource anRpcActionClass = Roaster.create(JavaClassSource.class);
            anRpcActionClass.setName(serviceName)
                    .setPublic().setStatic(true)
                    .setSuperType(String.format("%s<%s,%s>",parentRpcAction.getName(), requestTypeName,
                            responseTypeName))
                    .addField().setName("requestBuilder")
                        .setPublic().setType(builderClass)
                        .setLiteralInitializer(String.format("%s.newBuilder();", requestTypeName))
                    .getOrigin()
                    .addMethod().setName("getRequest")
                        .setProtected().setReturnType(requestTypeName)
                        .setBody("return requestBuilder.build();")
                    .getOrigin()
                    .addMethod().setName("getAction")
                        .setProtected().setReturnType(String.format("Function<%s,%s>",requestTypeName, responseTypeName))
                        .setBody(String.format("return (req) -> getClient().%s(req);", lowercaseFirstLetter(serviceName)))
                    .getOrigin()
            ;

            rpcActionsClass.addImport(requestTypeName);
            rpcActionsClass.addImport(responseTypeName);
            rpcActionsClass.addImport(builderClass);
            rpcActionsClass.addImport(Function.class);
            rpcActionsClass.addNestedType(anRpcActionClass);
        }

        String filename = String.format("%s/%s.java", GEN_LOC + "/" + PACKAGE_NAME.replace(".", "/"), className);
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
            Files.createDirectories(Path.of(GEN_LOC + "/" + PACKAGE_NAME.replace(".", "/")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String lowercaseFirstLetter(String str) {
        return str.substring(0,1).toLowerCase() + str.substring(1);
    }
}
