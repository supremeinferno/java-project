package Bank;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum Role {
	ADMIN("Administrator", EnumSet.allOf(Permission.class)),
	EMPLOYEE("Bank employee", EnumSet.of(Permission.VIEW_ALL_ACCOUNTS, Permission.OPEN_ACCOUNT,
			Permission.DEPOSIT, Permission.WITHDRAW, Permission.TRANSFER)),
	// a customer sees only their own accounts and can only send money from them
	CUSTOMER("Customer", EnumSet.of(Permission.TRANSFER));

	private final String label;
	private final Set<Permission> permissions;

	Role(String label, Set<Permission> permissions) {
		this.label = label;
		this.permissions = Collections.unmodifiableSet(permissions);
	}

	public String getLabel() {
		return label;
	}

	public Set<Permission> getPermissions() {
		return permissions;
	}

	public boolean can(Permission permission) {
		return permissions.contains(permission);
	}
}
