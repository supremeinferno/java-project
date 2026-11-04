package Bank;

public class StudentAccount extends SavingsAccount {

	private static final long serialVersionUID = 2L;
	private static final double MAX_WITHDRAW = 20000;
	private static final double INTEREST_RATE = 0.04;
	private final String institutionName;

	public StudentAccount(String accNum, String name, double balance, String institutionName) {
		super(accNum, name, balance, MAX_WITHDRAW, AccountType.STUDENT.getMinBalance());
		if (institutionName == null || institutionName.trim().isEmpty()) {
			throw new IllegalArgumentException("Institution name is required");
		}
		this.institutionName = institutionName.trim();
	}

	public String getInstitutionName() {
		return institutionName;
	}

	@Override
	public double getInterestRate() {
		return INTEREST_RATE;
	}

	@Override
	public AccountType getType() {
		return AccountType.STUDENT;
	}

	@Override
	public String getDetails() {
		return "Institution: " + institutionName + ", withdraw limit: " + getMaxWithLimit();
	}
}
