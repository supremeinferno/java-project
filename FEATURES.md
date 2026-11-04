# Banking Management System: Implemented Features

This document lists what the project implements so far and where each part lives in the code.

> **Role-based access control (RBAC)** is implemented: there are three roles (Administrator, Bank employee and Customer), and permissions are checked on every request. See [section 3](#3-login-roles-and-security).

---

## 1. OOP principles (`src/Bank/`)

| Principle | Where it is used |
|---|---|
| **Abstraction** | `BankAccount` is an `abstract` class with the abstract methods `getType()` and `getDetails()`. |
| **Inheritance** | `SavingsAccount` and `CurrentAccount` extend `BankAccount`. `StudentAccount` extends `SavingsAccount`, so the hierarchy has more than one level. |
| **Polymorphism (overriding)** | `SavingsAccount.withdraw()` overrides the base method to add a withdraw-limit check, then calls `super.withdraw()`. Each subclass overrides `getType()` and `getDetails()`. `StudentAccount` overrides `getInterestRate()` (4% instead of the savings 5%). |
| **Interfaces** | `InterestBearing` (Savings and Student accounts) and `FeeCharging` (Current accounts). `Bank.runMonthEnd()` works with any account through these interfaces, so a new account type only has to implement one to take part. `InterestBearing.getMonthlyInterest()` is a Java 8 `default` method shared by every implementation. |
| **Encapsulation** | All fields are `private` (most are `final`), and access goes through getters. Balance can only change through `deposit()` and `withdraw()`, which validate the amount. `Bank` only returns read-only views (`Collections.unmodifiableList` and `unmodifiableCollection`). |
| **Constructor chaining** | `super(...)` and `this(...)` calls, including a `protected` constructor in `SavingsAccount` that `StudentAccount` uses to set its own minimum balance. |
| **Enums with state** | `AccountType` holds the label, minimum balance and extra-field caption for each type. `Transaction.Kind` holds the label and whether money comes in or goes out. `Role` holds a fixed set of `Permission`s. |
| **Encapsulation (users)** | `User` never stores the password, only a salted hash, and offers `checkPassword()` and `setPassword()` instead of getters. Linked accounts can only be changed through `Bank`, which validates them (the setters are package-private). |
| **Custom exceptions** | `AccNotFound`, `InvalidAmount`, `MaxBalance`, `MaxWithdraw` and `AccessDenied` are checked exceptions in `src/Exceptions/`. |
| **Serialization** | All model classes implement `Serializable` and set a `serialVersionUID`. `Bank.readObject()` lets older save files still load. |

### Class hierarchy

```
BankAccount (abstract)
├── CurrentAccount   implements FeeCharging
└── SavingsAccount   implements InterestBearing
    └── StudentAccount   (inherits InterestBearing, overrides the rate)
```

---

## 2. Banking features

### Account types

| Type | Minimum balance | Type-specific field | Rules | Month-end |
|---|---|---|---|---|
| Savings | ₹2000 | Maximum withdraw limit | Withdraw limit set per account | 5% a year interest, paid monthly |
| Current | ₹5000 | Trade license number | License number is required | ₹500 fee if the balance is below ₹25,000 |
| Student | ₹100 | Institution name | Withdraw limit fixed at ₹20000 | 4% a year interest, paid monthly |

### Operations (`Bank.java`)

- **Open account:** account numbers are generated automatically from 10000 and never reused.
- **Deposit**
- **Withdraw**
- **Transfer** between two accounts.
- **Close account:** the remaining balance is paid out.

### Month-end interest and fees (`Bank.runMonthEnd()`)

- Administrators run it from the dashboard (**Run month-end**). It needs the `RUN_MONTH_END` permission.
- It pays one month's interest (balance × yearly rate ÷ 12, rounded to the paisa) to every `InterestBearing` account.
- It takes the monthly fee from every `FeeCharging` account. The fee is capped so the balance never drops below the minimum.
- It can run only once per calendar month; the bank remembers the last month it was run for.
- Each payment is recorded as an `INTEREST` or `FEE` transaction. The account page shows next month's interest or fee.

### Validation

- The minimum balance is enforced on opening and on every withdrawal.
- Amounts must be positive and finite.
- The withdraw limit is checked for savings and student accounts.
- You can't transfer to the same account.
- Required fields can't be empty.

### Transaction history (`Transaction.java`)

Every action (open, deposit, withdraw, transfer in/out, close, interest, fee) is recorded with:
- a timestamp
- the amount and the balance afterwards
- the other account, for transfers
- the holder's name, so the history still reads correctly after an account is closed

### Demo data (`DemoData.java`)

If `DEMO_DATA=true` and the bank is empty, the app creates 10 sample accounts with two weeks of history.

---

## 3. Login, roles and security

### Roles (`src/Bank/Role.java`, `Permission.java`, `User.java`)

| Permission | Administrator | Bank employee | Customer |
|---|:-:|:-:|:-:|
| See every account | ✓ | ✓ | only their own |
| Open accounts | ✓ | ✓ | |
| Deposit | ✓ | ✓ | |
| Withdraw | ✓ | ✓ | |
| Transfer | ✓ | ✓ | from their own accounts only |
| Close accounts | ✓ | | |
| Run month-end (interest and fees) | ✓ | | |
| Manage logins (Users & roles page) | ✓ | | |

- **Users** are stored inside `Bank`, so they are saved with the rest of the data (in the file or PostgreSQL). Usernames are unique, ignoring case.
- **Passwords** are hashed with PBKDF2-HMAC-SHA256 (65,536 iterations, random 16-byte salt) and compared in constant time. The minimum length is 5 characters.
- **Customers** are linked to the accounts they own. They only see those accounts and their transactions. They can send money to any account, but the details of other people's accounts stay hidden.
- **The first administrator** is created on first start from `ADMIN_USER` and `ADMIN_PASSWORD` (default `admin` / `admin`). It is also created when an older bank without logins is loaded. After that, logins are managed on the website.
- **Safety rules:** the last administrator can't be deleted, and nobody can delete their own login. Deleting a login ends its sessions straight away.
- **Change password:** every user can change their own password.

### Sessions and API protection (`src/Web/WebServer.java`)

- **Sessions:** each login gets a random token from `SecureRandom`, mapped on the server to the user. The token is stored in a cookie marked `HttpOnly` and `SameSite=Strict`, and also `Secure` behind HTTPS.
- **Protected API:** every route except `POST /api/login` and `GET /api/types` returns `401` if you aren't logged in. A route the role isn't allowed to use returns `403`, and so does an account that isn't the customer's.
- **The UI follows the role:** menu items, buttons and pages a role can't use are hidden. The server still checks every request, so hiding a button is never the only protection.
- **Logout:** `POST /api/logout` ends the session.
- **Other safeguards:**
  - a 16 KB limit on request bodies
  - protection against path traversal when serving files
  - escaping of JSON output
  - the `X-Content-Type-Options: nosniff` header
  - the server listens only on localhost unless `HOST` is set

### API routes

| Method | Route | Permission needed | Purpose |
|---|---|---|---|
| POST | `/api/login` | none | Log in |
| GET | `/api/types` | none | List account types |
| POST | `/api/logout` | logged in | Log out |
| GET | `/api/bank` | logged in | The user, plus the accounts and transactions they may see |
| POST | `/api/password` | logged in | Change your own password |
| POST | `/api/accounts` | `OPEN_ACCOUNT` | Open an account, optionally for a customer login (`owner`) |
| POST | `/api/deposit` | `DEPOSIT` | Deposit |
| POST | `/api/withdraw` | `WITHDRAW` | Withdraw |
| POST | `/api/transfer` | `TRANSFER` | Transfer (customers: only from their own accounts) |
| POST | `/api/close` | `CLOSE_ACCOUNT` | Close an account |
| POST | `/api/month-end` | `RUN_MONTH_END` | Pay interest and take fees, once a month |
| GET | `/api/users` | `MANAGE_USERS` | List logins and roles |
| POST | `/api/users` | `MANAGE_USERS` | Add a login |
| POST | `/api/users/accounts` | `MANAGE_USERS` | Set a customer's linked accounts |
| POST | `/api/users/delete` | `MANAGE_USERS` | Delete a login |

---

## 4. Storage (`src/Data/`)

- **Local file (default):** the bank is saved to a file called `data`. It writes a temp file first and then swaps it in, so a failed save doesn't destroy the old data. If the file can't be read, it is kept as `data.old`.
- **PostgreSQL (Neon):** used when `DATABASE_URL` is set. The bank is saved in a single table (`bank_state`) with an all-or-nothing upsert. If the data can't be loaded, the app refuses to start, so it never overwrites saved data with an empty bank.

---

## 5. Web interface and deployment

- **Front end (`web/`):**
  - login page with a 3D scene
  - Users & roles page (administrators) and a Change password page (everyone)
  - dashboard with charts
  - account list and account detail pages
  - transfer and open-account forms
  - activity log
  - dark mode and a mobile layout
- **Deployment:** a `Dockerfile` and `render.yaml` for deploying to the cloud.

---

## 6. Planned next

1. **Loans:** a `Loan` hierarchy with EMI calculation and a repayment schedule.
