package generators;

import com.google.protobuf.GeneratedMessageV3;
import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.Clients;
import io.grpc.MethodDescriptor;
import lombok.Builder;
import lombok.SneakyThrows;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import self.chera.grpc.RPCAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RPCActionsGenerator {
    private static final String GEN_LOC = "target/generated-sources/rpc-actions";
    private static final String PACKAGE_NAME = "self.chera.generated.grpc";
    private static final String PROTO_URL = "gproto+https://square.me";
    private static final String DOT = "_dot_";
    private static String packageFolderName;

    @Builder
    private static class HandlerWrapper {
        private final Class<?> handlerGrpcClass;
        private final Class<?> blockingStubClass;
        private final Class<?> stubClass;
    }

    public static void main(String[] args) {
        prepareGeneratedFolder();

        Map<String, HandlerWrapper.HandlerWrapperBuilder> listOfHandler = getListOfProtoHandler();
        listOfHandler.forEach((handlerName, wrapperBuilder) -> {
            HandlerWrapper wrapper = wrapperBuilder.build();
            String className = handlerName.replace("Handler", "Grpc");

            final JavaClassSource rpcActionsClass = Roaster.create(JavaClassSource.class);
            rpcActionsClass.setPackage(PACKAGE_NAME).setName(className);

            final JavaClassSource parentRpcAction = Roaster.create(JavaClassSource.class);

            // Parent RPC Action class
            parentRpcAction.setName("CheraRPCAction")
                    .setPublic().setStatic(true).setAbstract(true)
                    .addTypeVariable("Req").setBounds(GeneratedMessageV3.class)
                    .getOrigin()
                    .addTypeVariable("Res").setBounds(GeneratedMessageV3.class)
                    .getOrigin()
                    .setSuperType("RPCAction<Req, Res>")
                    .addField().setName("clientBuilder")
                        .setPublic().setType(ClientBuilder.class)
                        .setLiteralInitializer(String.format("Clients.builder(\"%s\");", PROTO_URL))
                    .getOrigin()
                    .addMethod().setName("getClient")
                        .setPublic().setReturnType(wrapper.blockingStubClass)
                        .setBody(String.format("%1$s client = clientBuilder.build"
                                + "(%1$s.class);\n"
                                + "clientBuilder = Clients.builder(\"%2$s\");\n"
                                + "return client;",
                                wrapper.blockingStubClass.getSimpleName(), PROTO_URL))
            ;

            rpcActionsClass.addImport(GeneratedMessageV3.class);
            rpcActionsClass.addImport(RPCAction.class);
            rpcActionsClass.addImport(ClientBuilder.class);
            rpcActionsClass.addImport(Clients.class);
            rpcActionsClass.addImport(wrapper.blockingStubClass);
            rpcActionsClass.addNestedType(parentRpcAction);



            // each grpc service class
            Class<?> handler = wrapper.handlerGrpcClass;
            List<Field> allMethodDescriptor = Arrays.stream(handler.getDeclaredFields())
                    .filter(t -> t.getType().isAssignableFrom(MethodDescriptor.class)).collect(Collectors.toList());
            for (Field declaredField : allMethodDescriptor) {
                ParameterizedType types;
                String serviceName;
                MethodDescriptor.MethodType serviceType;
                try {
                    MethodDescriptor<?, ?> service = (MethodDescriptor<?, ?>) declaredField.get(handler);
                    serviceType = service.getType();
                    serviceName = service.getBareMethodName();
                    types = (ParameterizedType) declaredField.getGenericType();
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e.getMessage());
                }
                assert serviceName != null;
                Class<?> protoClass = ((Class<?>) types.getActualTypeArguments()[0]).getEnclosingClass();
                String protoPackage = protoClass.getPackageName();
                String originalRequestTypeName = types.getActualTypeArguments()[0].getTypeName().replace("$", ".");
                String originalResponseTypeName = types.getActualTypeArguments()[1].getTypeName().replace("$", ".");

                System.out.printf("Adding service (%s) class %s, req: %s res: %s%n", serviceType.name() , serviceName, originalRequestTypeName, originalResponseTypeName);
                switch (serviceType) {
                    case UNARY:
                        String requestTypeName =
                                getNotSoSimpleName(originalRequestTypeName.replace(protoPackage + ".", ""));
                        String responseTypeName =
                                getNotSoSimpleName(originalResponseTypeName.replace(protoPackage + ".", ""));

                        Class<?> requestType = (Class<?>) types.getActualTypeArguments()[0];
                        List<Class<?>> buffer =
                                Arrays.stream(requestType.getNestMembers()).filter(c -> c.getName().replace("$", ".").equals(String.format("%s.Builder", originalRequestTypeName)))
                                        .collect(Collectors.toList());
                        Class<?> builderClass = buffer.get(0);
                        String builderTypeName = getNotSoSimpleName(builderClass.getCanonicalName().replace(protoPackage + ".", ""));

                        final JavaClassSource anRpcActionClass = Roaster.create(JavaClassSource.class);
                        anRpcActionClass.setName(serviceName)
                                .setPublic().setStatic(true)
                                .setSuperType(String.format("%s<%s,%s>",parentRpcAction.getName(), requestTypeName, responseTypeName))
                                .addField().setName("requestBuilder")
                                .setPublic().setType(builderTypeName)
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

                        rpcActionsClass.addImport(protoClass);
                        rpcActionsClass.addImport(Function.class);
                        rpcActionsClass.addNestedType(anRpcActionClass);
                        break;
                    case CLIENT_STREAMING:
                        System.out.println("Client streaming generation not implemented yet");
                        break;
                    default:
                        System.out.println("not implemented yet");
                        break;
                }
            }

            String sourceCode = rpcActionsClass.toString().replace(DOT, ".");
            String filename = String.format("%s/%s.java", packageFolderName, className);
            try {
                File myObj = new File(filename);
                if (myObj.createNewFile()) {
                    System.out.println("File created: " + myObj.getName());
                } else {
                    System.out.println("File already exists.");
                }
                FileWriter myWriter = new FileWriter(filename);
                myWriter.write(Roaster.format(sourceCode));
                myWriter.close();
                System.out.println("Successfully wrote to the file.");
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
        });
    }

    @SneakyThrows
    private static Map<String, HandlerWrapper.HandlerWrapperBuilder> getListOfProtoHandler() {
        String packageName = "self.chera.proto";
        Map<String, HandlerWrapper.HandlerWrapperBuilder> handlers = new HashMap<>();
        URL root = Thread.currentThread().getContextClassLoader().getResource(packageName.replace(".", "/"));

        // Filter .class files.
        File[] files = new File(root.getFile()).listFiles((dir, name) -> name.endsWith(".class"));

        // Find the classes
        for (File file : files) {
            String className = file.getName().replaceAll(".class$", "");
            Matcher m = Pattern.compile("(.*)Grpc.*").matcher(className);
            if (m.matches()) {
                String handlerName = m.group(1);
                if (!handlers.containsKey(handlerName)) {
                    handlers.put(handlerName, HandlerWrapper.builder());
                }
                if (className.matches(".*Grpc$")) {
                    Class<?> cls = Class.forName(packageName + "." + className);
                    handlers.get(handlerName).handlerGrpcClass(cls);
                } else if (className.matches(".*Grpc.*BlockingStub")) {
                    Class<?> cls = Class.forName(packageName + "." + className);
                    handlers.get(handlerName).blockingStubClass(cls);
                } else if (className.matches(".*Grpc.*FutureStub")) {
                    // do nothing
                } else if (className.matches(".*Grpc.*Stub")){
                    Class<?> cls = Class.forName(packageName + "." + className);
                    handlers.get(handlerName).stubClass(cls);
                }
            }
        }
        return handlers;
    }

    private static void prepareGeneratedFolder() {
        try {
            packageFolderName = GEN_LOC + "/" + PACKAGE_NAME.replace(".", "/");
            Files.createDirectories(Path.of(packageFolderName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String lowercaseFirstLetter(String str) {
        return str.substring(0,1).toLowerCase() + str.substring(1);
    }

    private static String getNotSoSimpleName (String str) {
        return str.replace(".", DOT);
    }
}
