# Security policy

Pay Relay handles payment messages, so security reports matter a lot.

## Reporting a problem

Please **do not** open a public issue. Use GitHub's private reporting instead: **Security → Report a vulnerability** on this repository. Include what you found, how to reproduce it, and what an attacker could do with it.

You can expect a first answer within a few days. Fixed issues are credited in the release notes unless you prefer otherwise.

## Scope

In scope: anything that lets a message other than a genuine wallet "money received" SMS reach the server, leaks messages or the key, or lets another app on the phone control Pay Relay.

Out of scope: problems in the server that receives the messages (report those to that project), and attacks that need the phone to be unlocked and in the attacker's hands.

## Advice for operators

- Use a long random key (`openssl rand -hex 32`) and HTTPS only.
- Use the wallet phone for this job alone, keep it updated, and protect it with a screen lock.
- Your server should still check the amount and wallet against the order. An SMS proves money arrived, the order match is your server's job.
