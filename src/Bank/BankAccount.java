package Bank;
import java.io.Serializable;

import Exceptions.InvalidAmount;
import Exceptions.MaxBalance;
import Exceptions.MaxWithdraw;

public abstract class BankAccount implements Serializable {

	private static final long serialVersionUID = 2L;
	private final String accNum;
	private final String name;
	private final double minBalance;
	private double balance;

	protected BankAccount(String accNum, String name, double balance, double minBalance) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Name is required");
		}
		if (Double.isNaN(balance) || balance < minBalance) {
			throw new IllegalArgumentException("Initial balance cannot be less than the minimum required balance: " + minBalance);
		}
		this.accNum = accNum;
		this.name = name.trim();
		this.balance = balance;
		this.minBalance = minBalance;
	}

	public abstract AccountType getType();

	/** Short description of the type-specific data, for display. */
	public abstract String getDetails();

	public void deposit(double amount) throws InvalidAmount
	{
		checkAmount(amount);
		balance += amount;
	}

	public void withdraw(double amount) throws InvalidAmount, MaxWithdraw, MaxBalance
	{
		checkAmount(amount);
		if (balance - amount < minBalance)
		{
			throw new MaxBalance("Insufficient Balance");
		}
		balance -= amount;
	}

	private static void checkAmount(double amount) throws InvalidAmount
	{
		if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0)
		{
			throw new InvalidAmount("Amount must be greater than zero");
		}
	}

	public String getAccNum() {
		return accNum;
	}

	public String getName() {
		return name;
	}

	public double getBalance() {
		return balance;
	}

	public double getMinBalance() {
		return minBalance;
	}

	@Override
	public String toString() {
		return "Name: " + name + ", Id: " + accNum + ", Balance: " + balance + ", Type: " + getType().getLabel();
	}
}
