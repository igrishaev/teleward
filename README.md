# Teleward

A Telegram captcha bot written in Clojure and compiled with GraalVM native
image. Runs on bare Linux/MacOS with no requirements. Fast and robust.

## Table of Contents

<!-- toc -->

- [Why](#why)
- [Features](#features)
- [Algorithm](#algorithm)
- [Java version](#java-version)
- [Binary version, Linux](#binary-version-linux)
- [Binary version, MacOS](#binary-version-macos)
- [Setting Up Your Bot](#setting-up-your-bot)
- [Configuration](#configuration)
- [Deploying on bare Ubuntu](#deploying-on-bare-ubuntu)
- [Health check](#health-check)
- [Further work](#further-work)

<!-- tocstop -->

## Why

To protect your Telegram channels from spam, obviously. Modern spammers are
smart: they don't use standard bots. Instead, they register ordinary accounts
and automate them with Selenium + web-version of Telegram (that's my
guess). Personally, I found Shieldy and other bots useless when dealing with such
kind of spammers. This bot aims the goal to finish that mess.

Another reason I opened this project for is to try my skills in developing
Clojure applications with GraalVM. My last experience with CLI client for
Exoscale was positive, so I decided to move further. Binary applications built
with Clojure are nice: they are fast, and they don't need installing JDK. At the
same time, they're are still Clojure: REPL is here, and that's amazing.

## Features

- This is Clojure, so you have REPL! During development, you call Telegram API
  directly from REPL and see what's going on.
- The bot can be delivered either as a Jar file or a binary file (with Graal).
- When Graal-compiled, needs no requirements (Java SDK, etc). The binary size is
  about 30 Mb.
- At the moment, supports only long polling strategy to obtain messages. The
  webhook is to be done soon.
- Keeps all the state in memory and thus doesn't need any kind of a
  database. The only exception is the current offset value which is tracked in a
  file.
- Supports English and Russian languages.
- Two captcha styles: normal "1 + 2" and Lisp captcha "(+ 1 2)".
- The `+`, `-`, and `*` operators are corresponding Unicode characters that
  prevent captcha from naive evaluation.

## Algorithm

The bot listens for all the messages in a group. Once a new pack of messages
arrives, the bot applies the following procedure to each message:

- Mark new members as locked.
- Send a captcha message to all members.
- Unless an author of a message is locked, delete that message.
- If a message is short and matches the captcha's solution, unlock a user and
  delete the catpcha message.
- If a locked user has posted three messages with no solution, ban them.
- If a locked user hasn't solved captcha in time, ban them as well.

*Please note:* the bot processes only messages no older than two minutes from
now. In other words, the bot is interested in what is happening now (with a
slight delay), but not in the far past. This is to prevent a sutuation what a
bot had been inactive and then has started to consume messages. Without this
condition, it will send captcha to chat members who have already joined and
confuse them.

## Java version

To make a Jar artefact, run:

```bash
make uberjar
```

The `uberjar` target calls `lein uberjar` and also injects the `VERSION` file
into it. The output file is `./target/teleward.jar`.

## Binary version, Linux

Linux version is built inside a Docker image, namely the
`ghcr.io/graalvm/graalvm-ce` one with `native-image` extension preinstalled. Run
the following command:

```bash
make docker-build
```

The output binary file appears at `./target/teleward`.

## Binary version, MacOS

- [Install GraalVM](https://www.graalvm.org/docs/getting-started/) locally.

- Install the "native image" extension:

```bash
gu install native-image
```

- Then `make` the project:

```bash
make
```

## Setting Up Your Bot

- To run the bot, first you need a token. Contact `@BotFather` in Telegram to
  create a new bot. Copy the token and don't share it.

- Add your new bot into a Telegram group. Promote it to admins. At least the bot
  must be able to 1) send messages, 2) delete messages, and 3) ban users.

- Run the bot locally:

```bash
teleward -t <telegram-token> -l debug
```

If everything is fine, the bot will start consuming messages and print them in
console.

## Configuration

See the version with `-v`, and help with `-h`. The bot takes into account plenty
of settings, yet not all of them are available for configuration for now. Below,
we name the most important parameters you will need.

- `-t, --telegram.token`: the Telegram token you obtain from
  BotFather. Required, can be set via an env variable `TELEGRAM_TOKEN`.

- `-l, --logging.level`: the logging level. Can be `debug, info, error`. Default
  is `info`. In production, most likely you will set `error`.

- `--telegram.offset-file`: where to store offset number for the next
  `getUpdates` call. Default is `TELEGRAM_OFFSET` in the current working
  directory.

- `--lang`: the language for messages. Can be `en, ru`, default is `ru`.

- `--captcha.style`: a type of captcha. When `lisp`, the captcha looks like `(+
  4 3)`. Any other value type will produce `4 + 3`. The operator is taken
  randomly.

Example:

```bash
./target/teleward -t <...> -l debug \
  --lang=en --telegram.offset-file=mybot.offset \
  --captcha.style=normal
```

For the rest of the config, see the `src/teleward/config.clj` file.

## Deploying on bare Ubuntu

- Buy the cheapest VPS machine and SSH to it.

- Create a user:

```bash
sudo useradd -s /bin/bash -d /home/ivan/ -m -G sudo ivan
sudo passwd ivan
mkdir /home/ivan/teleward
```

- Compile the file locally and copy it to the machine:

```bash
scp ./target/teleward ivan@hostname:/home/ivan/teleward/
```

- Create a new `systemctl` service:

```bash
sudo mcedit /etc/systemd/system/teleward.service
```

- Paste the following config:

```
[Unit]
Description = Teleward bot
After = network.target

[Service]
Type = simple
Restart = always
RestartSec = 1
User = ivan
WorkingDirectory = /home/ivan/teleward/
ExecStart = /home/ivan/teleward/teleward -l debug
Environment = TELEGRAM_TOKEN=xxxxxxxxxxxxxx

[Install]
WantedBy = multi-user.target
```

- Enable autoload:

```bash
sudo systemctl enable teleward
```

- Manage the service with commands:

```bash
sudo systemctl stop teleward
sudo systemctl start teleward
sudo systemctl status teleward
```

For Jar, the config file would be almost the same except the `ExecStart`
section. There, you specify something like `java -jar teleward.jar ...`.

## Health check

The bot accepts the `/health` command which it replies to "OK".

## Further work

- Implement webhook.
- Add tests.
- Report uptime for `/health`.
- More config parameters via CLI args.

&copy; 2022 Ivan Grishaev
