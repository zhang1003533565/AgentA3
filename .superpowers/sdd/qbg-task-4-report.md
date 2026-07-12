# Question Bank Generation Task 4 Report

## Status

- Implemented administrator-only question generation HTTP endpoints.
- Added `GET /api/exam/question-generation/options`.
- Added multipart `POST /api/exam/question-generation/generate` for text and file sources.
- Authorization is passed unchanged to `QuestionGenerationService` and is not logged.

## TDD Evidence

### RED

Command:

```text
cd AppBackend && mvn -Dtest=QuestionGenerationControllerTest test
```

Result: build failed during test compilation because `QuestionGenerationController` did not exist. This was the expected missing-feature failure.

### GREEN

Controller-only command passed with 7 tests, 0 failures, 0 errors, 0 skipped.

Required Task 1-4 combination:

```text
cd AppBackend && mvn -Dtest=QuestionGenerationControllerTest,QuestionGenerationMaterialParserTest,QuestionGenerationServiceImplTest,PythonAiProxyServiceTest test
```

Result: 49 tests, 0 failures, 0 errors, 0 skipped.

## Files

- `AppBackend/src/main/java/com/example/appbackend/controller/QuestionGenerationController.java`
- `AppBackend/src/test/java/com/example/appbackend/controller/QuestionGenerationControllerTest.java`
- `.superpowers/sdd/qbg-task-4-report.md`

## Commit

- `feat: 提供管理员题库生成接口`

## Self-review

- Both routes reject every role other than exact `ADMIN` with HTTP 403 before service interaction.
- Invalid source type, question type, and non-positive maximum return HTTP 400 with zero service interaction.
- Text and DOCX multipart requests are mapped into `GenerationCommand` without controller-side content transformation.
- The `Authorization` header is handed unchanged to both service methods.
- The controller contains no logger and does not write authorization data.
- No frontend files or unrelated production files were changed.

## Review Fixes

- Preserved compatibility with the frontend's legacy `sourceType=file`, but normalized it at the HTTP boundary from the uploaded filename extension to the concrete parser type `txt` or `docx`; unsupported extensions are rejected with HTTP 400.
- Direct `sourceType=txt` and `sourceType=docx` requests are accepted only when the uploaded filename extension matches. The service never receives `sourceType=file`.
- Added controller-side material validation: blank text, missing files, empty files, unsupported extensions, and declared-type/extension mismatches return HTTP 400 before service interaction.
- Restricted difficulty to blank, `easy`, `medium`, or `hard`; blank is normalized to null and invalid values return HTTP 400 before service interaction.
- Trimmed `sourceTitle` before constructing the command and rejected titles longer than 160 characters after trimming.
- Expanded the real MockMvc command-capture suite from 7 to 14 tests, including concrete TXT/DOCX source types and the legacy file compatibility path.

### Review TDD Evidence

The expanded controller suite first failed in 8 tests against the reviewed implementation: it exposed `file` reaching the service, missing material validation, unsupported/mismatched extensions being accepted, unrestricted difficulty, and untrimmed/unbounded titles. After the boundary fix, the controller suite passed 14/14.

The final Task 1-4 combination passed 56 tests with 0 failures, 0 errors, and 0 skipped.

## Final Review Fix: Filename Whitespace Consistency

- Added a MockMvc regression for `course.txt ` that requires HTTP 400 and no interaction with `QuestionGenerationService`.
- RED: the focused test failed with `Status expected:<400> but was:<200>`, proving the controller accepted a filename that `QuestionGenerationMaterialParser` would reject.
- Removed controller-side filename trimming before case-insensitive extension extraction. Uppercase extensions remain accepted, while trailing whitespace is no longer hidden from validation.
- GREEN: `QuestionGenerationControllerTest` passed 15 tests with 0 failures and 0 errors.
- Task 1-4 combination passed 57 tests with 0 failures and 0 errors using the explicit Byte Buddy Java agent; the combined test requires local loopback sockets for `PythonAiProxyServiceTest`.
