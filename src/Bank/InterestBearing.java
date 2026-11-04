package Bank;

/** An account that earns interest, credited at month-end by {@link Bank#runMonthEnd()}. */
public interface InterestBearing {

	/** Yearly interest rate, e.g. 0.05 for 5%. */
	double getInterestRate();

	double getBalance();

	/** Interest for one month on the current balance, rounded to the paisa. */
	default double getMonthlyInterest()
	{
		return Math.round(getBalance() * getInterestRate() / 12 * 100) / 100.0;
	}
}
