# University TA Help Desk

The first shared platform slice is a dependency-free Java HTTP service. It provides
account sessions, course memberships, and a server-authorized course forum. Run
`./scripts/test.sh` for the complete local verification suite, or
`./scripts/run-local.sh` to start it at `http://localhost:8080`.

Copy `.env.example` to `.env` only when changing local settings. Never commit a
real session or application secret.
