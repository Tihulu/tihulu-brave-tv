# Contributing

1. Keep changes narrowly scoped to TV support.
2. Run `./scripts/check.sh` before every commit.
3. Add a regression test for patcher/state bugs.
4. Do not copy large portions of Brave/Chromium into this repository.
5. Preserve upstream licenses and notices.
6. Do not weaken browser security to gain compatibility.
7. For runtime/input changes, include the exact TV/device and packaged APK test result in the pull request.

## Commit style

Use short imperative subjects, for example:

- `Add TV cursor input dispatcher`
- `Fix cursor bounds after window resize`
- `Update Brave manifest patch anchor`

Bug-fix commits should describe the reproduced failure in the body when it is not obvious from the test.
