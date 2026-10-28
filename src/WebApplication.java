import java.awt.Desktop;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.nio.file.Files;

import Bank.DemoData;
import Data.FileIO;
import Web.WebServer;

/**
 * Starts the website version of the Banking System and opens it in the browser.
 * Optional argument: port (default: the PORT environment variable, else 8080).
 */
public class WebApplication {
	public static void main(String[] args) throws IOException
	{
		// a cloud host passes the port in PORT
		String envPort = System.getenv("PORT");
		int port = args.length > 0 ? Integer.parseInt(args[0]) : envPort != null ? Integer.parseInt(envPort) : 8080;
		if (!Files.isDirectory(WebServer.ROOT))
		{
			System.err.println("Folder " + WebServer.ROOT + " not found. Run this from the project folder.");
			System.exit(1);
		}
		try
		{
			System.out.println("Data: " + FileIO.storage());
			FileIO.load();
		}
		catch (IllegalStateException | ExceptionInInitializerError e)
		{
			// refuse to start rather than run with an empty bank that would overwrite the saved one
			Throwable cause = e instanceof ExceptionInInitializerError && e.getCause() != null ? e.getCause() : e;
			System.err.println("Could not start: " + cause.getMessage());
			System.exit(1);
		}
		if (FileIO.bank.size() == 0 && FileIO.bank.getTransactions().isEmpty() && "true".equals(System.getenv("DEMO_DATA")))
		{
			// a fresh public deployment starts with demo accounts instead of an empty bank
			FileIO.bank = DemoData.create();
			FileIO.save();
			System.out.println("Loaded demo data: " + FileIO.bank.size() + " accounts");
		}
		try
		{
			new WebServer(port).start();
		}
		catch (BindException e)
		{
			System.err.println("Port " + port + " is busy - the website is probably already running. "
					+ "Stop the old one first, or open http://localhost:" + port);
			System.exit(1);
		}
		String url = "http://localhost:" + port;
		System.out.println("Banking System website running at " + url + "  (press Ctrl+C to stop)");
		openBrowser(url);
	}

	private static void openBrowser(String url)
	{
		try
		{
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
			{
				Desktop.getDesktop().browse(URI.create(url));
			}
		}
		catch (Exception e)
		{
			// no browser available; the URL is printed above
		}
	}
}
