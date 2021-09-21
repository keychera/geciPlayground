package self.chera.generators;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.GeneratedMessageV3;
import com.linecorp.armeria.client.ClientBuilder;
import com.linecorp.armeria.client.Clients;
import org.jboss.forge.roaster.model.source.MethodSource;
import self.chera.grpc.RPCAction;
import self.chera.grpc.RPCGlobals;
import self.chera.grpc.RPCStream;
import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;
import org.jboss.forge.roaster.ParserException;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import self.chera.proto.CheraHandlerGrpc;

import java.beans.Introspector;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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
    // configurable
    private static final String GENERATED_PACKAGE = "self.chera.generated.grpc";
    private static final String PROTO_URL_LITERAL = "Clients.builder(RPCGlobals.url);";
    private static final List<String> classesToDeprecate = List.of(
            "DeprecationGrpc.ToBeDeprecated",
            "DeprecationGrpc.AlsoDeprecated"
    );

    // for processing
    private static final String DOT = "_dot_";
    private static final String UNARY_PARENT_CLASS = "UnaryRPCAction";
    private static final String CLIENT_STREAM_PARENT_CLASS = "ClientStreamRPCAction";
    private static String packageFolderName;
    private static String targetLocation;


    private static class HandlerWrapper {
        public Class<?> protoClass = null;
        public Class<?> handlerGrpcClass = null;
        public Class<?> blockingStubClass = null;
        public Class<?> stubClass = null;
    }

    public static void main(String[] args) throws IOException {
        var protoFolder = args[0];
        targetLocation = args[1];
        prepareGeneratedFolder();
        Map<String, HandlerWrapper> listOfHandler = getListOfProtoHandler(protoFolder);
        listOfHandler.forEach((handlerName, wrapper) -> {
            String className = handlerName.replace("Handler", "Grpc");
            final JavaClassSource rpcActionsClass = Roaster.create(JavaClassSource.class);
            rpcActionsClass.setPackage(GENERATED_PACKAGE).setName(className);

            // each grpc service class
            Class<?> handler = wrapper.handlerGrpcClass;
            List<Field> allMethodDescriptor = Arrays.stream(handler.getDeclaredFields()).filter(t -> t.getType().isAssignableFrom(MethodDescriptor.class)).collect(Collectors.toList());
            for (Field declaredField : allMethodDescriptor) {
                ParameterizedType types;
                String serviceName;
                MethodDescriptor.MethodType serviceType;
                try {
                    MethodDescriptor<?, ?> service = (MethodDescriptor<?, ?>) declaredField.get(handler);
                    serviceType = service.getType();
                    serviceName = service.getBareMethodName();
                    types = (ParameterizedType) declaredField.getGenericType();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
                assert serviceName != null;
                Class<?> protoClass = wrapper.protoClass;
                String protoPackage = protoClass.getPackageName();
                Class<?> requestType = (Class<?>) types.getActualTypeArguments()[0];
                Class<?> responseType = (Class<?>) types.getActualTypeArguments()[1];
                String requestTypeName = requestType.getTypeName().replace("$", ".");
                String responseTypeName = responseType.getTypeName().replace("$", ".");
                System.out.printf("Adding service (%s) class %s, req: %s res: %s%n", serviceType.name(), serviceName, requestTypeName, responseTypeName);

                rpcActionsClass.addImport(protoClass);
                if (!requestTypeName.contains(protoPackage)) {
                    rpcActionsClass.addImport(requestTypeName);
                }
                if (!responseTypeName.contains(protoPackage)) {
                    rpcActionsClass.addImport(responseTypeName);
                }
                rpcActionsClass.addImport(Function.class);
                rpcActionsClass.addImport(RPCGlobals.class);

                String dot_requestTypeName, dot_responseTypeName, dot_builderTypeName;
                JavaClassSource serviceClassToAdd = null;
                switch (serviceType) {
                    case UNARY:
                        addUnaryParent(rpcActionsClass, wrapper);
                        dot_requestTypeName = getDotSimpleName(requestTypeName.replace(protoPackage + ".", ""));
                        dot_responseTypeName = getDotSimpleName(responseTypeName.replace(protoPackage + ".", ""));
                        dot_builderTypeName = getDotBuilderTypeName(protoPackage, requestTypeName);

                        final JavaClassSource unaryActionClass = Roaster.create(JavaClassSource.class);
                        unaryActionClass.setName(serviceName).setPublic().setStatic(true).setSuperType(String.format("%s<%s,%s>", UNARY_PARENT_CLASS, dot_requestTypeName, dot_responseTypeName))
                                .addField().setName("requestBuilder").setPublic().setType(dot_builderTypeName).setLiteralInitializer(String.format("%s.newBuilder();", dot_requestTypeName)).getOrigin()
                                .addMethod().setName("getRequest").setProtected().setReturnType(dot_requestTypeName).setBody("return requestBuilder.build();").getOrigin().addMethod()
                                .setName("getAction").setProtected().setReturnType(String.format("Function<%s,%s>", dot_requestTypeName, dot_responseTypeName))
                                .setBody(String.format("return (req) -> getClient().%s(req);", lowercaseFirstLetter(serviceName))).getOrigin();

                        serviceClassToAdd = unaryActionClass;
                        try {
                            addUnaryStaticExec(rpcActionsClass, serviceName, requestType, responseType);
                        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                        break;
                    case CLIENT_STREAMING:
                        addClientStreamParent(rpcActionsClass, wrapper);
                        dot_requestTypeName = getDotSimpleName(requestTypeName.replace(protoPackage + ".", ""));
                        dot_responseTypeName = getDotSimpleName(responseTypeName.replace(protoPackage + ".", ""));
                        dot_builderTypeName = getDotBuilderTypeName(protoPackage, requestTypeName);

                        final JavaClassSource clientStreamActionClass = Roaster.create(JavaClassSource.class);
                        clientStreamActionClass.setName(serviceName).setPublic().setStatic(true)
                                .setSuperType(String.format("%s<%s,%s>", CLIENT_STREAM_PARENT_CLASS, dot_requestTypeName, dot_responseTypeName)).addField().setName("requestBuilder").setPublic()
                                .setType(dot_builderTypeName).setLiteralInitializer(String.format("%s.newBuilder();", dot_requestTypeName)).getOrigin().addMethod().setName("getRequest").setProtected()
                                .setReturnType(dot_requestTypeName).setBody("var req = requestBuilder.build(); requestBuilder.clear(); return req;").getOrigin().addMethod()
                                .setName("getRequestStreamAction").setProtected().setReturnType(String.format("Function<StreamObserver<%s>,StreamObserver<%s>>", dot_responseTypeName,
                                // switched response and request position for ClientStream
                                dot_requestTypeName)).setBody(String.format("return (resStream) -> getClient().%s(resStream);", lowercaseFirstLetter(serviceName))).getOrigin();

                        rpcActionsClass.addImport(StreamObserver.class);
                        serviceClassToAdd = clientStreamActionClass;
                        break;
                    default:
                        System.out.println("not implemented yet");
                        break;
                }
                if (serviceClassToAdd != null) {
                    if (classesToDeprecate.contains(String.format("%s.%s", className, serviceName))) {
                        serviceClassToAdd.addAnnotation(Deprecated.class);
                    }
                    rpcActionsClass.addNestedType(serviceClassToAdd);
                }
            }


            String sourceCode = rpcActionsClass.toString().replace(DOT, ".");
            String filename = String.format("%s/%s.java", packageFolderName, className);
            try {
                File classFile = new File(filename);
                if (classFile.createNewFile()) {
                    System.out.println("File created: " + classFile.getName());
                } else {
                    System.out.println("File already exists.");
                }
                String formattedSourceCode = Roaster.format(sourceCode);
                try {
                    var currentClass = Roaster.parse(classFile);
                    if (currentClass.toString().equals(formattedSourceCode)) {
                        System.out.printf("%s is up to date!%n", currentClass.getName());
                    } else {
                        FileWriter myWriter = new FileWriter(filename);
                        myWriter.write(formattedSourceCode);
                        myWriter.close();
                        System.out.println("Successfully wrote to the file.");
                    }
                } catch (ParserException pre) {
                    FileWriter myWriter = new FileWriter(filename);
                    myWriter.write(formattedSourceCode);
                    myWriter.close();
                    System.out.println("Successfully wrote to the file.");
                }
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

            parentRpcAction.setName(UNARY_PARENT_CLASS).setPrivate().setStatic(true).setAbstract(true).addTypeVariable("Req").setBounds(GeneratedMessageV3.class).getOrigin().addTypeVariable("Res")
                    .setBounds(GeneratedMessageV3.class).getOrigin().setSuperType("RPCAction<Req, Res>").addField().setName("clientBuilder").setPublic().setType(ClientBuilder.class)
                    .setLiteralInitializer(PROTO_URL_LITERAL).getOrigin().addMethod().setName("getClient").setProtected().setReturnType(wrapper.blockingStubClass)
                    .setBody(String.format("return clientBuilder.build(%1$s.class);", wrapper.blockingStubClass.getSimpleName()));

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

            parentRpcAction.setName(CLIENT_STREAM_PARENT_CLASS).setPrivate().setStatic(true).setAbstract(true).addTypeVariable("Req").setBounds(GeneratedMessageV3.class).getOrigin()
                    .addTypeVariable("Res").setBounds(GeneratedMessageV3.class).getOrigin().setSuperType("RPCStream<Req, Res>").addField().setName("clientBuilder").setPublic()
                    .setType(ClientBuilder.class).setLiteralInitializer(PROTO_URL_LITERAL).getOrigin().addMethod().setName("getClient").setProtected()
                    .setReturnType(wrapper.stubClass).setBody(String.format("return clientBuilder.responseTimeoutMillis(10000).build(%s.class);", wrapper.stubClass.getSimpleName()));

            targetSource.addImport(GeneratedMessageV3.class);
            targetSource.addImport(RPCStream.class);
            targetSource.addImport(ClientBuilder.class);
            targetSource.addImport(Clients.class);
            targetSource.addImport(wrapper.stubClass);
            targetSource.addNestedType(parentRpcAction);
        }
    }

    private static void addUnaryStaticExec(JavaClassSource source, String serviceName, Class<?> requestType,Class<?> responseType) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        var descriptor = requestType.getMethod("getDescriptor");
        var params = (Descriptors.Descriptor) descriptor.invoke(null);
        final var methodName = Introspector.decapitalize(serviceName);
        var unaryStaticExecMethod = source.addMethod().setPublic().setStatic(true).setReturnType(responseType).setName(methodName);
        var setterList = new ArrayList<String>();
        params.getFields().forEach(field -> {
            var setterName = "set" + makeJavaName(field.getName());
            var paramName = Introspector.decapitalize(makeJavaName(field.getName()));
            var type = TYPE_MAPPING.getOrDefault(field.getJavaType(), "Object");

            unaryStaticExecMethod.addParameter(type, paramName);
            setterList.add(String.format(".%s(%s)", setterName, paramName));
        });
        var bodyBuilder = new StringBuilder();
        bodyBuilder
                .append("        var service = new ").append(serviceName).append("();\n")
                .append("        service.clientBuilder.addHeader(\"authorization\", \"Bearer \");\n");
        if (!setterList.isEmpty()) {
            bodyBuilder.append( "        service.requestBuilder");
            setterList.forEach(bodyBuilder::append);
            bodyBuilder.append( ";\n");
        }
        bodyBuilder.append(
                "        return service.exec();"
        );
        unaryStaticExecMethod.setBody(bodyBuilder.toString());
    }

    private static Map<String, HandlerWrapper> getListOfProtoHandler(String protoFolder) throws IOException {
        Map<String, HandlerWrapper> handlers = new HashMap<>();
        Set<String> allProtoPackage = new HashSet<>();
        HashMap<String, String> allProtoJavaName = new HashMap<>();

        List<File> protoFiles = listAllFiles(protoFolder).stream().filter(f -> f.getName().endsWith(".proto")).collect(Collectors.toList());

        for (File file : protoFiles) {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line, javaPackage = null;
            while ((line = br.readLine()) != null && javaPackage == null) {
                Matcher m = Pattern.compile("option *java_package *= *\"(.*)\";").matcher(line);
                if (m.matches()) {
                    javaPackage = m.group(1);
                }
            }
            if (javaPackage != null) {
                allProtoPackage.add(javaPackage);
            } else {
                br = new BufferedReader(new FileReader(file));
                while ((line = br.readLine()) != null && javaPackage == null) {
                    Matcher m = Pattern.compile("package *(.*);").matcher(line);
                    if (m.matches()) {
                        javaPackage = m.group(1);
                        allProtoPackage.add(javaPackage);
                    }
                }
            }
            String serviceName = null;
            while ((line = br.readLine()) != null && serviceName == null) {
                Matcher m = Pattern.compile("service *([^{ ]*) *\\{*").matcher(line);
                if (m.matches()) {
                    serviceName = m.group(1);
                }
            }
            String protoJavaName = makeJavaName(file.getName().replace(".proto", ""));
            allProtoJavaName.put(serviceName, protoJavaName);
        }

        allProtoPackage.forEach(packageSource -> {
            URL root = Thread.currentThread().getContextClassLoader().getResource(packageSource.replace(".", "/"));

            // Filter .class files.
            if (root != null) {
                File[] files = new File(root.getFile()).listFiles((dir, name) -> name.endsWith(".class"));

                // Find the classes
                assert files != null;
                for (File file : files) {
                    String className = file.getName().replaceAll(".class$", "");
                    Matcher m = Pattern.compile("(.*)Grpc.*").matcher(className);
                    if (m.matches()) {
                        String handlerName = m.group(1);
                        if (!handlers.containsKey(handlerName)) {
                            handlers.put(handlerName, new HandlerWrapper());
                        }
                        try {
                            if (className.matches(".*Grpc$")) {
                                handlers.get(handlerName).handlerGrpcClass =
                                        Class.forName(packageSource + "." + className);
                                String protoJavaName = allProtoJavaName.get(handlerName);
                                Class<?> protoClass;
                                try {
                                    protoClass = Class.forName(packageSource + "." + protoJavaName);
                                    handlers.get(handlerName).protoClass = protoClass;
                                } catch (ClassNotFoundException e) {
                                    e.printStackTrace();
                                    throw new RuntimeException(e.getMessage());
                                }
                            } else if (className.matches(".*Grpc.*BlockingStub")) {
                                handlers.get(handlerName).blockingStubClass =
                                        Class.forName(packageSource + "." + className);
                            } else if (className.matches(".*Grpc.*FutureStub")) {
                                // do nothing yet
                            } else if (className.matches(".*Grpc.*Stub")) {
                                handlers.get(handlerName).stubClass = Class.forName(packageSource + "." + className);
                            }
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                            throw new RuntimeException(e.getMessage());
                        }
                    }
                }
            }
        });
        return handlers;
    }

    private static void prepareGeneratedFolder() {
        try {
            packageFolderName = targetLocation + "/" + GENERATED_PACKAGE.replace(".", "/");
            Files.createDirectories(Path.of(packageFolderName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String lowercaseFirstLetter(String str) {
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }

    private static String uppercaseFirstLetter(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static String makeJavaName(String str) {
        String capitalized = str.substring(0, 1).toUpperCase() + str.substring(1);
        var tokenized = Arrays.stream(capitalized.split("-")).reduce((subtotal, element) -> subtotal + uppercaseFirstLetter(element));
        var tokenized2 = Arrays.stream(tokenized.orElse(str).split("_")).reduce((subtotal, element) -> subtotal + uppercaseFirstLetter(element));
        return tokenized2.orElse(str);
    }

    private static String getDotSimpleName(String str) {
        return str.replace(".", DOT);
    }

    private static String getDotBuilderTypeName(String protoPackage, String requestTypeName) {
        String simpleName = requestTypeName.replace(protoPackage + ".", "");
        return getDotSimpleName(String.format("%s.Builder", simpleName));
    }

    private static List<File> listAllFiles(String directoryName) {
        File directory = new File(directoryName);
        List<File> res = new ArrayList<>();
        File[] fList = directory.listFiles();
        if (fList != null) {
            for (File file : fList) {
                if (file.isFile()) {
                    res.add(file);
                } else if (file.isDirectory()) {
                    res.addAll(listAllFiles(file.getAbsolutePath()));
                }
            }
        }
        return res;
    }

    private static final Map<JavaType, String> TYPE_MAPPING = Map.of(
            JavaType.INT, int.class.getSimpleName(),
            JavaType.LONG, long.class.getSimpleName(),
            JavaType.FLOAT, float.class.getSimpleName(),
            JavaType.DOUBLE, double.class.getSimpleName(),
            JavaType.BOOLEAN, boolean.class.getSimpleName(),
            JavaType.STRING, String.class.getSimpleName()
    );
}
