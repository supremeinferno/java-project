<div align="center">

<img src="web/favicon.svg" alt="Banking System logo" width="72">

# Banking System

**A bank management web application with a dependency-free Java backend and a 3D interface built with three.js.**

[![Live demo](https://img.shields.io/badge/demo-live-0f766e?style=for-the-badge)](https://java-project-3umo.onrender.com)
[![Java](https://img.shields.io/badge/Java-8%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org)
[![three.js](https://img.shields.io/badge/three.js-r160-000000?style=for-the-badge&logo=threedotjs&logoColor=white)](https://threejs.org)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](Dockerfile)

[Live demo](https://java-project-3umo.onrender.com) · [Project report](Bank.pdf) · [Report a bug](https://github.com/supremeinferno/java-project/issues)

<img src="screenshot/web-dashboard.png" alt="Dashboard" width="100%">

</div>

## Table of contents

- [Overview](#overview)
- [Features](#features)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Deployment](#deployment)
- [Screenshots](#screenshots)
- [Architecture](#architecture)
- [API reference](#api-reference)
- [Project structure](#project-structure)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

## Overview

Banking System automates the everyday work of a bank branch. Staff sign in and can open accounts, move money and review every transaction. All rules (minimum balances, withdrawal limits, valid amounts) are enforced on the server, and every change is saved immediately.

The backend uses only the Java standard library: the HTTP server built into the JDK and Java serialization for storage. The front end is plain HTML, CSS and JavaScript modules with no build step, so the project runs anywhere Java runs.

> **Live demo:** https://java-project-3umo.onrender.com
> Hosted on Render's free plan: the first request after a period of inactivity takes about a minute while the server wakes, and data is reset whenever the server restarts.

## Features

**Accounts**
- Open Savings, Current and Student accounts; the form adapts to the details each type needs
- Account page with a card, balance, rules, a 5% interest projection and full history
- Search, sort and filter by type; export the list to CSV
- Close an account with confirmation; the remaining balance is paid out and the number is never reused

**Transactions**
- Deposit, withdraw and transfer between accounts
- Live preview of the account holder and the available balance while typing an account number
- Complete activity log for the whole bank, filterable by kind

**Dashboard**
- Total holdings, account count, average balance and today's activity
- Money in & out chart for the last 14 days and holdings by account type
- Interactive 3D view: one tower per account, sized by balance; drag to rotate, click to open

**Experience**
- Animated 3D login card that flies in, flips and can be spun with the mouse
- Show / hide password on the login form
- Quick account search from anywhere (press `/`)
- Light and dark themes and a responsive layout for phones
- Works without WebGL, just without the 3D parts

### Account rules

| Type | Minimum balance | Required when opening | Maximum per withdrawal |
|---|---|---|---|
| Savings | ₹2,000 | Maximum withdraw limit | Set when opening |
| Current | ₹5,000 | Trade licence number | No limit |
| Student | ₹100 | Institution name | ₹20,000 |

Withdrawals and transfers can never take an account below its minimum balance.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 8+, `com.sun.net.httpserver` (built into the JDK) |
| Storage | Java serialization to a local `data` file, written atomically |
| Front end | HTML, CSS, JavaScript (ES modules), no build step |
| 3D graphics | [three.js](https://threejs.org) r160, bundled in `web/vendor` |
| Hosting | Docker, [Render](https://render.com) |

## Getting started

### Prerequisites

- Java Development Kit 8 or newer (`java -version`)

### Run locally

```bash
git clone https://github.com/supremeinferno/java-project.git
cd java-project
javac -d bin $(find src -name '*.java')
java -cp bin WebApplication
```

The browser opens at http://localhost:8080. Sign in with `admin` / `admin` (see [Configuration](#configuration) to change it). Pass a port number to use another port: `java -cp bin WebApplication 9000`.

**VS Code:** open the folder with the *Extension Pack for Java* and run **Banking Website** from the Run and Debug panel.

> Always open the site through the Java server. Opening `web/index.html` directly or through a static server such as Live Server doesn't work, because the pages need the server's API.

## Configuration

All settings are environment variables:

| Variable | Default | Description |
|---|---|---|
| `ADMIN_USER` | `admin` | Login username |
| `ADMIN_PASSWORD` | `admin` | Login password. **Always set this on a public server.** |
| `PORT` | `8080` | Port to listen on |
| `HOST` | localhost only | Interface to bind; the Docker image uses `0.0.0.0` |

Example: `ADMIN_PASSWORD=change-me java -cp bin WebApplication`

## Deployment

The repository includes a multi-stage `Dockerfile` and a Render blueprint (`render.yaml`).

**Render**
1. Sign in to [Render](https://render.com) with GitHub.
2. Choose **New → Blueprint** and select this repository.
3. Enter an `ADMIN_PASSWORD` when prompted and click **Apply**.

Every push to `main` redeploys automatically. To change the password later, edit `ADMIN_PASSWORD` in the service's **Environment** tab.

**Any Docker host**

```bash
docker build -t banking-system .
docker run -p 8080:8080 -e ADMIN_PASSWORD=change-me banking-system
```

> On Render's free plan the file system is not persistent, so accounts are lost when the service restarts or redeploys.

## Screenshots

| Login | Dashboard (dark theme) |
|---|---|
| ![Login](screenshot/web-login.png) | ![Dashboard, dark theme](screenshot/web-dashboard-dark.png) |
| **Accounts** | **Account details** |
| ![Accounts](screenshot/web-accounts.png) | ![Account details](screenshot/web-account.png) |
| **Transfer** | **Activity** |
| ![Transfer](screenshot/web-transfer.png) | ![Activity](screenshot/web-activity.png) |
| **Open an account** | **Mobile** |
| ![Open an account](screenshot/web-open.png) | <img src="screenshot/web-mobile-dashboard.png" alt="Mobile dashboard" width="48%"> <img src="screenshot/web-mobile-account.png" alt="Mobile account page" width="48%"> |

<sub>Screenshots show demo data.</sub>

## Architecture

```
Browser ──HTTP/JSON──▶ WebServer ──▶ Bank (domain model) ──▶ FileIO ──▶ data file
 web/                  src/Web        src/Bank                src/Data
```

| Package | Responsibility |
|---|---|
| `src/Bank` | Domain model. `Bank` holds the accounts in a map and records every change as a `Transaction`. `BankAccount` is the abstract base of `SavingsAccount`, `CurrentAccount` and `StudentAccount`. `AccountType` holds each type's rules, so a new type needs only an enum entry and a subclass. |
| `src/Exceptions` | Checked exceptions: `AccNotFound`, `InvalidAmount`, `MaxBalance` (below minimum balance), `MaxWithdraw` (over the limit). |
| `src/Data` | `FileIO` loads and saves the bank, writing to a temporary file first so a failed save never corrupts existing data. |
| `src/Web` | `WebServer` serves the front end and the JSON API and checks the session on every request. `WebApplication` is the entry point. |
| `web` | Front end: `app.js` (pages and routing), `chart.js` (money in & out chart), `scene3d.js` (three.js scenes). |

**Security:** sessions use random 192-bit tokens in `HttpOnly`, `SameSite=Strict` cookies, marked `Secure` behind HTTPS. Static file paths are normalized and confined to the `web` folder, request bodies are size-limited, and all user content is escaped before it's rendered.

## API reference

| Method | Endpoint | Parameters | Description |
|---|---|---|---|
| `POST` | `/api/login` | `username`, `password` | Sign in and receive a session cookie |
| `POST` | `/api/logout` | | Sign out |
| `GET` | `/api/types` | | Account types and their rules |
| `GET` | `/api/bank` | | All accounts and transactions |
| `POST` | `/api/accounts` | `type`, `name`, `balance`, `extra` | Open an account |
| `POST` | `/api/deposit` | `account`, `amount` | Deposit |
| `POST` | `/api/withdraw` | `account`, `amount` | Withdraw |
| `POST` | `/api/transfer` | `from`, `to`, `amount` | Transfer between accounts |
| `POST` | `/api/close` | `account` | Close an account |

Parameters are sent as `application/x-www-form-urlencoded`. Every endpoint except `login` and `types` needs a session. Errors return `{"error": "message"}` with status `400`, `401` or `404`.

## Project structure

```
.
├── src/
│   ├── WebApplication.java   entry point
│   ├── Bank/                 domain model
│   ├── Data/                 persistence
│   ├── Exceptions/           checked exceptions
│   └── Web/                  HTTP server and API
├── web/                      front end and bundled three.js
├── screenshot/               README images
├── Bank.pdf                  project report
├── Dockerfile                container build
└── render.yaml               Render blueprint
```

## Roadmap

- [ ] Persistent database (PostgreSQL or MySQL) so data survives restarts
- [ ] Multiple staff accounts with roles and hashed passwords
- [ ] Monthly interest for Savings and Student accounts
- [ ] Printable statements and transaction receipts
- [ ] Custom domain for the live site

## Contributing

Contributions are welcome.

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m "Add my feature"`
4. Push the branch: `git push origin feature/my-feature`
5. Open a pull request

## License

This project is for educational purposes. three.js is distributed under the [MIT License](web/vendor/three/LICENSE).

## Acknowledgments

- [three.js](https://threejs.org) for 3D rendering
- [Shields.io](https://shields.io) for the badges
