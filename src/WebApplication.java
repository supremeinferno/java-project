import java.awt.Desktop;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.nio.file.Files;

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
		FileIO.load();
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
