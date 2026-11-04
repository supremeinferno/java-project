package Bank;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.YearMonth;
import java.util.List;

/**
 * Tests for {@link Bank#runMonthEnd()}. Needs no libraries; from the project root:
 * <pre>
 * javac -d bin $(find src test -name '*.java')
 * java -ea -cp bin Bank.MonthEndTest
 * </pre>
 */
public class MonthEndTest {

	private static int passed;
	private static int failed;

	public static void main(String[] args) throws Exception
	{
		run("pays interest and takes fees", MonthEndTest::paysInterestAndTakesFees);
		run("rounds interest to the paisa", MonthEndTest::roundsInterestToThePaisa);
		run("caps the fee at the minimum balance", MonthEndTest::capsFeeAtMinimumBalance);
		run("records INTEREST and FEE transactions", MonthEndTest::recordsTransactions);
		run("runs only once a month", MonthEndTest::runsOnlyOnceAMonth);
		run("remembers the month after a save and load", MonthEndTest::remembersMonthAfterReload);
		System.out.println(passed + " passed, " + failed + " failed");
		if (failed > 0)
		{
			System.exit(1);
		}
	}

	static void paysInterestAndTakesFees() throws Exception
	{
		Bank bank = new Bank();
		BankAccount savings = bank.openAccount(AccountType.SAVINGS, "Asha", 12000, "5000");
		BankAccount student = bank.openAccount(AccountType.STUDENT, "Ravi", 1200, "IIT Delhi");
		BankAccount current = bank.openAccount(AccountType.CURRENT, "Shop", 10000, "TL-1");
		BankAccount richCurrent = bank.openAccount(AccountType.CURRENT, "Big Shop", 30000, "TL-2");

		MonthEndResult result = bank.runMonthEnd();

		// 12000 * 5% / 12 and 1200 * 4% / 12
		check(savings.getBalance(), 12050);
		check(student.getBalance(), 1204);
		check(current.getBalance(), 9500);
		// at least FEE_FREE_BALANCE: no fee
		check(richCurrent.getBalance(), 30000);

		check(result.getMonth(), YearMonth.now());
		check(result.getInterestCount(), 2);
		check(result.getInterestTotal(), 54);
		check(result.getFeeCount(), 1);
		check(result.getFeeTotal(), 500);
		check(bank.getLastMonthEnd(), YearMonth.now());
	}

	static void roundsInterestToThePaisa() throws Exception
	{
		Bank bank = new Bank();
		// 2345.67 * 5% / 12 = 9.77362...
		BankAccount savings = bank.openAccount(AccountType.SAVINGS, "Asha", 2345.67, "5000");

		bank.runMonthEnd();

		check(savings.getBalance(), 2345.67 + 9.77);
	}

	static void capsFeeAtMinimumBalance() throws Exception
	{
		Bank bank = new Bank();
		BankAccount nearMin = bank.openAccount(AccountType.CURRENT, "Shop", 5200, "TL-1");
		BankAccount atMin = bank.openAccount(AccountType.CURRENT, "Other Shop", 5000, "TL-2");

		MonthEndResult result = bank.runMonthEnd();

		check(nearMin.getBalance(), AccountType.CURRENT.getMinBalance());
		check(atMin.getBalance(), AccountType.CURRENT.getMinBalance());
		// an account at the minimum owes nothing, so it is not counted
		check(result.getFeeCount(), 1);
		check(result.getFeeTotal(), 200);
	}

	static void recordsTransactions() throws Exception
	{
		Bank bank = new Bank();
		BankAccount savings = bank.openAccount(AccountType.SAVINGS, "Asha", 12000, "5000");
		BankAccount current = bank.openAccount(AccountType.CURRENT, "Shop", 10000, "TL-1");
		int before = bank.getTransactions().size();

		bank.runMonthEnd();

		List<Transaction> added = bank.getTransactions().subList(before, bank.getTransactions().size());
		check(added.size(), 2);

		Transaction interest = added.get(0);
		check(interest.getKind(), Transaction.Kind.INTEREST);
		check(interest.getAccNum(), savings.getAccNum());
		check(interest.getAmount(), 50);
		check(interest.getBalanceAfter(), 12050);
		check(interest.getKind().isCredit(), true);

		Transaction fee = added.get(1);
		check(fee.getKind(), Transaction.Kind.FEE);
		check(fee.getAccNum(), current.getAccNum());
		// amounts are positive; the kind gives the direction
		check(fee.getAmount(), 500);
		check(fee.getBalanceAfter(), 9500);
		check(fee.getKind().isCredit(), false);
	}

	static void runsOnlyOnceAMonth() throws Exception
	{
		Bank bank = new Bank();
		BankAccount savings = bank.openAccount(AccountType.SAVINGS, "Asha", 12000, "5000");
		check(bank.getLastMonthEnd(), null);
		bank.runMonthEnd();
		int transactions = bank.getTransactions().size();

		try
		{
			bank.runMonthEnd();
			throw new AssertionError("second run in the same month should fail");
		}
		catch (IllegalArgumentException expected)
		{
		}

		check(savings.getBalance(), 12050);
		check(bank.getTransactions().size(), transactions);
	}

	static void remembersMonthAfterReload() throws Exception
	{
		Bank bank = new Bank();
		bank.openAccount(AccountType.SAVINGS, "Asha", 12000, "5000");
		bank.runMonthEnd();

		Bank loaded = copy(bank);

		check(loaded.getLastMonthEnd(), YearMonth.now());
		try
		{
			loaded.runMonthEnd();
			throw new AssertionError("a reloaded bank should still refuse a second run this month");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}

	// ---- helpers

	private interface Test {
		void run() throws Exception;
	}

	private static void run(String name, Test test)
	{
		try
		{
			test.run();
			passed++;
			System.out.println("PASS " + name);
		}
		catch (Throwable e)
		{
			failed++;
			System.out.println("FAIL " + name + ": " + e);
		}
	}

	private static void check(double actual, double expected)
	{
		if (Math.abs(actual - expected) > 0.001)
		{
			throw new AssertionError("expected " + expected + " but was " + actual);
		}
	}

	private static void check(Object actual, Object expected)
	{
		if (expected == null ? actual != null : !expected.equals(actual))
		{
			throw new AssertionError("expected " + expected + " but was " + actual);
		}
	}

	/** Saves and loads the bank the way the file storage does. */
	private static Bank copy(Bank bank) throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ObjectOutputStream out = new ObjectOutputStream(bytes);
		out.writeObject(bank);
		out.close();
		ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
		return (Bank) in.readObject();
	}
}
