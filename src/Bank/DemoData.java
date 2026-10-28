package Bank;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A bank filled with demo accounts and two weeks of history, for a fresh public deployment. */
public final class DemoData {

	private static final long DAY = 24 * 60 * 60 * 1000L;
	private static final long HOUR = 60 * 60 * 1000L;

	private DemoData() {
	}

	public static Bank create()
	{
		Object[][] people = {
			{ AccountType.SAVINGS, "Priya Sharma", 84500, "25000" },
			{ AccountType.CURRENT, "Rohan Mehta Traders", 215000, "TL-2024-0193" },
			{ AccountType.STUDENT, "Kabir Singh", 6200, "Delhi University" },
			{ AccountType.SAVINGS, "Ananya Iyer", 52300, "15000" },
			{ AccountType.CURRENT, "Fatima Khan Studio", 128000, "TL-2023-4471" },
			{ AccountType.STUDENT, "Sneha Patel", 3800, "IIT Bombay" },
			{ AccountType.SAVINGS, "Arjun Nair", 39900, "10000" },
			{ AccountType.SAVINGS, "Meera Joshi", 147200, "30000" },
			{ AccountType.STUDENT, "Aditya Verma", 2100, "Christ University" },
			{ AccountType.CURRENT, "Vikram Rao & Sons", 96000, "TL-2025-0820" },
		};
		Bank bank = new Bank();
		Random random = new Random(7); // same demo every time
		long now = System.currentTimeMillis();
		List<String> numbers = new ArrayList<String>();
		for (int i = 0; i < people.length; i++)
		{
			Object[] p = people[i];
			int before = bank.getTransactions().size();
			numbers.add(bank.openAccount((AccountType) p[0], (String) p[1], ((Number) p[2]).doubleValue(), (String) p[3]).getAccNum());
			bank.backdate(before, now - (40 - i * 2) * DAY - random.nextInt(8) * HOUR);
		}
		for (int day = 13; day >= 0; day--)
		{
			int count = 2 + random.nextInt(4);
			for (int k = 0; k < count; k++)
			{
				String a = numbers.get(random.nextInt(numbers.size()));
				String b = numbers.get(random.nextInt(numbers.size()));
				double amount = Math.round((500 + random.nextInt(18000)) / 100.0) * 100;
				int kind = random.nextInt(10);
				int before = bank.getTransactions().size();
				try
				{
					if (kind < 5)
					{
						bank.deposit(a, amount);
					}
					else if (kind < 8)
					{
						bank.withdraw(a, amount);
					}
					else if (!a.equals(b))
					{
						bank.transfer(a, b, Math.min(amount, 8000));
					}
				}
				catch (Exception e)
				{
					// e.g. over a withdraw limit: that one is simply skipped
				}
				// today's entries stay in the past hours, never in the future
				long time = day == 0 ? now - (count - k) * HOUR : now - day * DAY - (9 - k) * HOUR - random.nextInt(50) * 60000L;
				bank.backdate(before, time);
			}
		}
		return bank;
	}
}
