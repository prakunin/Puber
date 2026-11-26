# Review antigravity, round 2

ANTIGRAVITY: ran (one run, exit 0; diff in the prompt). Reviewer also read the prod-affecting changes.
CONFIRMED:
- [minor] baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt:181-189 - dnsResponse checks only size > 12 before reading query[offset+1] and query[offset+2] and assumes the label walk ended on a 0 byte; a truncated body throws IndexOutOfBounds in the mock dispatcher instead of 400. Only a hand-built truncated POST triggers it; test-only robustness gap; does not block.
REJECTED:
- BaselineProfileGenerator.kt:28-33 checkLate in @Before of test N+1 blames the wrong test and skips resets - intended; the run is already failed.
- Production DNS resolver selection changed - unpinned DNS_RESOLVER = DohResolver.GOOGLE (same URL, bootstrap 8.8.4.4/8.8.8.8), pinned by googleResolver_isDnsGoogleWithBootstrap8844And8888 and unpinnedEndpoint_buildsTheAppClientWithDoh; pin without resolver -> system DNS.
- Production update-check URL changed - prod binding AppUpdateRepository(get()) defaults to https://api.github.com; same URL; pinned in AppUpdateRepositoryTest and KinoPubApiClientGitHubReleaseTest.
- Removing the pin guard in autoResolveWorkingDomain can switch away from a pin - candidates are only the pinned endpoint; switchToFound writes only if the domain differs; unpinned list unchanged; tests cover both probe answers.
- runtimeModules() override of IAppUpdateRepository duplicate definition - appended after prod modules (PuberApp.kt:112), instrumentation only.
VERDICT: clean
