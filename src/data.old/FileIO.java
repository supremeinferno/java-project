package Data;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import Bank.Bank;

public class FileIO {

	private static final File FILE = new File("data");
	private static final Database DATABASE = Database.fromEnv();

	public static Bank bank = new Bank();

	/** Where the bank is kept, for the startup log. */
	public static String storage()
	{
		return DATABASE != null ? "PostgreSQL (" + DATABASE.describe() + ")" : "file " + FILE.getAbsolutePath();
	}

	/** Loads the bank from disk; an unreadable file is kept as data.old and a new bank is started. */
	public static void load()
	{
		if (DATABASE != null)
		{
			bank = DATABASE.load();
			return;
		}
		if (!FILE.exists())
		{
			bank = new Bank();
			return;
		}
		try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(FILE.toPath())))
		{
			bank = (Bank) in.readObject();
		}
		catch (IOException | ClassNotFoundException | ClassCastException e)
		{
			System.err.println("Could not read " + FILE + " (" + e + "); starting with an empty bank");
			bank = new Bank();
			try
			{
				Files.move(FILE.toPath(), new File("data.old").toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException ignored)
			{
			}
		}
	}

	/** Writes the bank to a temp file first so a failed save never destroys the old data. */
	public static void save() throws IOException
	{
		if (DATABASE != null)
		{
			DATABASE.save(bank);
			return;
		}
		File tmp = new File("data.tmp");
		try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(tmp.toPath())))
		{
			out.writeObject(bank);
		}
		Files.move(tmp.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}
}
