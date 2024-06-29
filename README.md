Generates a typescript file based on Spring `RestController` methods. Not production ready.

Example:
```
new TypescriptSdkGenerator()
        .setGenerationCondition(activeProfile == null || activeProfile.isEmpty() || activeProfile.equals("dev"))
        .setOutputDirectory("./typescript-sdk")
        .setSdkFileName("demo-api.ts")
        .extendTypeMap("LocalDate", "number")
        .register(StudentService.class)
        .register(EnrollmentService.class)
        .generate();
```

Example `RestController`:
```
@RestController
@RequestMapping("/StudentService")
public class StudentService {

    @RequestMapping("/CreateStudent")
    public CreateStudentOutput CreateStudent(@RequestBody CreateStudentInput input) {
        return null;
    }

    @RequestMapping("/DeleteStudent")
    public DeleteStudentOutput DeleteStudent(@RequestBody DeleteStudentInput input) {
        return null;
    }

    @RequestMapping("/FindUser")
    public FindUserOutput FindUser(@RequestBody FindUserInput input) {
        return null;
    }

}
```

Example typescript output:
```
// ...
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
// ...
```
