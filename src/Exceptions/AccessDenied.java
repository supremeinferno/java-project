package Exceptions;

/** Thrown when a logged-in user's role does not allow an action, or the account is not theirs. */
public class AccessDenied extends Exception {

	private static final long serialVersionUID = 1L;

	public AccessDenied(String s)
	{
		super(s);
	}

}
