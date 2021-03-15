package generators;

import com.google.protobuf.GeneratedMessageV3;
import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.Clients;
import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;
import lombok.Builder;
import lombok.SneakyThrows;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import self.chera.grpc.RPCAction;
import self.chera.grpc.RPCStream;

import java.io.*;
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
    private static final String GENERATED_TARGET_LOCATION = "target/generated-sources/rpc-actions";
    private static final String GENERATED_PACKAGE = "self.chera.generated.grpc";
    private static final String PROTO_URL = "gproto+https://square.me";
    private static final String DOT = "_dot_";
    private static final String UNARY_PARENT_CLASS = "UnaryRPCAction";
    private static final String CLIENT_STREAM_PARENT_CLASS = "ClientStreamRPCAction";
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
            rpcActionsClass.setPackage(GENERATED_PACKAGE).setName(className);

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
                Class<?> requestType = (Class<?>) types.getActualTypeArguments()[0];
                Class<?> responseType = (Class<?>) types.getActualTypeArguments()[1];
                String requestTypeName = requestType.getTypeName().replace("$", ".");
                String responseTypeName = responseType.getTypeName().replace("$", ".");
                System.out.printf("Adding service (%s) class %s, req: %s res: %s%n", serviceType.name(), serviceName,
                        requestTypeName, responseTypeName);

                rpcActionsClass.addImport(protoClass);
                rpcActionsClass.addImport(Function.class);

                String dot_requestTypeName, dot_responseTypeName, dot_builderTypeName;
                switch (serviceType) {
                    case UNARY:
                        addUnaryParent(rpcActionsClass, wrapper);
                        dot_requestTypeName = getDotSimpleName(requestTypeName.replace(protoPackage + ".", ""));
                        dot_responseTypeName = getDotSimpleName(responseTypeName.replace(protoPackage + ".", ""));
                        dot_builderTypeName = getDotBuilderTypeName(protoPackage, requestType, requestTypeName);

                        final JavaClassSource unaryActionClass = Roaster.create(JavaClassSource.class);
                        unaryActionClass.setName(serviceName).setPublic().setStatic(true).setSuperType(
                                String.format("%s<%s,%s>", UNARY_PARENT_CLASS, dot_requestTypeName,
                                        dot_responseTypeName)).addField().setName("requestBuilder").setPublic()
                                .setType(dot_builderTypeName)
                                .setLiteralInitializer(String.format("%s.newBuilder();", dot_requestTypeName))
                                .getOrigin().addMethod().setName("getRequest").setProtected()
                                .setReturnType(dot_requestTypeName).setBody("return requestBuilder.build();")
                                .getOrigin().addMethod().setName("getAction").setProtected().setReturnType(
                                String.format("Function<%s,%s>", dot_requestTypeName, dot_responseTypeName)).setBody(
                                String.format("return (req) -> getClient().%s(req);",
                                        lowercaseFirstLetter(serviceName))).getOrigin();

                        rpcActionsClass.addNestedType(unaryActionClass);
                        break;
                    case CLIENT_STREAMING:
                        addClientStreamParent(rpcActionsClass, wrapper);
                        dot_requestTypeName = getDotSimpleName(requestTypeName.replace(protoPackage + ".", ""));
                        dot_responseTypeName = getDotSimpleName(responseTypeName.replace(protoPackage + ".", ""));
                        dot_builderTypeName = getDotBuilderTypeName(protoPackage, requestType, requestTypeName);

                        final JavaClassSource clientStreamActionClass = Roaster.create(JavaClassSource.class);
                        clientStreamActionClass.setName(serviceName).setPublic().setStatic(true).setSuperType(
                                String.format("%s<%s,%s>", CLIENT_STREAM_PARENT_CLASS, dot_requestTypeName,
                                        dot_responseTypeName)).addField().setName("requestBuilder").setPublic()
                                .setType(dot_builderTypeName)
                                .setLiteralInitializer(String.format("%s.newBuilder();", dot_requestTypeName))
                                .getOrigin().addMethod().setName("getRequest").setProtected()
                                .setReturnType(dot_requestTypeName)
                                .setBody("var req = requestBuilder.build(); requestBuilder.clear(); return req;")
                                .getOrigin().addMethod().setName("getRequestStreamAction").setProtected().setReturnType(
                                String.format("Function<StreamObserver<%s>,StreamObserver<%s>>", dot_responseTypeName,
                                        // switched response and request position for ClientStream
                                        dot_requestTypeName)).setBody(
                                String.format("return (resStream) -> getClient().%s(resStream);",
                                        lowercaseFirstLetter(serviceName))).getOrigin();

                        rpcActionsClass.addImport(StreamObserver.class);
                        rpcActionsClass.addNestedType(clientStreamActionClass);
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

    private static void addUnaryParent(JavaClassSource targetSource, HandlerWrapper wrapper) {
        if (!targetSource.hasNestedType(UNARY_PARENT_CLASS)) {
            // Parent RPC Action class
            final JavaClassSource parentRpcAction = Roaster.create(JavaClassSource.class);

            parentRpcAction.setName(UNARY_PARENT_CLASS).setPrivate().setStatic(true).setAbstract(true)
                    .addTypeVariable("Req").setBounds(GeneratedMessageV3.class).getOrigin().addTypeVariable("Res")
                    .setBounds(GeneratedMessageV3.class).getOrigin().setSuperType("RPCAction<Req, Res>").addField()
                    .setName("clientBuilder").setPublic().setType(ClientBuilder.class)
                    .setLiteralInitializer(String.format("Clients.builder(\"%s\");", PROTO_URL)).getOrigin().addMethod()
                    .setName("getClient").setPublic().setReturnType(wrapper.blockingStubClass).setBody(
                    String.format("return clientBuilder.build(%1$s.class);",
                            wrapper.blockingStubClass.getSimpleName()));

            targetSource.addImport(GeneratedMessageV3.class);
            targetSource.addImport(RPCAction.class);
            targetSource.addImport(ClientBuilder.class);
            targetSource.addImport(Clients.class);
            targetSource.addImport(wrapper.blockingStubClass);
            targetSource.addNestedType(parentRpcAction);
        }
    }

    private static void addClientStreamParent(JavaClassSource targetSource, HandlerWrapper wrapper) {
        if (!targetSource.hasNestedType(CLIENT_STREAM_PARENT_CLASS)) {
            // Parent RPC Action class
            final JavaClassSource parentRpcAction = Roaster.create(JavaClassSource.class);

            parentRpcAction.setName(CLIENT_STREAM_PARENT_CLASS).setPrivate().setStatic(true).setAbstract(true)
                    .addTypeVariable("Req").setBounds(GeneratedMessageV3.class).getOrigin().addTypeVariable("Res")
                    .setBounds(GeneratedMessageV3.class).getOrigin().setSuperType("RPCStream<Req, Res>").addField()
                    .setName("clientBuilder").setPublic().setType(ClientBuilder.class)
                    .setLiteralInitializer(String.format("Clients.builder(\"%s\");", PROTO_URL)).getOrigin().addMethod()
                    .setName("getClient").setPublic().setReturnType(wrapper.stubClass).setBody(
                    String.format("return clientBuilder.responseTimeoutMillis(10000).build(%s.class);",
                            wrapper.stubClass.getSimpleName()));

            targetSource.addImport(GeneratedMessageV3.class);
            targetSource.addImport(RPCStream.class);
            targetSource.addImport(ClientBuilder.class);
            targetSource.addImport(Clients.class);
            targetSource.addImport(wrapper.stubClass);
            targetSource.addNestedType(parentRpcAction);
        }
    }

    @SneakyThrows
    private static Map<String, HandlerWrapper.HandlerWrapperBuilder> getListOfProtoHandler() {
        Map<String, HandlerWrapper.HandlerWrapperBuilder> handlers = new HashMap<>();
        List<String> allProtoPackages = new ArrayList<>();

        URL protoFolder = Thread.currentThread().getContextClassLoader().getResource("protoFiles");

        assert protoFolder != null;
        File[] protoFiles = new File(protoFolder.getFile()).listFiles((dir, name) -> name.endsWith(".proto"));
        // Find the classes

        assert protoFiles != null;
        for (File file : protoFiles) {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line, javaPackage = null;
            while ((line = br.readLine()) != null && javaPackage == null) {
                Matcher m = Pattern.compile("option *java_package *= *\"(.*)\";").matcher(line);
                if (m.matches()) {
                    javaPackage = m.group(1);
                }
            }
            allProtoPackages.add(javaPackage);
        }

        allProtoPackages.forEach(packageSource -> {
            URL root = Thread.currentThread().getContextClassLoader().getResource(packageSource.replace(".", "/"));

            // Filter .class files.
            assert root != null;
            File[] files = new File(root.getFile()).listFiles((dir, name) -> name.endsWith(".class"));

            // Find the classes
            assert files != null;
            for (File file : files) {
                String className = file.getName().replaceAll(".class$", "");
                Matcher m = Pattern.compile("(.*)Grpc.*").matcher(className);
                if (m.matches()) {
                    String handlerName = m.group(1);
                    if (!handlers.containsKey(handlerName)) {
                        handlers.put(handlerName, HandlerWrapper.builder());
                    }
                    try {
                        if (className.matches(".*Grpc$")) {
                            Class<?> cls = Class.forName(packageSource + "." + className);
                            handlers.get(handlerName).handlerGrpcClass(cls);
                        } else if (className.matches(".*Grpc.*BlockingStub")) {
                            Class<?> cls = Class.forName(packageSource + "." + className);
                            handlers.get(handlerName).blockingStubClass(cls);
                        } else if (className.matches(".*Grpc.*FutureStub")) {
                            // do nothing
                        } else if (className.matches(".*Grpc.*Stub")) {
                            Class<?> cls = Class.forName(packageSource + "." + className);
                            handlers.get(handlerName).stubClass(cls);
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e.getMessage());
                    }
                }
            }
        });
        return handlers;
    }

    private static void prepareGeneratedFolder() {
        try {
            packageFolderName = GENERATED_TARGET_LOCATION + "/" + GENERATED_PACKAGE.replace(".", "/");
            Files.createDirectories(Path.of(packageFolderName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String lowercaseFirstLetter(String str) {
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }

    private static String getDotSimpleName(String str) {
        return str.replace(".", DOT);
    }

    private static String getDotBuilderTypeName(String protoPackage, Class<?> requestType, String requestTypeName) {
        List<Class<?>> buffer = Arrays.stream(requestType.getNestMembers())
                .filter(c -> c.getName().replace("$", ".").equals(String.format("%s.Builder", requestTypeName)))
                .collect(Collectors.toList());
        Class<?> builderClass = buffer.get(0);
        return getDotSimpleName(builderClass.getCanonicalName().replace(protoPackage + ".", ""));
    }
}
