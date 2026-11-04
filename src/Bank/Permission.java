package Bank;

/** Something a user may be allowed to do. Each {@link Role} grants a fixed set of these. */
public enum Permission {
	/** See every account and transaction; without it a user sees only the accounts linked to them. */
	VIEW_ALL_ACCOUNTS,
	OPEN_ACCOUNT,
	DEPOSIT,
	WITHDRAW,
	TRANSFER,
	CLOSE_ACCOUNT,
	/** Credit interest and take fees for the month, see {@link Bank#runMonthEnd()}. */
	RUN_MONTH_END,
	/** Create and delete logins and link customers to their accounts. */
	MANAGE_USERS
}
