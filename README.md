# AuthGate

AuthGate is a GUI account registration and login mod for Minecraft Forge 1.21.4.
It stores server-side accounts and password hashes, limits unauthenticated player
actions, and provides password management commands.

## Requirements

- Minecraft 1.21.4
- Minecraft Forge 54.1.22
- Java 21

Install the same AuthGate JAR on the server and on each connecting client. Build
the JAR from this project with `gradlew.bat build`; the result is written to
`build/libs/`.

## Single-player testing

Single-player authentication is disabled by default. After launching the game
and creating a world once, edit
`<world>/serverconfig/authmod-server.toml` and set:

```toml
requireLoginInSinglePlayer = true
```

## Server data and commands

Account data is stored in the world directory at `authmod/accounts.json`.

- `/changepassword` opens the password change screen.
- `/authadmin reset <player>` resets an account (permission level 2).
- `/authadmin unlock <player>` clears a login lock (permission level 2).
- `/authadmin info <player>` shows failed attempts and remaining lock time (permission level 2).

AuthGate provides password authentication for this modded server; it does not
authenticate Microsoft or Mojang accounts.

## Credits

Author and creator: **Heyun1018**.
