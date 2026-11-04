package Bank;
import Exceptions.InvalidAmount;
import Exceptions.MaxBalance;
import Exceptions.MaxWithdraw;

public class SavingsAccount extends BankAccount implements InterestBearing {

	private static final long serialVersionUID = 2L;
	private static final double INTEREST_RATE = 0.05;
	private final double maxWithLimit;

	public SavingsAccount(String accNum, String name, double balance, double maxWithLimit) {
		this(accNum, name, balance, maxWithLimit, AccountType.SAVINGS.getMinBalance());
	}

	protected SavingsAccount(String accNum, String name, double balance, double maxWithLimit, double minBalance) {
		super(accNum, name, balance, minBalance);
		if (Double.isNaN(maxWithLimit) || maxWithLimit <= 0) {
			throw new IllegalArgumentException("Maximum withdraw limit must be greater than zero");
		}
		this.maxWithLimit = maxWithLimit;
	}

	@Override
	public double getInterestRate() {
		return INTEREST_RATE;
	}

	/** The balance after one year of interest, for display. */
	public double getNetBalance()
	{
		return getBalance() * (1 + getInterestRate());
	}

	public double getMaxWithLimit() {
		return maxWithLimit;
	}

	@Override
	public void withdraw(double amount) throws InvalidAmount, MaxWithdraw, MaxBalance
	{
		if (amount > maxWithLimit)
		{
			throw new MaxWithdraw("Maximum Withdraw Limit Exceeded (" + maxWithLimit + ")");
		}
		super.withdraw(amount);
	}

	@Override
	public AccountType getType() {
		return AccountType.SAVINGS;
	}

	@Override
	public String getDetails() {
		return "Withdraw limit: " + maxWithLimit;
	}
}
