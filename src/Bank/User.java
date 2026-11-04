package Bank;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import Exceptions.AccessDenied;

/**
 * A login. The password is never stored, only a salted PBKDF2 hash of it.
 * A customer is linked to the accounts they own; staff roles see every account.
 */
public class User implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,32}");
	private static final int MIN_PASSWORD = 5;
	private static final int ITERATIONS = 65536;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final String username;
	private final Role role;
	private final LinkedHashSet<String> accounts = new LinkedHashSet<String>();
	private byte[] salt;
	private byte[] hash;

	User(String username, String password, Role role) {
		if (username == null || !USERNAME.matcher(username.trim()).matches()) {
			throw new IllegalArgumentException("Username must be 3 to 32 letters, digits, dots, dashes or underscores");
		}
		if (role == null) {
			throw new IllegalArgumentException("Choose a role");
		}
		this.username = username.trim();
		this.role = role;
		setPassword(password);
	}

	public void setPassword(String password)
	{
		if (password == null || password.length() < MIN_PASSWORD)
		{
			throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD + " characters");
		}
		byte[] newSalt = new byte[16];
		RANDOM.nextBytes(newSalt);
		hash = hash(password, newSalt);
		salt = newSalt;
	}

	public boolean checkPassword(String password)
	{
		// isEqual takes the same time however many bytes match
		return password != null && MessageDigest.isEqual(hash, hash(password, salt));
	}

	private static byte[] hash(String password, byte[] salt)
	{
		try
		{
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			return factory.generateSecret(new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)).getEncoded();
		}
		catch (GeneralSecurityException e)
		{
			throw new IllegalStateException("PBKDF2 is not available", e);
		}
	}

	public boolean can(Permission permission) {
		return role.can(permission);
	}

	public void require(Permission permission) throws AccessDenied
	{
		if (!can(permission))
		{
			throw new AccessDenied("A " + role.getLabel().toLowerCase() + " is not allowed to do this");
		}
	}

	/** True if this user may see and use the account: staff see every account, a customer only their own. */
	public boolean canAccess(String accNum)
	{
		return can(Permission.VIEW_ALL_ACCOUNTS) || (accNum != null && accounts.contains(accNum.trim()));
	}

	public void requireAccess(String accNum) throws AccessDenied
	{
		if (!canAccess(accNum))
		{
			throw new AccessDenied("Account " + (accNum == null ? "" : accNum.trim()) + " is not one of your accounts");
		}
	}

	/** Replaces the linked accounts; {@link Bank} checks the numbers first. */
	void setAccounts(Collection<String> accNums)
	{
		accounts.clear();
		accounts.addAll(accNums);
	}

	void linkAccount(String accNum)
	{
		accounts.add(accNum);
	}

	public boolean owns(String accNum) {
		return accounts.contains(accNum);
	}

	public String getUsername() {
		return username;
	}

	public Role getRole() {
		return role;
	}

	/** Account numbers linked to this customer, closed ones included so their history stays visible. */
	public Set<String> getAccounts() {
		return Collections.unmodifiableSet(accounts);
	}
}
