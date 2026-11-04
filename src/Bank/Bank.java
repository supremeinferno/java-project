package Bank;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

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
	// keyed by lower-case username, so names are unique ignoring case; null in a bank saved before logins existed
	private LinkedHashMap<String, User> users = new LinkedHashMap<String, User>();
	// the last month runMonthEnd() was run for; null until the first run
	private YearMonth lastMonthEnd;

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

	/**
	 * Credits a month's interest to every {@link InterestBearing} account and takes the fee from every
	 * {@link FeeCharging} one. Each account type decides its own rate or fee; this method only asks.
	 *
	 * @throws IllegalArgumentException if it has already been run this month
	 */
	public MonthEndResult runMonthEnd()
	{
		YearMonth month = YearMonth.now();
		if (month.equals(lastMonthEnd))
		{
			throw new IllegalArgumentException("Month-end has already been run for " + month);
		}
		MonthEndResult result = new MonthEndResult(month);
		for (BankAccount acc : accounts.values())
		{
			if (acc instanceof InterestBearing)
			{
				double interest = ((InterestBearing) acc).getMonthlyInterest();
				if (interest > 0)
				{
					acc.adjust(interest);
					record(Transaction.Kind.INTEREST, acc, interest, null);
					result.addInterest(interest);
				}
			}
			if (acc instanceof FeeCharging)
			{
				double fee = ((FeeCharging) acc).getMonthlyFee();
				if (fee > 0)
				{
					acc.adjust(-fee);
					record(Transaction.Kind.FEE, acc, fee, null);
					result.addFee(fee);
				}
			}
		}
		lastMonthEnd = month;
		return result;
	}

	/** The last month that month-end was run for, or null if never. */
	public YearMonth getLastMonthEnd() {
		return lastMonthEnd;
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
		if (users == null)
		{
			users = new LinkedHashMap<String, User>();
		}
	}

	// ---- logins

	/**
	 * Adds a login. {@code accNums} links a customer to their accounts and must be empty for staff.
	 * Nothing changes if any value is invalid.
	 *
	 * @throws IllegalArgumentException if a value is invalid or the username is taken
	 */
	public User addUser(String username, String password, Role role, Collection<String> accNums) throws AccNotFound
	{
		User user = new User(username, password, role);
		if (findUser(user.getUsername()) != null)
		{
			throw new IllegalArgumentException("The username " + user.getUsername() + " is taken");
		}
		user.setAccounts(checkLinks(user, accNums));
		users.put(key(user.getUsername()), user);
		return user;
	}

	public User findUser(String username)
	{
		return username == null ? null : users.get(key(username));
	}

	private User getUser(String username)
	{
		User user = findUser(username);
		if (user == null)
		{
			throw new IllegalArgumentException("There is no login " + username);
		}
		return user;
	}

	/** Removes a login; the last administrator cannot be removed, or nobody could manage the bank. */
	public User removeUser(String username)
	{
		User user = getUser(username);
		if (user.getRole() == Role.ADMIN && countUsers(Role.ADMIN) == 1)
		{
			throw new IllegalArgumentException("Keep at least one administrator");
		}
		users.remove(key(user.getUsername()));
		return user;
	}

	/** Replaces the accounts linked to a customer. */
	public void setUserAccounts(String username, Collection<String> accNums) throws AccNotFound
	{
		User user = getUser(username);
		user.setAccounts(checkLinks(user, accNums));
	}

	/** Links one more account to a customer, e.g. one just opened for them. */
	public void linkAccount(String username, String accNum) throws AccNotFound
	{
		User user = getUser(username);
		user.linkAccount(checkLinks(user, Collections.singleton(accNum)).iterator().next());
	}

	/** Trims the numbers and checks that they may be linked; an existing link to a closed account may stay. */
	private LinkedHashSet<String> checkLinks(User user, Collection<String> accNums) throws AccNotFound
	{
		LinkedHashSet<String> result = new LinkedHashSet<String>();
		for (String accNum : accNums)
		{
			String n = accNum.trim();
			if (!user.owns(n) && findAccount(n) == null)
			{
				throw new AccNotFound("Account " + n + " not found");
			}
			result.add(n);
		}
		if (!result.isEmpty() && user.getRole() != Role.CUSTOMER)
		{
			throw new IllegalArgumentException("Only customers are linked to accounts; staff see every account");
		}
		return result;
	}

	private int countUsers(Role role)
	{
		int n = 0;
		for (User user : users.values())
		{
			if (user.getRole() == role)
			{
				n++;
			}
		}
		return n;
	}

	private static String key(String username)
	{
		return username.trim().toLowerCase(Locale.ROOT);
	}

	/** Read-only view of all logins in the order they were added. */
	public Collection<User> getUsers() {
		return Collections.unmodifiableCollection(users.values());
	}

	/** Read-only view of all accounts in the order they were opened. */
	public Collection<BankAccount> getAccounts() {
		return Collections.unmodifiableCollection(accounts.values());
	}

	public int size() {
		return accounts.size();
	}
}
