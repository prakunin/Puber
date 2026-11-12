LENS: blind  ROUND: 1  BASE: 4db9c0d4

### F1 [nit] Converting `gradlew.bat` to CRLF rewrites the whole file, and no `.gitattributes` keeps it that way
- Where: /home/user1/orca/workspaces/Puber/dependency-update-stable/gradlew.bat:1-112
- Problem: The base blob had LF line endings. The new blob has CRLF (`git show HEAD:gradlew.bat | file -`). Because of that, the diff replaces all 82 old lines instead of showing the wrapper-template change. The real change is small: the safety-net `goto`, `1>&2 echo` and the extra `goto exitWithErrorLevel`. The repo has no `.gitattributes`, so the line endings of this file now depend on the `core.autocrlf` setting of whoever regenerates the wrapper next.
- Failure scenario: A later wrapper bump made on an LF-normalising setup turns the file back to LF. That produces another whole-file diff, and a reviewer cannot see what the template actually changed. Behaviour is fine: CRLF is the correct form for a `.bat` file.
- Fix: Add `.gitattributes` with `*.bat text eol=crlf` (and `gradlew text eol=lf`) in this change, so this one-time conversion stays fixed.

I checked these and found no problem:
- **Release signing fallback** (`app/build.gradle.kts:173-178`): `app/debug.jks` is tracked and opens with store password `android`. Its single entry is `androiddebugkey`, so the hardcoded credentials match the keystore and are the public debug ones, not secrets.
- **Silent debug signing in CI:** a missing secret cannot make CI release a debug-signed prod APK. The "Validate release secrets" step in `.github/workflows/release.yml` fails first.
- **Plugin versions:** the Kotlin compose and serialization plugins and the AGP and `com.android.test` plugins all take their versions from the bumped catalog refs, so their versions stay in step.

### Unproven
- /home/user1/orca/workspaces/Puber/dependency-update-stable/gradle/wrapper/gradle-wrapper.jar: the binary was replaced and I did not check its checksum against the official 9.9.1 wrapper. It becomes a finding only if its sha256 differs from the one Gradle publishes. CI's `gradle/actions/setup-gradle@v5` validates the wrapper by default, which lowers the risk.
- /home/user1/orca/workspaces/Puber/dependency-update-stable/app/src/main/generated/baselineProfiles/: after the library bumps, the startup profile rules may no longer match the renamed library methods. That would show as slower startup measured on a device, not as a build or runtime failure.
