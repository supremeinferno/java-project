package Bank;

/** An account that pays a monthly fee, taken at month-end by {@link Bank#runMonthEnd()}. */
public interface FeeCharging {

	/** The fee due this month on the current balance; 0 if none. */
	double getMonthlyFee();
}
