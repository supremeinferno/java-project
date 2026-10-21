import java.awt.EventQueue;

import Data.FileIO;
import GUI.Login;

public class Application {
	public static void main(String[] args)
	{
		FileIO.load();
		EventQueue.invokeLater(() -> new Login().setVisible(true));
	}
}
