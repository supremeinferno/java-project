package Web;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import Bank.AccountType;
import Bank.BankAccount;
import Bank.CurrentAccount;
import Bank.FeeCharging;
import Bank.InterestBearing;
import Bank.MonthEndResult;
import Bank.Permission;
import Bank.Role;
import Bank.SavingsAccount;
import Bank.StudentAccount;
import Bank.Transaction;
import Bank.User;
import Data.FileIO;
import Exceptions.AccNotFound;
import Exceptions.AccessDenied;
import Exceptions.InvalidAmount;
import Exceptions.MaxBalance;
import Exceptions.MaxWithdraw;

/**
 * Serves the website in the {@code web} folder and a small JSON API over {@link FileIO#bank}.
 * Requests are handled one at a time on the server thread, so the bank needs no locking.
 */
public class WebServer {

	private static final String COOKIE = "session";
	private static final int MAX_BODY = 16 * 1024;

	public static final Path ROOT = Paths.get("web").toAbsolutePath().normalize();
	private static final Map<String, String> CONTENT_TYPES = new HashMap<String, String>();
	static
	{
		CONTENT_TYPES.put("html", "text/html; charset=utf-8");
		CONTENT_TYPES.put("css", "text/css; charset=utf-8");
		CONTENT_TYPES.put("js", "text/javascript; charset=utf-8");
		CONTENT_TYPES.put("png", "image/png");
		CONTENT_TYPES.put("svg", "image/svg+xml");
		CONTENT_TYPES.put("ico", "image/x-icon");
	}

	// session token -> the logged-in user
	private final Map<String, User> sessions = new HashMap<String, User>();
	private final SecureRandom random = new SecureRandom();
	private final HttpServer server;

	/**
	 * Listens on localhost only, so the website is not reachable from other computers,
	 * unless HOST is set (a cloud host sets HOST=0.0.0.0).
	 */
	public WebServer(int port) throws IOException {
		String host = System.getenv("HOST");
		InetSocketAddress address = host == null ? new InetSocketAddress(InetAddress.getLoopbackAddress(), port) : new InetSocketAddress(host, port);
		server = HttpServer.create(address, 0);
		server.createContext("/api/", this::handleApi);
		server.createContext("/", this::handleFile);
	}

	public void start()
	{
		server.start();
	}

	private void handleApi(HttpExchange ex) throws IOException
	{
		String route = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
		try
		{
			Map<String, String> form = readForm(ex);
			if (route.equals("POST /api/login"))
			{
				login(ex, form);
				return;
			}
			if (route.equals("GET /api/types"))
			{
				send(ex, 200, typesJson());
				return;
			}
			String session = sessionOf(ex);
			if (session == null)
			{
				send(ex, 401, error("Please log in"));
				return;
			}
			User user = sessions.get(session);
			// every route below checks the user's role first, and for a customer that the account is theirs
			switch (route)
			{
			case "POST /api/logout":
				sessions.remove(session);
				ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict");
				send(ex, 200, "{}");
				break;
			case "GET /api/bank":
				YearMonth last = FileIO.bank.getLastMonthEnd();
				send(ex, 200, "{\"user\":" + json(user) + ",\"accounts\":" + accountsJson(user)
						+ ",\"transactions\":" + transactionsJson(user)
						+ ",\"lastMonthEnd\":" + (last == null ? "null" : quote(last.toString())) + "}");
				break;
			case "POST /api/month-end":
				user.require(Permission.RUN_MONTH_END);
				MonthEndResult result = FileIO.bank.runMonthEnd();
				saveAndSend(ex, 200, "{\"month\":" + quote(result.getMonth().toString())
						+ ",\"interestCount\":" + result.getInterestCount()
						+ ",\"interestTotal\":" + result.getInterestTotal()
						+ ",\"feeCount\":" + result.getFeeCount()
						+ ",\"feeTotal\":" + result.getFeeTotal() + "}");
				break;
			case "POST /api/password":
				if (!user.checkPassword(value(form, "current")))
				{
					throw new IllegalArgumentException("The current password is wrong");
				}
				user.setPassword(form.get("password"));
				saveAndSend(ex, 200, "{}");
				break;
			case "POST /api/accounts":
				user.require(Permission.OPEN_ACCOUNT);
				// checked before opening, so a wrong name doesn't leave a new account behind
				User owner = customerOf(value(form, "owner"));
				BankAccount acc = FileIO.bank.openAccount(parseType(form.get("type")), form.get("name"),
						parseNumber(form.get("balance"), "Balance"), value(form, "extra"));
				if (owner != null)
				{
					FileIO.bank.linkAccount(owner.getUsername(), acc.getAccNum());
				}
				saveAndSend(ex, 201, "{\"account\":" + json(acc) + "}");
				break;
			case "POST /api/deposit":
				user.require(Permission.DEPOSIT);
				user.requireAccess(form.get("account"));
				FileIO.bank.deposit(form.get("account"), parseNumber(form.get("amount"), "Amount"));
				saveAndSend(ex, 200, "{\"account\":" + json(FileIO.bank.findAccount(form.get("account"))) + "}");
				break;
			case "POST /api/withdraw":
				user.require(Permission.WITHDRAW);
				user.requireAccess(form.get("account"));
				FileIO.bank.withdraw(form.get("account"), parseNumber(form.get("amount"), "Amount"));
				saveAndSend(ex, 200, "{\"account\":" + json(FileIO.bank.findAccount(form.get("account"))) + "}");
				break;
			case "POST /api/transfer":
				user.require(Permission.TRANSFER);
				// a customer may send to any account, but only from their own
				user.requireAccess(form.get("from"));
				FileIO.bank.transfer(form.get("from"), form.get("to"), parseNumber(form.get("amount"), "Amount"));
				saveAndSend(ex, 200, "{\"account\":" + json(FileIO.bank.findAccount(form.get("from"))) + "}");
				break;
			case "POST /api/close":
				user.require(Permission.CLOSE_ACCOUNT);
				saveAndSend(ex, 200, "{\"account\":" + json(FileIO.bank.closeAccount(form.get("account"))) + "}");
				break;
			case "GET /api/users":
				user.require(Permission.MANAGE_USERS);
				send(ex, 200, "{\"users\":" + usersJson() + ",\"roles\":" + rolesJson() + "}");
				break;
			case "POST /api/users":
				user.require(Permission.MANAGE_USERS);
				User created = FileIO.bank.addUser(form.get("username"), form.get("password"),
						parseRole(form.get("role")), parseList(form.get("accounts")));
				saveAndSend(ex, 201, "{\"user\":" + json(created) + "}");
				break;
			case "POST /api/users/accounts":
				user.require(Permission.MANAGE_USERS);
				FileIO.bank.setUserAccounts(value(form, "username"), parseList(form.get("accounts")));
				saveAndSend(ex, 200, "{\"user\":" + json(FileIO.bank.findUser(form.get("username"))) + "}");
				break;
			case "POST /api/users/delete":
				user.require(Permission.MANAGE_USERS);
				if (user == FileIO.bank.findUser(form.get("username")))
				{
					throw new IllegalArgumentException("You can't delete your own login");
				}
				User removed = FileIO.bank.removeUser(value(form, "username"));
				sessions.values().removeIf(u -> u == removed);
				saveAndSend(ex, 200, "{}");
				break;
			default:
				send(ex, 404, error("Not found"));
			}
		}
		catch (AccNotFound e)
		{
			send(ex, 404, error(e.getMessage()));
		}
		catch (AccessDenied e)
		{
			send(ex, 403, error(e.getMessage()));
		}
		catch (InvalidAmount | MaxBalance | MaxWithdraw | IllegalArgumentException e)
		{
			send(ex, 400, error(e.getMessage()));
		}
		catch (RuntimeException e)
		{
			e.printStackTrace();
			send(ex, 500, error("Something went wrong"));
		}
	}

	private void login(HttpExchange ex, Map<String, String> form) throws IOException
	{
		User user = FileIO.bank.findUser(value(form, "username"));
		if (user == null || !user.checkPassword(value(form, "password")))
		{
			send(ex, 401, error("Login Failed"));
			return;
		}
		byte[] bytes = new byte[24];
		random.nextBytes(bytes);
		StringBuilder token = new StringBuilder();
		for (byte b : bytes)
		{
			token.append(String.format("%02x", b));
		}
		sessions.put(token.toString(), user);
		// behind an HTTPS proxy, mark the cookie Secure so it is never sent over plain HTTP
		String secure = "https".equals(ex.getRequestHeaders().getFirst("X-Forwarded-Proto")) ? "; Secure" : "";
		ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict" + secure);
		send(ex, 200, "{\"user\":" + json(user) + "}");
	}

	/** Returns the session token of a logged-in user, or null. */
	private String sessionOf(HttpExchange ex)
	{
		String header = ex.getRequestHeaders().getFirst("Cookie");
		if (header == null)
		{
			return null;
		}
		for (String part : header.split(";"))
		{
			String[] pair = part.trim().split("=", 2);
			if (pair.length == 2 && pair[0].equals(COOKIE) && sessions.containsKey(pair[1]))
			{
				return pair[1];
			}
		}
		return null;
	}

	/** If writing the file fails, the change stays in memory and the user is told. */
	private static void saveAndSend(HttpExchange ex, int status, String body) throws IOException
	{
		try
		{
			FileIO.save();
		}
		catch (IOException e)
		{
			send(ex, 500, error("Could not save data: " + e.getMessage()));
			return;
		}
		send(ex, status, body);
	}

	private void handleFile(HttpExchange ex) throws IOException
	{
		if (!ex.getRequestMethod().equals("GET") && !ex.getRequestMethod().equals("HEAD"))
		{
			sendText(ex, 405, "Method not allowed");
			return;
		}
		String path = ex.getRequestURI().getPath();
		if (path.endsWith("/"))
		{
			path += "index.html";
		}
		// normalize() removes "..", and the startsWith check keeps requests inside the web folder
		Path file = ROOT.resolve(path.substring(1)).normalize();
		if (!file.startsWith(ROOT) || !Files.isRegularFile(file))
		{
			sendText(ex, 404, "Not found");
			return;
		}
		String name = file.getFileName().toString();
		String type = CONTENT_TYPES.get(name.substring(name.lastIndexOf('.') + 1));
		ex.getResponseHeaders().set("Content-Type", type == null ? "application/octet-stream" : type);
		ex.getResponseHeaders().set("Cache-Control", "no-cache");
		sendBytes(ex, 200, Files.readAllBytes(file));
	}

	// ---- request parsing

	private static Map<String, String> readForm(HttpExchange ex) throws IOException
	{
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		try (InputStream in = ex.getRequestBody())
		{
			byte[] buffer = new byte[4096];
			int n;
			while ((n = in.read(buffer)) != -1)
			{
				body.write(buffer, 0, n);
				if (body.size() > MAX_BODY)
				{
					throw new IllegalArgumentException("Request too large");
				}
			}
		}
		Map<String, String> form = new HashMap<String, String>();
		for (String pair : body.toString("UTF-8").split("&"))
		{
			if (pair.isEmpty())
			{
				continue;
			}
			String[] kv = pair.split("=", 2);
			form.put(decode(kv[0]), kv.length > 1 ? decode(kv[1]) : "");
		}
		return form;
	}

	private static String decode(String s) throws UnsupportedEncodingException
	{
		return URLDecoder.decode(s, "UTF-8");
	}

	private static String value(Map<String, String> form, String key)
	{
		String v = form.get(key);
		return v == null ? "" : v;
	}

	private static double parseNumber(String text, String fieldName)
	{
		try
		{
			double value = Double.parseDouble(text.trim());
			if (!Double.isNaN(value) && !Double.isInfinite(value))
			{
				return value;
			}
		}
		catch (NumberFormatException | NullPointerException e)
		{
			// reported below
		}
		throw new IllegalArgumentException(fieldName + " must be a valid number");
	}

	private static AccountType parseType(String name)
	{
		for (AccountType type : AccountType.values())
		{
			if (type.name().equals(name))
			{
				return type;
			}
		}
		throw new IllegalArgumentException("Choose an account type");
	}

	private static Role parseRole(String name)
	{
		for (Role role : Role.values())
		{
			if (role.name().equals(name))
			{
				return role;
			}
		}
		throw new IllegalArgumentException("Choose a role");
	}

	/** Splits "10001, 10002" (commas or spaces) into account numbers. */
	private static List<String> parseList(String text)
	{
		List<String> list = new ArrayList<String>();
		for (String part : (text == null ? "" : text).split("[,\\s]+"))
		{
			if (!part.isEmpty())
			{
				list.add(part);
			}
		}
		return list;
	}

	/** The customer login named in the open-account form, or null if the field was left empty. */
	private static User customerOf(String username)
	{
		if (username.trim().isEmpty())
		{
			return null;
		}
		User owner = FileIO.bank.findUser(username);
		if (owner == null || owner.getRole() != Role.CUSTOMER)
		{
			throw new IllegalArgumentException("There is no customer login " + username.trim());
		}
		return owner;
	}

	// ---- JSON output

	private static String typesJson()
	{
		StringBuilder sb = new StringBuilder("{\"types\":[");
		for (AccountType type : AccountType.values())
		{
			if (type.ordinal() > 0)
			{
				sb.append(',');
			}
			sb.append("{\"id\":").append(quote(type.name()))
				.append(",\"label\":").append(quote(type.getLabel()))
				.append(",\"minBalance\":").append(type.getMinBalance())
				.append(",\"extraLabel\":").append(quote(type.getExtraLabel()))
				.append('}');
		}
		return sb.append("]}").toString();
	}

	/** The accounts this user may see: all of them for staff, their own for a customer. */
	private static String accountsJson(User user)
	{
		StringBuilder sb = new StringBuilder("[");
		for (BankAccount acc : FileIO.bank.getAccounts())
		{
			if (!user.canAccess(acc.getAccNum()))
			{
				continue;
			}
			if (sb.length() > 1)
			{
				sb.append(',');
			}
			sb.append(json(acc));
		}
		return sb.append(']').toString();
	}

	private static String json(User user)
	{
		return "{\"username\":" + quote(user.getUsername())
				+ ",\"role\":" + quote(user.getRole().name())
				+ ",\"roleLabel\":" + quote(user.getRole().getLabel())
				+ ",\"permissions\":" + namesJson(user.getRole().getPermissions())
				+ ",\"accounts\":" + stringsJson(user.getAccounts()) + "}";
	}

	private static String usersJson()
	{
		StringBuilder sb = new StringBuilder("[");
		for (User user : FileIO.bank.getUsers())
		{
			if (sb.length() > 1)
			{
				sb.append(',');
			}
			sb.append(json(user));
		}
		return sb.append(']').toString();
	}

	private static String rolesJson()
	{
		StringBuilder sb = new StringBuilder("[");
		for (Role role : Role.values())
		{
			if (role.ordinal() > 0)
			{
				sb.append(',');
			}
			sb.append("{\"id\":").append(quote(role.name()))
				.append(",\"label\":").append(quote(role.getLabel()))
				.append(",\"permissions\":").append(namesJson(role.getPermissions()))
				.append('}');
		}
		return sb.append(']').toString();
	}

	private static String namesJson(Collection<? extends Enum<?>> values)
	{
		List<String> names = new ArrayList<String>();
		for (Enum<?> value : values)
		{
			names.add(value.name());
		}
		return stringsJson(names);
	}

	private static String stringsJson(Collection<String> values)
	{
		StringBuilder sb = new StringBuilder("[");
		for (String value : values)
		{
			if (sb.length() > 1)
			{
				sb.append(',');
			}
			sb.append(quote(value));
		}
		return sb.append(']').toString();
	}

	private static String json(BankAccount acc)
	{
		return "{\"accNum\":" + quote(acc.getAccNum())
				+ ",\"name\":" + quote(acc.getName())
				+ ",\"type\":" + quote(acc.getType().name())
				+ ",\"typeLabel\":" + quote(acc.getType().getLabel())
				+ ",\"balance\":" + acc.getBalance()
				+ ",\"minBalance\":" + acc.getMinBalance()
				+ ",\"withdrawLimit\":" + (acc instanceof SavingsAccount ? ((SavingsAccount) acc).getMaxWithLimit() : "null")
				+ ",\"netBalance\":" + (acc instanceof SavingsAccount ? ((SavingsAccount) acc).getNetBalance() : "null")
				+ ",\"interestRate\":" + (acc instanceof InterestBearing ? ((InterestBearing) acc).getInterestRate() : "null")
				+ ",\"monthlyInterest\":" + (acc instanceof InterestBearing ? ((InterestBearing) acc).getMonthlyInterest() : "null")
				+ ",\"monthlyFee\":" + (acc instanceof FeeCharging ? ((FeeCharging) acc).getMonthlyFee() : "null")
				+ ",\"extra\":" + quote(extraOf(acc))
				+ ",\"details\":" + quote(acc.getDetails()) + "}";
	}

	/** The type-specific value entered when the account was opened (see AccountType.getExtraLabel). */
	private static String extraOf(BankAccount acc)
	{
		if (acc instanceof StudentAccount)
		{
			return ((StudentAccount) acc).getInstitutionName();
		}
		if (acc instanceof CurrentAccount)
		{
			return ((CurrentAccount) acc).getTradeLicenseNumber();
		}
		if (acc instanceof SavingsAccount)
		{
			return String.valueOf(((SavingsAccount) acc).getMaxWithLimit());
		}
		return "";
	}

	private static String transactionsJson(User user)
	{
		StringBuilder sb = new StringBuilder("[");
		for (Transaction t : FileIO.bank.getTransactions())
		{
			if (!user.canAccess(t.getAccNum()))
			{
				continue;
			}
			if (sb.length() > 1)
			{
				sb.append(',');
			}
			sb.append("{\"id\":").append(t.getId())
				.append(",\"time\":").append(t.getTime())
				.append(",\"kind\":").append(quote(t.getKind().name()))
				.append(",\"kindLabel\":").append(quote(t.getKind().getLabel()))
				.append(",\"credit\":").append(t.getKind().isCredit())
				.append(",\"accNum\":").append(quote(t.getAccNum()))
				.append(",\"name\":").append(quote(t.getName()))
				.append(",\"amount\":").append(t.getAmount())
				.append(",\"balanceAfter\":").append(t.getBalanceAfter())
				.append(",\"otherAccNum\":").append(t.getOtherAccNum() == null ? "null" : quote(t.getOtherAccNum()))
				.append('}');
		}
		return sb.append(']').toString();
	}

	private static String error(String message)
	{
		return "{\"error\":" + quote(message) + "}";
	}

	private static String quote(String s)
	{
		StringBuilder sb = new StringBuilder("\"");
		for (char c : s.toCharArray())
		{
			if (c == '"' || c == '\\')
			{
				sb.append('\\').append(c);
			}
			else if (c < 0x20)
			{
				sb.append(String.format("\\u%04x", (int) c));
			}
			else
			{
				sb.append(c);
			}
		}
		return sb.append('"').toString();
	}

	// ---- responses

	private static void send(HttpExchange ex, int status, String json) throws IOException
	{
		ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		ex.getResponseHeaders().set("Cache-Control", "no-store");
		sendBytes(ex, status, json.getBytes(StandardCharsets.UTF_8));
	}

	private static void sendText(HttpExchange ex, int status, String text) throws IOException
	{
		ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
		sendBytes(ex, status, text.getBytes(StandardCharsets.UTF_8));
	}

	private static void sendBytes(HttpExchange ex, int status, byte[] bytes) throws IOException
	{
		ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
		boolean head = ex.getRequestMethod().equals("HEAD");
		ex.sendResponseHeaders(status, head ? -1 : bytes.length);
		try (OutputStream out = ex.getResponseBody())
		{
			if (!head)
			{
				out.write(bytes);
			}
		}
	}
}
