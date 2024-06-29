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

Example `RestController`:
```
@RestController
@RequestMapping("/StudentService")
public class StudentService {

    Logger log = LoggerFactory.getLogger(StudentService.class);

    @RequestMapping("/CreateStudent")
    public CreateStudentOutput CreateStudent(@RequestBody CreateStudentInput input) {
        log.info("creating student {}", input);
        return new CreateStudentOutput(
                Outcome.OK,
                new CreateStudentOutput.CreateStudentOutputUser(0L, "", "")
        );
    }

    @RequestMapping("/DeleteStudent")
    public DeleteStudentOutput DeleteStudent(@RequestBody DeleteStudentInput input) {
        return new DeleteStudentOutput();
    }

    @RequestMapping("/FindUser")
    public FindUserOutput FindUser(@RequestBody FindUserInput input) {
        return new FindUserOutput(null);
    }

}
```
