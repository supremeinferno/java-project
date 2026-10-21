package Bank;

public enum AccountType {
	SAVINGS("Savings Account", 2000, "Maximum Withdraw Limit"),
	CURRENT("Current Account", 5000, "Trade License Number"),
	STUDENT("Student Account", 100, "Institution Name");

	private final String label;
	private final double minBalance;
	private final String extraLabel;

	AccountType(String label, double minBalance, String extraLabel) {
		this.label = label;
		this.minBalance = minBalance;
		this.extraLabel = extraLabel;
	}

	public String getLabel() {
		return label;
	}

	public double getMinBalance() {
		return minBalance;
	}

	/** Caption of the one type-specific field of this account type. */
	public String getExtraLabel() {
		return extraLabel;
	}
}
