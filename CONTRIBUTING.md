# Contributing

Contributions are welcome. Naviamp is a music player for self-hosted libraries, so changes should keep the app reliable, approachable, and consistent across supported platforms.

## Good Contributions

- Bug fixes with a clear description of the problem and the fix.
- Small feature improvements that fit the existing app direction.
- UI polish that makes the app easier to use without adding unnecessary complexity.
- Platform fixes for macOS, Windows, Linux, or Android.
- Tests for shared domain logic, provider mapping, playback behavior, or settings behavior.

## Before Opening a Pull Request

- Keep the change focused.
- Avoid committing build artifacts, local configuration, signing files, secrets, or generated output.
- Run the smallest relevant checks you can before submitting.
- Mention what you tested manually, especially for playback, platform-specific behavior, or UI changes.

Useful commands:

```shell
make desktop-test
make android-debug
./gradlew check
```

## Project Expectations

- Keep provider-specific behavior inside provider modules where practical.
- Keep business logic out of UI code when it can live in shared domain code.
- Prefer clear, readable code over clever code.
- Match the existing style before introducing new patterns.
- Treat desktop and Android differences as bugs unless there is a real platform reason.

## Security

Do not open public issues or pull requests containing credentials, server URLs with private tokens, signing keys, keystores, or other secrets. If you find a security issue, report it privately to the maintainer.

## License

By contributing to Naviamp, you agree that your contribution will be licensed under the GNU General Public License v3.0.
