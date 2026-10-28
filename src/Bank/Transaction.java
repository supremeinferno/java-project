package Bank;
import java.io.Serializable;

/** One entry in the bank's history. Amounts are always positive; {@link Kind#isCredit()} gives the direction. */
public class Transaction implements Serializable {

	public enum Kind {
		OPEN("Account opened", true),
		DEPOSIT("Deposit", true),
		WITHDRAW("Withdrawal", false),
		TRANSFER_IN("Transfer in", true),
		TRANSFER_OUT("Transfer out", false),
		CLOSE("Account closed", false);

		private final String label;
		private final boolean credit;

		Kind(String label, boolean credit) {
			this.label = label;
			this.credit = credit;
		}

		public String getLabel() {
			return label;
		}

		/** True if money came into the account. */
		public boolean isCredit() {
			return credit;
		}
	}

	private static final long serialVersionUID = 1L;
	private final int id;
	private final long time;
	private final Kind kind;
	private final String accNum;
	private final String name;
	private final double amount;
	private final double balanceAfter;
	private final String otherAccNum;

	Transaction(int id, Kind kind, BankAccount acc, double amount, String otherAccNum) {
		this.id = id;
		this.time = System.currentTimeMillis();
		this.kind = kind;
		this.accNum = acc.getAccNum();
		this.name = acc.getName();
		this.amount = amount;
		this.balanceAfter = kind == Kind.CLOSE ? 0 : acc.getBalance();
		this.otherAccNum = otherAccNum;
	}

	public int getId() {
		return id;
	}

	public long getTime() {
		return time;
	}

	public Kind getKind() {
		return kind;
	}

	public String getAccNum() {
		return accNum;
	}

	/** Holder name at the time, so history still reads well after the account is closed. */
	public String getName() {
		return name;
	}

	public double getAmount() {
		return amount;
	}

	public double getBalanceAfter() {
		return balanceAfter;
	}

	/** The other account of a transfer, otherwise null. */
	public String getOtherAccNum() {
		return otherAccNum;
	}
}
