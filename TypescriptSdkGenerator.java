package com.example.demo;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class TypescriptSdkGenerator {

    private final List<Class<?>> serviceClasses = new ArrayList<>();
    private String outputDirectory;
    private boolean shouldExecute = true;
    private String typescriptSdkFileName = "api.ts";

    private static final Map<String, String> JavaToTypescriptTypeMap = new HashMap<>() {{
        put("String", "string");
        put("long", "number");
        put("Long", "number");
        put("int", "number");
        put("Integer", "number");
        put("boolean", "boolean");
        put("Boolean", "boolean");
        put("double", "number");
        put("Double", "number");
        put("float", "number");
        put("Float", "number");
        put("byte", "number");
        put("Byte", "number");
        put("short", "number");
        put("Short", "number");
        put("char", "string");
        put("Character", "string");
    }};

    public TypescriptSdkGenerator extendTypeMap(String javaType, String typescriptType) {
        JavaToTypescriptTypeMap.put(javaType, typescriptType);
        return this;
    }

    public TypescriptSdkGenerator() {
    }

    public TypescriptSdkGenerator setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
        return this;
    }

    public TypescriptSdkGenerator setSdkFileName(String sdkFileName) {
        this.typescriptSdkFileName = sdkFileName;
        return this;
    }

    public TypescriptSdkGenerator register(Class<?> serviceClass) {
        this.serviceClasses.add(serviceClass);
        return this;
    }

    public TypescriptSdkGenerator setGenerationCondition(boolean executionCondition) {
        this.shouldExecute = executionCondition;
        return this;
    }

    public static class TypescriptSDKGeneratorException extends RuntimeException {
        public TypescriptSDKGeneratorException(String message) {
            super(message);
        }
    }

    public void generate() throws TypescriptSDKGeneratorException {
        if (!this.shouldExecute) {
            return;
        }
        List<Service> services = this.serviceClasses.stream()
                .map(Service::new)
                .toList();
        TypescriptSdkFileBuilder sdkBuilder = new TypescriptSdkFileBuilder(services);

        if (this.outputDirectory == null) {
            this.printServiceTypescriptFile(sdkBuilder);
            return;
        }
        this.writeServiceToTypescriptFile(sdkBuilder);
    }

    private void printServiceTypescriptFile(TypescriptSdkFileBuilder sdkBuilder) {
        System.out.println("// generating typescript sdk");
        System.out.println(sdkBuilder.build());
    }

    private void writeServiceToTypescriptFile(TypescriptSdkFileBuilder sdkBuilder) {
        try {
            Path fileName = Path.of(this.outputDirectory, this.typescriptSdkFileName);
            Files.deleteIfExists(fileName);
            Files.createDirectories(Path.of(this.outputDirectory));
            Files.createFile(fileName);
            Files.writeString(fileName, sdkBuilder.build());
        } catch (Exception e) {
            e.printStackTrace();
            throw new TypescriptSDKGeneratorException(e.getMessage());
        }
    }

    public static class TypescriptSdkFileBuilder {
        List<Service> services;
        List<JavaClass> additionalClasses;

        public TypescriptSdkFileBuilder(List<Service> services) {
            this.services = services;
            this.additionalClasses = new TypeFinder(services)
                    .getAdditionalClasses()
                    .stream()
                    .map(JavaClass::new)
                    .toList();
        }

        public String build() {
            return Constant.FILE_FORMAT
                    .replace(Constant.FileComponents.RPC_FUNCTION.name(), Constant.RPC_FUNCTION)
                    .replace(Constant.FileComponents.ADDITIONAL_CLASSES.name(), this.additionalClasses.stream()
                            .map(JavaClass::formatTypescriptInterface)
                            .collect(Collectors.joining("\n")))
                    .replace(Constant.FileComponents.INPUT_OUTPUT_INTERFACES.name(), this.services.stream()
                            .map(Service::formatIOInterfaces)
                            .collect(Collectors.joining("\n")))
                    .replace(Constant.FileComponents.SDK_CLASSES.name(), this.services.stream()
                            .map(Service::formatTypescriptSdkClass)
                            .collect(Collectors.joining("\n")))
                    .stripTrailing() + "\n";
        }
    }

    static class ClassFieldSet {
        Set<Field> fields = new HashSet<>();
        Set<Class<?>> classes = new HashSet<>();
    }

    public static class TypeFinder {
        List<Service> services;
        Set<Field> fields = new HashSet<>();
        Set<Class<?>> classes = new HashSet<>();

        public TypeFinder(List<Service> services) {
            this.services = services;
            this.findAllFields();
        }

        public Set<Class<?>> getAdditionalClasses() {
            Set<Class<?>> classes = this.fields.stream()
                    .filter(field -> !Types.isPrimitive(field))
                    .filter(field -> !Types.isParameterizedType(field))
                    .map(Field::getType)
                    .collect(Collectors.toSet());
            classes.addAll(this.classes);
            return classes;
        }

        public void findAllFields() {
            Set<Field> topLevelFields = this.services.stream()
                    .map(Service::getEndpoints)
                    .flatMap(Collection::stream)
                    .map(Endpoint::getInputAndOutputFieldSet)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
            this.fields.addAll(topLevelFields);
            int fieldTypesSize = -1;
            while (fieldTypesSize != this.fields.size()) {
                fieldTypesSize = this.fields.size();
                List<ClassFieldSet> classFieldSets = this.fields.stream()
                        .map(this::getNewFields)
                        .toList();
                classFieldSets.forEach(classFieldSet -> {
                    this.classes.addAll(classFieldSet.classes);
                    this.fields.addAll(classFieldSet.fields);
                });
            }
        }

        private ClassFieldSet getNewFields(Field field) {
            ClassFieldSet cf = new ClassFieldSet();
            switch (Types.getFieldType(field)) {
                case LIST -> {
                    Class<?> listType = Types.getListType(field);
                    Class<?> cls = Types.ClassForName(listType.getName());
                    if (Types.isPrimitive(cls)) {
                        return cf;
                    }
                    Field[] declaredFields = cls.getDeclaredFields();
                    cf.fields.addAll(List.of(declaredFields));
                    cf.classes.add(cls);
                    return cf;
                }
                case MAP -> {
                    Class<?> mapType = Types.getMapType(field);
                    Class<?> cls = Types.ClassForName(mapType.getName());
                    if (Types.isPrimitive(cls)) {
                        return cf;
                    }
                    Field[] declaredFields = cls.getDeclaredFields();
                    cf.fields.addAll(List.of(declaredFields));
                    cf.classes.add(cls);
                    return cf;
                }
                case PRIMITIVE -> {
                    return cf;
                }
                case OBJECT -> {
                    cf.fields.add(field);
                    Class<?> objectClass = Types.ClassForName(field.getType().getName());
                    cf.fields.addAll(List.of(objectClass.getDeclaredFields()));
                    return cf;
                }
                case ENUM -> {
                    return cf;
                }
                default -> throw new TypescriptSDKGeneratorException("");
            }
        }
    }

    public static class Service {
        String name;
        List<Endpoint> endpoints;

        Service(Class<?> clazz) {
            this.name = clazz.getSimpleName();
            this.endpoints = Arrays.stream(clazz.getDeclaredMethods())
                    .filter(Endpoint::isEndpoint)
                    .map(Endpoint::new)
                    .toList();
        }

        public List<Endpoint> getEndpoints() {
            return endpoints;
        }

        String formatIOInterfaces() {
            return String.join("\n", this.endpoints.stream().map(endpoint ->
                    String.format(
                            "%s\n%s",
                            endpoint.inputInterface.formatTypescriptInterface(),
                            endpoint.outputInterface.formatTypescriptInterface()
                    )).toList());
        }

        String formatTypescriptSdkClass() {
            String sdkMethods = String.join("", this.endpoints.stream().map(endpoint -> String.format(
                    Constant.SDK_METHOD,
                    endpoint.name,
                    endpoint.inputInterface.name,
                    endpoint.outputInterface.name,
                    this.name,
                    endpoint.name
            )).toList()).stripTrailing();
            return String.format(Constant.SDK_CLASS, this.name, sdkMethods);
        }
    }

    public static class Endpoint {
        String name;
        JavaClass inputInterface;
        JavaClass outputInterface;

        Endpoint(Method method) {
            Class<?> returnType = method.getReturnType();
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            int idx = findIndexOfRequestBodyParameter(parameterAnnotations);
            Class<?> requestBodyClass = method.getParameters()[idx].getType();
            this.name = method.getName();
            this.inputInterface = new JavaClass(requestBodyClass);
            this.outputInterface = new JavaClass(returnType);
        }

        public Set<Field> getInputAndOutputFieldSet() {
            Set<Field> fields = new HashSet<>();
            fields.addAll(Arrays.stream(inputInterface.clazz.getDeclaredFields()).toList());
            fields.addAll(Arrays.stream(outputInterface.clazz.getDeclaredFields()).toList());
            return fields;
        }

        private static int findIndexOfRequestBodyParameter(Annotation[][] parameterAnnotations) {
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (int j = 0; j < parameterAnnotations[i].length; j++) {
                    if (parameterAnnotations[i][j] instanceof RequestBody) {
                        return i;
                    }
                }
            }
            throw new RuntimeException("missing request body");
        }

        private static boolean isEndpoint(Method method) {
            return method.isAnnotationPresent(RequestMapping.class);
        }
    }

    private static class JavaClass {
        String name;
        Class<?> clazz;
        boolean isEnum;
        List<JavaField> fields;

        JavaClass(Class<?> clazz) {
            this.name = clazz.getSimpleName();
            this.clazz = clazz;
            this.isEnum = clazz.isEnum();
            if (isEnum) return;
            this.fields = Arrays.stream(clazz.getDeclaredFields())
                    .map(JavaField::new)
                    .toList();
        }

        String formatTypescriptEnum() {
            Object[] values = this.clazz.getEnumConstants();
            String valuesStr = Arrays.stream(values)
                    .map(Object::toString)
                    .map(s -> String.format(Constant.TYPESCRIPT_ENUM_VALUE, s, s))
                    .collect(Collectors.joining())
                    .stripTrailing();
            return String.format(Constant.TYPESCRIPT_ENUM, this.name, valuesStr);
        }

        String formatTypescriptInterface() {
            if (this.isEnum) {
                return this.formatTypescriptEnum();
            }
            return String.format(Constant.TYPESCRIPT_INTERFACE, this.name,
                    String.join("\n", fields
                            .stream()
                            .map(JavaField::toTypescriptFieldDeclaration)
                            .toList()));
        }
    }

    public static class JavaField {
        String name;
        Field field;

        JavaField(Field field) {
            this.name = field.getName();
            this.field = field;
        }

        String toTypescriptFieldDeclaration() {
            return switch (Types.getFieldType(this.field)) {
                case LIST -> String.format(
                        "    %s: %s[];",
                        this.name,
                        Types.getListType(this.field).getSimpleName());
                case MAP -> String.format(
                        "    %s: { [index: string]: %s };",
                        this.name,
                        Types.getMapType(this.field).getSimpleName());
                case PRIMITIVE -> String.format(
                        "    %s: %s;",
                        this.name,
                        JavaToTypescriptTypeMap.get(this.field.getType().getSimpleName())
                );
                case OBJECT -> String.format(
                        "    %s: %s;",
                        this.name,
                        this.field.getType().getSimpleName());
                case ENUM -> String.format(
                        "    %s: %s;",
                        this.name,
                        this.field.getType().getSimpleName());
            };
        }
    }

    interface Constant {

        enum FileComponents {
            RPC_FUNCTION,
            ADDITIONAL_CLASSES,
            INPUT_OUTPUT_INTERFACES,
            SDK_CLASSES,
        }

        String FILE_FORMAT = """
                ADDITIONAL_CLASSES
                INPUT_OUTPUT_INTERFACES
                RPC_FUNCTION
                SDK_CLASSES
                """;

        String RPC_FUNCTION = """
                async function executeRPC<T>(headers: HeadersInit, host: string, endpoint: string, t: T) {
                    const response = await fetch(`${host}${endpoint}`, {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json",
                            ...headers
                        },
                        body: JSON.stringify(t),
                    });
                    if (response.status !== 200) {
                        throw new Error(response.body?.toString());
                    }
                    return response.json();
                }
                """;

        String TYPESCRIPT_INTERFACE = """
                export interface %s {
                %s
                }
                """;

        String TYPESCRIPT_ENUM = """
                export enum %s {
                %s
                }
                """;

        String TYPESCRIPT_ENUM_VALUE = """
                    %s = "%s",
                """;

        String SDK_CLASS = """
                export class %s {
                    host: string;
                    headers: HeadersInit;
                    
                    constructor(host: string, headers: HeadersInit) {
                        this.host = host;
                        this.headers = headers;
                    }
                    
                %s
                }
                """;

        String SDK_METHOD = """
                    %s = async (input: %s): Promise<%s> => {
                        return executeRPC(this.headers, this.host, "/%s/%s", input);
                    };
                """;
    }

    private static class Types {

        enum FieldType {
            LIST,
            MAP,
            PRIMITIVE,
            OBJECT,
            ENUM,
        }

        public static FieldType getFieldType(Field field) {
            if (isParameterizedType(field)) {
                if (isList(field)) return FieldType.LIST;
                if (isMap(field)) return FieldType.MAP;
                throw new TypescriptSDKGeneratorException("unknown parameterized type");
            }
            if (isPrimitive(field)) return FieldType.PRIMITIVE;
            if (isEnum(field)) return FieldType.ENUM;
            return FieldType.OBJECT;
        }

        public static Class<?> ClassForName(String name) {
            try {
                return Class.forName(name);
            } catch (Exception e) {
                throw new TypescriptSDKGeneratorException(e.getMessage());
            }
        }

        public static boolean isParameterizedType(Field field) {
            return field.getGenericType() instanceof ParameterizedType;
        }

        public static boolean isList(Field field) {
            if (field.getGenericType() instanceof ParameterizedType parameterizedType) {
                return List.class.isAssignableFrom((Class<?>) parameterizedType.getRawType());
            }
            throw new IllegalStateException();
        }

        public static Class<?> getListType(Field field) {
            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            assert parameterizedType.getActualTypeArguments().length == 1;
            String parameterizedTypeName = parameterizedType.getActualTypeArguments()[0].getTypeName();
            return Types.ClassForName(parameterizedTypeName);
        }

        public static boolean isMap(Field field) {
            if (field.getGenericType() instanceof ParameterizedType parameterizedType) {
                return Map.class.isAssignableFrom((Class<?>) parameterizedType.getRawType());
            }
            throw new IllegalStateException();
        }

        public static Class<?> getMapType(Field field) {
            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
            Type[] mapParams = parameterizedType.getActualTypeArguments();
            assert mapParams.length == 2;
            if (!String.class.isAssignableFrom(ClassForName(mapParams[0].getTypeName()))) {
                throw new TypescriptSDKGeneratorException("map indexes must be of type String");
            }
            return ClassForName(mapParams[1].getTypeName());
        }

        public static boolean isEnum(Field field) {
            return field.getType().isEnum();
        }

        public static boolean isPrimitive(Field field) {
            return JavaToTypescriptTypeMap.containsKey(field.getType().getSimpleName());
        }

        public static boolean isPrimitive(Class<?> cls) {
            return JavaToTypescriptTypeMap.containsKey(cls.getSimpleName());
        }
    }
}
