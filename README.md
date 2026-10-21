# Banking System - Java Swing Project

## Description
The Banking System is a Java Swing application that simulates basic banking operations. It provides a user-friendly interface for managing accounts, transactions, and other essential banking functions.

## Features
- Account creation and management
- Deposit and withdrawal transactions
- Balance inquiry
- Savings, Current and Student account types
- Searchable, sortable account list
- Data saved between runs (`data` file)
- User-friendly GUI with Java Swing

## Screenshots
![Screenshot 1](screenshot/1.png)
![Screenshot 2](screenshot/2.png)

## Technologies Used
- Java
- Java Swing for GUI

## Installation
1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd "java project"
   ```
2. Compile and run (Java 8+):
   ```bash
   javac -d bin $(find src -name '*.java')
   java -cp bin Application
   ```
   Run it from the project folder so the `data` file is found.

## Project Structure
- `src/Bank` - domain model: `Bank`, `BankAccount` and the Savings / Current / Student accounts, plus the `AccountType` enum. No GUI code.
- `src/Exceptions` - `AccNotFound`, `InvalidAmount`, `MaxBalance`, `MaxWithdraw`.
- `src/Data` - `FileIO`, saves and loads the bank (file `data`, created on first run).
- `src/GUI` - Swing windows. One `AccountForm` serves all account types and one `TransactionForm` serves deposit and withdraw, so a new account type only needs an `AccountType` entry and a `BankAccount` subclass.

Accounts are kept in a map, so there is no limit on the number of accounts. Data is saved after every change.

## Usage
1. Launch the application.
2. Log in with username `admin` and password `admin`.
3. Use the menu to add accounts, deposit, withdraw and view the account list.

## Contribution
Contributions are welcome! If you'd like to contribute to the project, please follow these steps:

1. Fork the repository
2. Create a new branch (git checkout -b feature/new-feature)
3. Commit your changes (git commit -m 'Add new feature')
4. Push to the branch (git push origin feature/new-feature)
5. Create a pull request

## License
This project is for educational purposes.

## Acknowledgments
- Thanks to Java for the programming language.
- Special thanks to Java Swing for the GUI components.
