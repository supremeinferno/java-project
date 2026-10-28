package Bank;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import Exceptions.AccNotFound;
import Exceptions.InvalidAmount;
import Exceptions.MaxBalance;
import Exceptions.MaxWithdraw;

public class Bank implements Serializable {

	private static final long serialVersionUID = 2L;
	private static final int FIRST_ACCOUNT_NUMBER = 10000;

	private final LinkedHashMap<String, BankAccount> accounts = new LinkedHashMap<String, BankAccount>();
	private int nextAccountNumber = FIRST_ACCOUNT_NUMBER;
	// not final: a bank saved before history existed loads with null here, see readObject
	private ArrayList<Transaction> transactions = new ArrayList<Transaction>();

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
		record(Transaction.Kind.OPEN, acc, balance, null);
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
		BankAccount acc = getAccount(accountNum);
		acc.deposit(amt);
		record(Transaction.Kind.DEPOSIT, acc, amt, null);
	}

	public void withdraw(String accountNum, double amt) throws MaxBalance, AccNotFound, MaxWithdraw, InvalidAmount
	{
		BankAccount acc = getAccount(accountNum);
		acc.withdraw(amt);
		record(Transaction.Kind.WITHDRAW, acc, amt, null);
	}

	/** Moves money between two accounts; the sender's minimum balance and withdraw limit apply. */
	public void transfer(String fromNum, String toNum, double amt) throws MaxBalance, AccNotFound, MaxWithdraw, InvalidAmount
	{
		BankAccount from = getAccount(fromNum);
		BankAccount to = getAccount(toNum);
		if (from == to)
		{
			throw new IllegalArgumentException("Choose two different accounts");
		}
		from.withdraw(amt);
		// cannot fail: withdraw has already checked the amount
		to.deposit(amt);
		record(Transaction.Kind.TRANSFER_OUT, from, amt, to.getAccNum());
		record(Transaction.Kind.TRANSFER_IN, to, amt, from.getAccNum());
	}

	/** Removes the account; its remaining balance is paid out. Its number is never given out again. */
	public BankAccount closeAccount(String accountNum) throws AccNotFound
	{
		BankAccount acc = getAccount(accountNum);
		accounts.remove(acc.getAccNum());
		record(Transaction.Kind.CLOSE, acc, acc.getBalance(), null);
		return acc;
	}

	private void record(Transaction.Kind kind, BankAccount acc, double amt, String other)
	{
		transactions.add(new Transaction(transactions.size() + 1, kind, acc, amt, other));
	}

	/** Re-dates the transactions from index {@code from} on; only DemoData uses this. */
	void backdate(int from, long time)
	{
		for (int i = from; i < transactions.size(); i++)
		{
			transactions.set(i, new Transaction(transactions.get(i), time));
		}
	}

	/** Read-only view of every transaction, oldest first. */
	public List<Transaction> getTransactions() {
		return Collections.unmodifiableList(transactions);
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
	{
		in.defaultReadObject();
		if (transactions == null)
		{
			transactions = new ArrayList<Transaction>();
		}
	}

	/** Read-only view of all accounts in the order they were opened. */
	public Collection<BankAccount> getAccounts() {
		return Collections.unmodifiableCollection(accounts.values());
	}

	public int size() {
		return accounts.size();
	}
}
