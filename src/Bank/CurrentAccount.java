package Bank;

public class CurrentAccount extends BankAccount implements FeeCharging {

	private static final long serialVersionUID = 2L;
	public static final double MONTHLY_FEE = 500;
	/** No fee is charged when the balance is at least this much. */
	public static final double FEE_FREE_BALANCE = 25000;
	private final String tradeLicenseNumber;

	public CurrentAccount(String accNum, String name, double balance, String tradeLicenseNumber) {
		super(accNum, name, balance, AccountType.CURRENT.getMinBalance());
		if (tradeLicenseNumber == null || tradeLicenseNumber.trim().isEmpty()) {
			throw new IllegalArgumentException("Trade license number is required");
		}
		this.tradeLicenseNumber = tradeLicenseNumber.trim();
	}

	public String getTradeLicenseNumber() {
		return tradeLicenseNumber;
	}

	/** The fee never takes the balance below the minimum, so it may be less than {@link #MONTHLY_FEE}. */
	@Override
	public double getMonthlyFee()
	{
		if (getBalance() >= FEE_FREE_BALANCE)
		{
			return 0;
		}
		return Math.min(MONTHLY_FEE, getBalance() - getMinBalance());
	}

	@Override
	public AccountType getType() {
		return AccountType.CURRENT;
	}

	@Override
	public String getDetails() {
		return "Trade license: " + tradeLicenseNumber;
	}
}
