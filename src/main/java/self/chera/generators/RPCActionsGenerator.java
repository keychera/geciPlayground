package self.chera.generators;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.MethodDescriptor;
import io.grpc.stub.StreamObserver;
import org.jboss.forge.roaster.ParserException;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import self.chera.grpc.*;

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
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RPCActionsGenerator {
    // configurable
    private static final String GENERATED_PACKAGE = "self.chera.generated.grpc";
    private static final List<String> classesToDeprecate = List.of(
            "DeprecationGrpc.ToBeDeprecated",
            "DeprecationGrpc.AlsoDeprecated"
    );

    // for processing
    private static final String DOT = "_d_";
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

            rpcActionsClass.addImport(RPCClass.class);
            rpcActionsClass.setSuperType(String.format("%s<%s>", RPCClass.class.getName(), className));

            rpcActionsClass.addMethod().setConstructor(true).setPrivate().setBody("");

            rpcActionsClass.addMethod().setPublic().setStatic(true).setReturnType(className)
                    .setName(Introspector.decapitalize(className))
                    .setBody(String.format("return new %s();", className));

            // each grpc service class
            Class<?> handler = wrapper.handlerGrpcClass;
            List<Field> allMethodDescriptor = Arrays.stream(handler.getDeclaredFields()).filter(
                    t -> t.getType().isAssignableFrom(MethodDescriptor.class)).collect(Collectors.toList());
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
                System.out.printf(
                        "Adding service (%s) class %s, req: %s res: %s%n", serviceType.name(), serviceName,
                        requestTypeName, responseTypeName
                );

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
                        unaryActionClass.setName(serviceName).setPublic().setStatic(true).setSuperType(
                                String.format("%s<%s,%s>", UNARY_PARENT_CLASS, dot_requestTypeName,
                                        dot_responseTypeName
                                ))
                                .addField().setName("requestBuilder").setPublic().setType(
                                dot_builderTypeName).setLiteralInitializer(
                                String.format("%s.newBuilder();", dot_requestTypeName)).getOrigin()
                                .addMethod().setName("getRequest").setProtected().setReturnType(
                                dot_requestTypeName).setBody("return requestBuilder.build();").getOrigin().addMethod()
                                .setName("getAction").setProtected().setReturnType(
                                String.format("Function<%s,%s>", dot_requestTypeName, dot_responseTypeName))
                                .setBody(String.format(
                                        "return (req) -> getClient().%s(req);",
                                        lowercaseFirstLetter(serviceName)
                                )).getOrigin();

                        serviceClassToAdd = unaryActionClass;
                        try {
                            addUnaryExec(rpcActionsClass, protoClass, serviceName, requestType, responseType);
                        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                            e.printStackTrace();
                            System.err.printf("not adding static exec method for service: %s%n", serviceName);
                        }
                        break;
                    case CLIENT_STREAMING:
                        addClientStreamParent(rpcActionsClass, wrapper);
                        dot_requestTypeName = getDotSimpleName(requestTypeName.replace(protoPackage + ".", ""));
                        dot_responseTypeName = getDotSimpleName(responseTypeName.replace(protoPackage + ".", ""));
                        dot_builderTypeName = getDotBuilderTypeName(protoPackage, requestTypeName);

                        final JavaClassSource clientStreamActionClass = Roaster.create(JavaClassSource.class);
                        clientStreamActionClass.setName(serviceName).setPublic().setStatic(true)
                                .setSuperType(
                                        String.format("%s<%s,%s>", CLIENT_STREAM_PARENT_CLASS, dot_requestTypeName,
                                                dot_responseTypeName
                                        )).addField().setName("requestBuilder").setPublic()
                                .setType(dot_builderTypeName).setLiteralInitializer(
                                String.format("%s.newBuilder();", dot_requestTypeName)).getOrigin().addMethod().setName(
                                "getRequest").setProtected()
                                .setReturnType(dot_requestTypeName).setBody(
                                "var req = requestBuilder.build(); requestBuilder.clear(); return req;").getOrigin().addMethod()
                                .setName("getRequestStreamAction").setProtected().setReturnType(
                                String.format("Function<StreamObserver<%s>,StreamObserver<%s>>", dot_responseTypeName,
                                        // switched response and request position for ClientStream
                                        dot_requestTypeName
                                )).setBody(String.format(
                                "return (resStream) -> getClient().%s(resStream);",
                                lowercaseFirstLetter(serviceName)
                        )).getOrigin();

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

            parentRpcAction.setName(UNARY_PARENT_CLASS).setPrivate().setStatic(true).setAbstract(true)
                    .addTypeVariable("Req").setBounds(GeneratedMessageV3.class).getOrigin()
                    .addTypeVariable("Res").setBounds(GeneratedMessageV3.class).getOrigin()
                    .setSuperType("RPCAction<Req, Res>")
                    .addMethod().setName("getClient").setProtected().setReturnType(wrapper.blockingStubClass)
                    .setBody(String.format(
                            "return clientBuilder.build(%1$s.class);",
                            wrapper.blockingStubClass.getSimpleName()
                    ));

            targetSource.addImport(GeneratedMessageV3.class);
            targetSource.addImport(RPCAction.class);
            targetSource.addImport(RPCClientBuilder.class);
            targetSource.addImport(wrapper.blockingStubClass);
            targetSource.addNestedType(parentRpcAction);
        }
    }

    private static void addClientStreamParent(JavaClassSource targetSource, HandlerWrapper wrapper) {
        if (!targetSource.hasNestedType(CLIENT_STREAM_PARENT_CLASS)) {
            // Parent RPC Action class
            final JavaClassSource parentRpcAction = Roaster.create(JavaClassSource.class);

            parentRpcAction.setName(CLIENT_STREAM_PARENT_CLASS).setPrivate().setStatic(true).setAbstract(true)
                    .addTypeVariable("Req").setBounds(GeneratedMessageV3.class).getOrigin()
                    .addTypeVariable("Res").setBounds(GeneratedMessageV3.class).getOrigin()
                    .setSuperType("RPCStream<Req, Res>")
                    .addMethod().setName("getClient").setProtected().setReturnType(wrapper.stubClass)
                    .setBody(String.format(
                            "return clientBuilder.responseTimeoutMillis(10000).build(%s.class);",
                            wrapper.stubClass.getSimpleName()
                    ));

            targetSource.addImport(GeneratedMessageV3.class);
            targetSource.addImport(RPCStream.class);
            targetSource.addImport(RPCClientBuilder.class);
            targetSource.addImport(wrapper.stubClass);
            targetSource.addNestedType(parentRpcAction);
        }
    }

    private static void addUnaryExec(
            JavaClassSource source, Class<?> protoClass, String serviceName, Class<?> requestType,
            Class<?> responseType
    ) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        var descriptor = requestType.getMethod("getDescriptor");
        var params = (Descriptors.Descriptor) descriptor.invoke(null);
        final var methodName = Introspector.decapitalize(serviceName);
        var unaryStaticExecMethod = source.addMethod().setPublic().setReturnType(responseType).setName(methodName);
        var setterList = new ArrayList<String>();
        params.getFields().forEach(field -> {
            var paramName = Introspector.decapitalize(makeJavaName(field.getName()));
            var type = mapType(field.getJavaType());

            String setterName;
            if (field.isMapField()) { // mapField is also a repeatedField
                var mapInfo = field.getMessageType().getFields();
                var keyType = mapType(mapInfo.get(0).getJavaType());
                var valueType = mapType(mapInfo.get(1).getJavaType());
                var paramType = String.format("%s<%s, %s>", Map.class.getCanonicalName(), keyType, valueType);
                unaryStaticExecMethod.addParameter(paramType, paramName);
                setterName = "putAll" + makeJavaName(field.getName());
            } else {
                var repeated = field.isRepeated();
                if (repeated) {
                    setterName = "addAll" + makeJavaName(field.getName());
                    source.addImport(List.class);
                } else {
                    setterName = "set" + makeJavaName(field.getName());
                }

                String paramType;
                if (field.getType() == Descriptors.FieldDescriptor.Type.ENUM) {
                    var enumType = field.getEnumType().getName();
                    // a kinda dirty solution to resolve enum inside a request and outside the request. not yet handling deeper layer or imported enum
                    var container = field.getEnumType().getContainingType();
                    if (container != null) {
                        enumType = String.format("%s.%s", container.getName(), enumType);
                    }
                    paramType = String.format("%s.%s", protoClass.getCanonicalName(), enumType);
                } else if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
                    var messageType = field.getMessageType().getName();
                    paramType = String.format("%s.%s", protoClass.getCanonicalName(), messageType);
                } else {
                    paramType = type;
                }
                paramType = resolveTypeIfRepeated(repeated, paramType);
                unaryStaticExecMethod.addParameter(paramType, paramName);
            }
            setterList.add(String.format(".%s(%s)", setterName, paramName));
        });
        var bodyBuilder = new StringBuilder();
        bodyBuilder
                .append("        var service = new ").append(serviceName).append("();\n")
                .append("        clientMod.forEach(c -> c.accept(service.clientBuilder));\n");
        if (!setterList.isEmpty()) {
            bodyBuilder.append("        service.requestBuilder");
            setterList.forEach(bodyBuilder::append);
            bodyBuilder.append(";\n");
        }
        bodyBuilder.append(
                "        return service.exec();"
        );
        unaryStaticExecMethod.setBody(bodyBuilder.toString());
    }

    private static Map<String, HandlerWrapper> getListOfProtoHandler(String protoFolder) throws IOException {
        Map<String, HandlerWrapper> handlers = new HashMap<>();

        var allProtoPackage = listAllProtoFiles(protoFolder).stream()
                .flatMap(file -> getJavaPackageNameFromProto(file).stream())
                .collect(Collectors.toUnmodifiableList());

        var allProtoJavaName = listAllProtoFiles(protoFolder).stream()
                .collect(Collectors.toUnmodifiableMap(
                        file -> getServiceNameFromProto(file).orElse(file.getName()),
                        file -> makeJavaName(file.getName().replace(".proto", ""))
                ));

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

    private static List<File> listAllProtoFiles(String directoryName) throws IOException {
        return Files.walk(Path.of(directoryName))
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .filter(file -> file.getName().endsWith(".proto"))
                .collect(Collectors.toUnmodifiableList());
    }

    private static Optional<String> getJavaPackageNameFromProto(File protoFile) {
        try {
            var br = new BufferedReader(new FileReader(protoFile));
            var javaPackagePattern = Pattern.compile("option *java_package *= *\"(.*)\";");
            var protoPackagePattern = Pattern.compile("package *(.*);");
            return Stream.of(javaPackagePattern, protoPackagePattern).flatMap(pattern -> br.lines()
                    .flatMap(s -> pattern.matcher(s).results())
                    .filter(m -> m.groupCount() > 0)
                    .map(m -> m.group(1))
            ).findFirst();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private static Optional<String> getServiceNameFromProto(File protoFile) {
        try {
            var br = new BufferedReader(new FileReader(protoFile));
            var servicePattern = Pattern.compile("service *([^{ ]*) *\\{*");
            return br.lines()
                    .flatMap(s -> servicePattern.matcher(s).results())
                    .filter(m -> m.groupCount() > 0)
                    .map(m -> m.group(1))
                    .findAny();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return Optional.empty();
        }
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
        var tokenized = Arrays.stream(capitalized.split("-")).reduce(
                (subtotal, element) -> subtotal + uppercaseFirstLetter(element));
        var tokenized2 = Arrays.stream(tokenized.orElse(str).split("_")).reduce(
                (subtotal, element) -> subtotal + uppercaseFirstLetter(element));
        return tokenized2.orElse(str);
    }

    private static String getDotSimpleName(String str) {
        return str.replace(".", DOT);
    }

    private static String getDotBuilderTypeName(String protoPackage, String requestTypeName) {
        String simpleName = requestTypeName.replace(protoPackage + ".", "");
        return getDotSimpleName(String.format("%s.Builder", simpleName));
    }

    private static String mapType(Descriptors.FieldDescriptor.JavaType javaType) {
        return TYPE_MAPPING.getOrDefault(javaType, "Object");
    }

    private static String resolveTypeIfRepeated(boolean repeated, String type) {
        if (repeated) {
            return String.format("List<%s>", type);
        } else {
            return type;
        }
    }

    private static final Map<Descriptors.FieldDescriptor.JavaType, String> TYPE_MAPPING = Map.of(
            Descriptors.FieldDescriptor.JavaType.INT, Integer.class.getSimpleName(),
            Descriptors.FieldDescriptor.JavaType.LONG, Long.class.getSimpleName(),
            Descriptors.FieldDescriptor.JavaType.FLOAT, Float.class.getSimpleName(),
            Descriptors.FieldDescriptor.JavaType.DOUBLE, Double.class.getSimpleName(),
            Descriptors.FieldDescriptor.JavaType.BOOLEAN, Boolean.class.getSimpleName(),
            Descriptors.FieldDescriptor.JavaType.STRING, String.class.getSimpleName(),
            Descriptors.FieldDescriptor.JavaType.BYTE_STRING, ByteString.class.getCanonicalName()
    );
}
