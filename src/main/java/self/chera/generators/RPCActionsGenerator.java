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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
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

    public static void main(String[] args) {
        var protoFolder = args[0];
        targetLocation = args[1];
        prepareGeneratedFolder();
        var listOfHandler = getListOfProtoHandler(protoFolder);
        listOfHandler.forEach((handlerName, wrapper) -> {
            var className = handlerName.replace("Handler", "Grpc");
            final var rpcActionsClass = Roaster.create(JavaClassSource.class);

            // class identity
            rpcActionsClass.addImport(RPCClass.class);
            rpcActionsClass.setPackage(GENERATED_PACKAGE).setName(className);
            rpcActionsClass.setSuperType(String.format("%s<%s>", RPCClass.class.getName(), className));

            rpcActionsClass.addMethod().setConstructor(true).setPrivate().setBody("");

            rpcActionsClass.addMethod().setPublic().setStatic(true).setReturnType(className)
                    .setName(Introspector.decapitalize(className))
                    .setBody(String.format("return new %s();", className));

            // each grpc service class
            var handler = wrapper.handlerGrpcClass;
            var allMethodDescriptor = Stream.of(handler.getDeclaredFields())
                    .filter(t -> t.getType().isAssignableFrom(MethodDescriptor.class))
                    .collect(Collectors.toList());
            allMethodDescriptor.forEach(methodDescriptor -> {
                ParameterizedType types;
                String serviceName;
                MethodDescriptor.MethodType serviceType;
                try {
                    var service = (MethodDescriptor<?, ?>) methodDescriptor.get(handler);
                    serviceType = service.getType();
                    serviceName = service.getBareMethodName();
                    types = (ParameterizedType) methodDescriptor.getGenericType();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
                assert serviceName != null;
                var protoClass = wrapper.protoClass;
                var protoPackage = protoClass.getPackageName();
                var requestType = (Class<?>) types.getActualTypeArguments()[0];
                var responseType = (Class<?>) types.getActualTypeArguments()[1];
                var requestTypeName = requestType.getTypeName().replace("$", ".");
                var responseTypeName = responseType.getTypeName().replace("$", ".");
                System.out.printf(
                        "Adding service (%s) class %s, req: %s res: %s%n",
                        serviceType.name(), serviceName, requestTypeName, responseTypeName
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

                        final var unaryActionClass = Roaster.create(JavaClassSource.class);
                        unaryActionClass.setName(serviceName).setPublic().setStatic(true)
                                .setSuperType(String.format("%s<%s,%s>", UNARY_PARENT_CLASS, dot_requestTypeName,
                                        dot_responseTypeName
                                ));

                        unaryActionClass.addField().setName("requestBuilder").setPublic().setType(dot_builderTypeName)
                                .setLiteralInitializer(String.format("%s.newBuilder();", dot_requestTypeName));

                        unaryActionClass.addMethod().setName("getRequest").setProtected()
                                .setReturnType(dot_requestTypeName).setBody("return requestBuilder.build();");

                        unaryActionClass.addMethod().setName("getAction").setProtected()
                                .setReturnType(
                                        String.format("Function<%s,%s>", dot_requestTypeName, dot_responseTypeName)
                                )
                                .setBody(String.format(
                                        "return (req) -> getClient().%s(req);",
                                        Introspector.decapitalize(serviceName)
                                ));

                        serviceClassToAdd = unaryActionClass;
                        try {
                            addUnaryStaticExec(
                                    rpcActionsClass, protoClass, className, serviceName, requestType, responseType
                            );
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

                        final var clientStreamActionClass = Roaster.create(JavaClassSource.class);
                        clientStreamActionClass.setName(serviceName).setPublic().setStatic(true)
                                .setSuperType(String.format(
                                        "%s<%s,%s>", CLIENT_STREAM_PARENT_CLASS, dot_requestTypeName,
                                        dot_responseTypeName
                                ));

                        clientStreamActionClass.addField().setName("requestBuilder")
                                .setPublic().setType(dot_builderTypeName)
                                .setLiteralInitializer(
                                        String.format("%s.newBuilder();", dot_requestTypeName)
                                );

                        clientStreamActionClass.addMethod().setName("getRequest")
                                .setProtected().setReturnType(dot_requestTypeName)
                                .setBody(
                                        "var req = requestBuilder.build(); requestBuilder.clear(); return req;"
                                );

                        // switched response and request position for ClientStream
                        clientStreamActionClass.addMethod().setName("getRequestStreamAction").setProtected()
                                .setReturnType(String.format(
                                        "Function<StreamObserver<%s>, StreamObserver<%s>>",
                                        dot_responseTypeName, dot_requestTypeName
                                ))
                                .setBody(String.format(
                                        "return (resStream) -> getClient().%s(resStream);",
                                        Introspector.decapitalize(serviceName)
                                ));

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
            });


            var sourceCode = rpcActionsClass.toString().replace(DOT, ".");
            var filename = String.format("%s/%s.java", packageFolderName, className);
            try {
                var classFile = new File(filename);
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

    private static void addUnaryStaticExec(
            JavaClassSource source, Class<?> protoClass, String className, String serviceName, Class<?> requestType,
            Class<?> responseType
    ) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        var methodName = Introspector.decapitalize(serviceName);
        var params = (Descriptors.Descriptor) requestType.getMethod("getDescriptor").invoke(null);

        var unaryStaticExecMethod = source
                .addMethod().setPublic().setReturnType(responseType).setName(methodName);

        if (classesToDeprecate.contains(String.format("%s.%s", className, serviceName))) {
            unaryStaticExecMethod.addAnnotation(Deprecated.class);
        }

        var setterList = params.getFields().stream().map(field -> {
            String paramType, setterName;
            if (field.isMapField()) { // mapField is also a repeatedField
                var mapInfo = field.getMessageType().getFields();
                var keyType = mapType(mapInfo.get(0).getJavaType());
                var valueType = mapType(mapInfo.get(1).getJavaType());
                paramType = String.format("%s<%s, %s>", Map.class.getCanonicalName(), keyType, valueType);
                setterName = "putAll" + makeJavaName(field.getName());
            } else {
                var repeated = field.isRepeated();
                if (repeated) {
                    setterName = "addAll" + makeJavaName(field.getName());
                    source.addImport(List.class);
                } else {
                    setterName = "set" + makeJavaName(field.getName());
                }
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
                    paramType = mapType(field.getJavaType());
                }
                paramType = resolveTypeIfRepeated(repeated, paramType);
            }

            var paramName = Introspector.decapitalize(makeJavaName(field.getName()));
            unaryStaticExecMethod.addParameter(paramType, paramName);
            return String.format(".%s(%s)", setterName, paramName);
        }).collect(Collectors.toList());

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

    private static Map<String, HandlerWrapper> getListOfProtoHandler(String protoFolder) {
        var handlerClasses = streamAllFiles(protoFolder, ".proto")
                .map(file -> Map.entry(
                        getServiceNameFromProto(file).orElse(file.getName()),
                        getProtoJavaInfo(file).orElse(ProtoJavaInfo.NULL)
                ))
                .filter(entry -> entry.getValue().isNotNull())
                .flatMap(entry -> {
                    var serviceName = entry.getKey();
                    var packageName = entry.getValue().packageName;
                    var protoJavaName = entry.getValue().protoJavaName;
                    var protoFile = entry.getValue().protoFile;
                    return getPackageDir(packageName).stream()
                            .flatMap(packageDir -> streamAllFiles(packageDir, ".class"))
                            .filter(isFileRelevant(serviceName, protoJavaName))
                            .flatMap(classFile -> GrpcClassInfo.resolve(packageName, protoFile, classFile).stream())
                            .filter(GrpcClassInfo::isNecessary);
                }).collect(Collectors.groupingBy(GrpcClassInfo::getHandlerName));

        return handlerClasses.entrySet().stream()
                .flatMap(entry -> {
                    var wrapper = new HandlerWrapper();
                    entry.getValue().get(0).asProtoClass().ifPresent(wrapper::setProtoClass);
                    entry.getValue().stream()
                            .filter(GrpcClassInfo::isNecessary)
                            .forEach(info -> {
                                info.asHandlerClass().ifPresent(wrapper::setHandlerGrpcClass);
                                info.asBlockingStub().ifPresent(wrapper::setBlockingStubClass);
                                info.asStub().ifPresent(wrapper::setStubClass);
                            });
                    return Stream.of(Map.entry(entry.getKey(), wrapper));
                })
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Stream<File> streamAllFiles(String directoryName, String extension) {
        try {
            return Files.walk(Path.of(directoryName))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().endsWith(extension));
        } catch (IOException e) {
            e.printStackTrace();
            return Stream.empty();
        }
    }

    private static Optional<ProtoJavaInfo> getProtoJavaInfo(File protoFile) {
        try {
            var br = new BufferedReader(new FileReader(protoFile));
            var javaPackagePattern = Pattern.compile("option *java_package *= *\"(.*)\";");
            var protoPackagePattern = Pattern.compile("package *(.*);");
            var className = makeJavaName(protoFile.getName().replace(".proto", ""));
            return Stream.of(javaPackagePattern, protoPackagePattern).flatMap(pattern -> br.lines()
                    .flatMap(s -> pattern.matcher(s).results())
                    .filter(m -> m.groupCount() > 0)
                    .map(m -> m.group(1))
                    .map(packageName -> new ProtoJavaInfo(protoFile, packageName, className))
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

    private static Optional<String> getPackageDir(String packageName) {
        var url = Thread.currentThread().getContextClassLoader().getResource(packageName.replace(".", "/"));
        if (url == null) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(Path.of(url.toURI()).toString());
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }
    }

    private static Predicate<File> isFileRelevant(String serviceName, String protoJavaName) {
        return classFile -> {
            var className = classFile.getName().strip().toLowerCase();
            return className.contains(protoJavaName.toLowerCase()) || className.contains(serviceName.toLowerCase());
        };
    }

    private static void prepareGeneratedFolder() {
        try {
            packageFolderName = targetLocation + "/" + GENERATED_PACKAGE.replace(".", "/");
            Files.createDirectories(Path.of(packageFolderName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String makeJavaName(String str) {
        return Stream.of(str)
                .map(RPCActionsGenerator::uppercaseFirstLetter)
                .flatMap(s -> Stream.of(s.split("[-_]")))
                .reduce((subtotal, element) -> subtotal + uppercaseFirstLetter(element))
                .orElse(str);
    }

    private static String uppercaseFirstLetter(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
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

    private static class HandlerWrapper {
        public Class<?> protoClass = null;
        public Class<?> handlerGrpcClass = null;
        public Class<?> blockingStubClass = null;
        public Class<?> stubClass = null;

        public void setProtoClass(Class<?> protoClass) {
            this.protoClass = protoClass;
        }

        public void setHandlerGrpcClass(Class<?> handlerGrpcClass) {
            this.handlerGrpcClass = handlerGrpcClass;
        }

        public void setBlockingStubClass(Class<?> blockingStubClass) {
            this.blockingStubClass = blockingStubClass;
        }

        public void setStubClass(Class<?> stubClass) {
            this.stubClass = stubClass;
        }
    }

    private static class ProtoJavaInfo {
        public final File protoFile;
        public final String packageName;
        public final String protoJavaName;

        public ProtoJavaInfo(File protoFile, String packageName, String protoJavaName) {
            this.protoFile = protoFile;
            this.packageName = packageName;
            this.protoJavaName = protoJavaName;
        }

        public boolean isNotNull() {
            return !equals(NULL);
        }

        public static ProtoJavaInfo NULL = new ProtoJavaInfo(null, "", "");
    }

    private static class GrpcClassInfo {
        public static final Pattern mainPattern = Pattern.compile("(.*)Grpc.*\\.class$");
        public static final Pattern handlerClassPattern = Pattern.compile(".*Grpc$");
        public static final Pattern blockingStubPattern = Pattern.compile(".*Grpc.*BlockingStub$");
        public static final Pattern futureStubPattern = Pattern.compile(".*Grpc.*FutureStub$");
        public static final Pattern stubPattern = Pattern.compile(".*Grpc.*Stub$");

        private final File protoFile;
        private final String packageName;
        private final String className;

        private final String handlerName;

        public String getHandlerName() {
            return handlerName;
        }

        private GrpcClassInfo(String packageName, File protoFile, File classFile, String handlerName) {
            this.protoFile = protoFile;
            this.packageName = packageName;
            this.className = classFile.getName().replaceAll(".class$", "");
            this.handlerName = handlerName;
        }

        public static Optional<GrpcClassInfo> resolve(String packageName, File protoFile, File classFile) {
            return mainPattern.matcher(classFile.getName()).results()
                    .filter(m -> m.groupCount() > 0)
                    .map(m -> new GrpcClassInfo(
                            packageName, protoFile, classFile,
                            m.group(1).replaceAll(".class$", "")
                    ))
                    .findAny();
        }

        public boolean isNecessary() {
            return matchClass(handlerClassPattern)
                    || matchClass(blockingStubPattern)
                    || matchClass(futureStubPattern)
                    || matchClass(stubPattern);
        }

        public Optional<Class<?>> asProtoClass() {
            var protoJavaName = makeJavaName(protoFile.getName().replace(".proto", ""));
            try {
                return Optional.of(Class.forName(packageName + "." + protoJavaName));
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }

        public Optional<Class<?>> asHandlerClass() {
            return getClassWithMatchingPattern(handlerClassPattern);
        }

        public Optional<Class<?>> asBlockingStub() {
            return getClassWithMatchingPattern(blockingStubPattern);
        }

        public Optional<Class<?>> asFutureStub() {
            return getClassWithMatchingPattern(futureStubPattern);
        }

        public Optional<Class<?>> asStub() {
            var notBlocking = !blockingStubPattern.matcher(className).matches();
            var notFuture = !futureStubPattern.matcher(className).matches();
            if (notBlocking && notFuture) {
                return getClassWithMatchingPattern(stubPattern);
            } else {
                return Optional.empty();
            }
        }

        private Optional<Class<?>> getClassWithMatchingPattern(Pattern pattern) {
            if (matchClass(pattern)) {
                try {
                    return Optional.of(Class.forName(packageName + "." + className));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        }

        private boolean matchClass(Pattern pattern) {
            return pattern.matcher(className).matches();
        }
    }
}
