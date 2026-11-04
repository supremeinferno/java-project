package Data;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Bank.Bank;

/**
 * Keeps the bank in PostgreSQL when DATABASE_URL is set: one row holding the serialized Bank,
 * written in a single statement, so a save is all-or-nothing. Needs the PostgreSQL JDBC driver
 * on the classpath (the Docker image adds it).
 */
final class Database {

	private final String url;
	private final Properties props = new Properties();

	/** Returns null when DATABASE_URL is not set. */
	static Database fromEnv()
	{
		String raw = System.getenv("DATABASE_URL");
		if (raw == null || raw.trim().isEmpty())
		{
			return null;
		}
		// tolerate a value pasted with extras, e.g. psql 'postgresql://...' or surrounding quotes
		Matcher m = Pattern.compile("(jdbc:postgresql://|postgres(?:ql)?://)[^\\s'\"]+").matcher(raw);
		if (!m.find())
		{
			throw new IllegalStateException("DATABASE_URL is not a PostgreSQL connection string; it should start with postgresql://");
		}
		try
		{
			return new Database(m.group());
		}
		catch (IllegalArgumentException e)
		{
			// the message would include the URL, and with it the password
			throw new IllegalStateException("DATABASE_URL could not be read; copy it again from the Neon Connect dialog");
		}
	}

	/** Accepts postgres://user:password@host/db?sslmode=require (as Neon, Render and Supabase give it) or a jdbc: URL. */
	private Database(String raw) {
		if (raw.startsWith("jdbc:"))
		{
			url = raw;
			return;
		}
		URI uri = URI.create(raw);
		if (uri.getRawUserInfo() != null)
		{
			String[] user = uri.getRawUserInfo().split(":", 2);
			props.setProperty("user", decode(user[0]));
			if (user.length > 1)
			{
				props.setProperty("password", decode(user[1]));
			}
		}
		StringBuilder query = new StringBuilder();
		if (uri.getRawQuery() != null)
		{
			for (String param : uri.getRawQuery().split("&"))
			{
				// libpq-only option that the JDBC driver does not understand
				if (!param.startsWith("channel_binding="))
				{
					query.append(query.length() == 0 ? "?" : "&").append(param);
				}
			}
		}
		url = "jdbc:postgresql://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + uri.getRawPath() + query;
	}

	private static String decode(String s)
	{
		try
		{
			return URLDecoder.decode(s, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private Connection connect() throws SQLException
	{
		Connection c = DriverManager.getConnection(url, props);
		try (Statement s = c.createStatement())
		{
			s.execute("CREATE TABLE IF NOT EXISTS bank_state (id INT PRIMARY KEY, data BYTEA NOT NULL, saved_at TIMESTAMPTZ NOT NULL DEFAULT now())");
		}
		return c;
	}

	/** Never returns an empty bank when the saved one can't be read; the next save would overwrite it. */
	Bank load()
	{
		try (Connection c = connect();
				PreparedStatement s = c.prepareStatement("SELECT data FROM bank_state WHERE id = 1");
				ResultSet r = s.executeQuery())
		{
			if (!r.next())
			{
				return new Bank();
			}
			try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(r.getBytes(1))))
			{
				return (Bank) in.readObject();
			}
		}
		catch (SQLException | IOException | ClassNotFoundException | ClassCastException e)
		{
			throw new IllegalStateException("Could not load the bank from the database: " + e.getMessage(), e);
		}
	}

	void save(Bank bank) throws IOException
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes))
		{
			out.writeObject(bank);
		}
		try (Connection c = connect();
				PreparedStatement s = c.prepareStatement("INSERT INTO bank_state (id, data, saved_at) VALUES (1, ?, now()) "
						+ "ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data, saved_at = now()"))
		{
			s.setBytes(1, bytes.toByteArray());
			s.executeUpdate();
		}
		catch (SQLException e)
		{
			throw new IOException("database error: " + e.getMessage(), e);
		}
	}

	/** Host and database name, without the password, for the startup log. */
	String describe()
	{
		return url.replaceFirst("^jdbc:postgresql://", "").replaceFirst("\\?.*$", "");
	}
}
