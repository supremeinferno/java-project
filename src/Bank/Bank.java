package Bank;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;

import Exceptions.AccNotFound;
import Exceptions.InvalidAmount;
import Exceptions.MaxBalance;
import Exceptions.MaxWithdraw;

public class Bank implements Serializable {

	private static final long serialVersionUID = 2L;
	private static final int FIRST_ACCOUNT_NUMBER = 10000;

	private final LinkedHashMap<String, BankAccount> accounts = new LinkedHashMap<String, BankAccount>();
	private int nextAccountNumber = FIRST_ACCOUNT_NUMBER;

	/**
	 * Opens an account of the given type. {@code extra} is the type-specific value:
	 * withdraw limit (savings), trade license (current) or institution (student).
	 *
	 * @throws IllegalArgumentException if any value is invalid
	 */
	public BankAccount openAccount(AccountType type, String name, double balance, String extra)
	{
		String accNum = newAccountNumber();
		BankAccount acc;
		switch (type)
		{
		case SAVINGS:
			acc = new SavingsAccount(accNum, name, balance, parseLimit(extra));
			break;
		case CURRENT:
			acc = new CurrentAccount(accNum, name, balance, extra);
			break;
		case STUDENT:
			acc = new StudentAccount(accNum, name, balance, extra);
			break;
		default:
			throw new IllegalArgumentException("Unknown account type: " + type);
		}
		accounts.put(accNum, acc);
		nextAccountNumber++;
		return acc;
	}

	private String newAccountNumber()
	{
		while (accounts.containsKey(String.valueOf(nextAccountNumber)))
		{
			nextAccountNumber++;
		}
		return String.valueOf(nextAccountNumber);
	}

	private static double parseLimit(String text)
	{
		try
		{
			return Double.parseDouble(text.trim());
		}
		catch (RuntimeException e)
		{
			throw new IllegalArgumentException("Maximum withdraw limit must be a number");
		}
	}

	public BankAccount findAccount(String accountNum)
	{
		return accountNum == null ? null : accounts.get(accountNum.trim());
	}

	private BankAccount getAccount(String accountNum) throws AccNotFound
	{
		BankAccount acc = findAccount(accountNum);
		if (acc == null)
		{
			throw new AccNotFound("Account Not Found");
		}
		return acc;
	}

	public void deposit(String accountNum, double amt) throws InvalidAmount, AccNotFound
	{
		getAccount(accountNum).deposit(amt);
	}

	public void withdraw(String accountNum, double amt) throws MaxBalance, AccNotFound, MaxWithdraw, InvalidAmount
	{
		getAccount(accountNum).withdraw(amt);
	}

	/** Read-only view of all accounts in the order they were opened. */
	public Collection<BankAccount> getAccounts() {
		return Collections.unmodifiableCollection(accounts.values());
	}

	public int size() {
		return accounts.size();
	}
}
