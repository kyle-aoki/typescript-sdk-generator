Generates a typescript file based on Spring `RestController` methods.

Example:
```
new TypescriptSdkGenerator()
        .setGenerationCondition(activeProfile == null || activeProfile.isEmpty() || activeProfile.equals("dev"))
        .setOutputDirectory("./typescript-sdk")
        .setSdkFileName("demo-api.ts")
        .extendTypeMap("Integer", "number")
        .extendTypeMap("Float", "number")
        .register(StudentService.class)
        .register(StudentServiceV2.class)
        .generate();
```
