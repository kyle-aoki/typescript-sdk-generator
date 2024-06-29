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

Example typescript output:
```
...
export interface FindUserOutput {
    user: User;
}

export interface FindUserInputV2 {
    userId: number;
}

export interface FindUserOutputV2 {
    user: User;
}

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

export class StudentService {
    host: string;
    headers: HeadersInit;

    constructor(host: string, headers: HeadersInit) {
        this.host = host;
        this.headers = headers;
    }

    CreateStudent = async (input: CreateStudentInput): Promise<CreateStudentOutput> => {
        return executeRPC(this.headers, this.host, "/StudentService/CreateStudent", input);
    };
    DeleteStudent = async (input: DeleteStudentInput): Promise<DeleteStudentOutput> => {
        return executeRPC(this.headers, this.host, "/StudentService/DeleteStudent", input);
    };
    FindUser = async (input: FindUserInput): Promise<FindUserOutput> => {
        return executeRPC(this.headers, this.host, "/StudentService/FindUser", input);
    };
}
...
```
