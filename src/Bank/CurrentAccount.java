package Bank;

public class CurrentAccount extends BankAccount {

	private static final long serialVersionUID = 2L;
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

	@Override
	public AccountType getType() {
		return AccountType.CURRENT;
	}

	@Override
	public String getDetails() {
		return "Trade license: " + tradeLicenseNumber;
	}
}
