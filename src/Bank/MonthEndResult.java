package Bank;
import java.time.YearMonth;

/** What one run of {@link Bank#runMonthEnd()} did. */
public class MonthEndResult {

	private final YearMonth month;
	private int interestCount;
	private double interestTotal;
	private int feeCount;
	private double feeTotal;

	MonthEndResult(YearMonth month) {
		this.month = month;
	}

	void addInterest(double amount)
	{
		interestCount++;
		interestTotal += amount;
	}

	void addFee(double amount)
	{
		feeCount++;
		feeTotal += amount;
	}

	public YearMonth getMonth() {
		return month;
	}

	/** Number of accounts that were paid interest. */
	public int getInterestCount() {
		return interestCount;
	}

	public double getInterestTotal() {
		return interestTotal;
	}

	/** Number of accounts that were charged a fee. */
	public int getFeeCount() {
		return feeCount;
	}

	public double getFeeTotal() {
		return feeTotal;
	}
}
