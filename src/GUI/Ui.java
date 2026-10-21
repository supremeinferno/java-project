package GUI;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import Data.FileIO;

/** Shared helpers for the windows. */
final class Ui {

	private static final Map<String, JFrame> open = new HashMap<String, JFrame>();

	private Ui() {
	}

	/** Shows the window made by {@code factory}, or brings the existing one to the front. */
	static void show(String key, Supplier<JFrame> factory)
	{
		JFrame frame = open.get(key);
		if (frame == null || !frame.isDisplayable())
		{
			frame = factory.get();
			open.put(key, frame);
			frame.setLocationRelativeTo(null);
		}
		frame.setVisible(true);
		frame.toFront();
	}

	static JPanel formPanel()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(new EmptyBorder(15, 15, 15, 15));
		return panel;
	}

	static void addRow(JPanel panel, int row, String label, Component field)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = row;
		c.insets = new Insets(6, 6, 6, 6);
		c.anchor = GridBagConstraints.WEST;
		panel.add(new JLabel(label), c);
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(field, c);
	}

	/** Parses a number typed by the user; shows a message and returns null if it is not valid. */
	static Double parseNumber(Component parent, String text, String fieldName)
	{
		try
		{
			double value = Double.parseDouble(text.trim());
			if (!Double.isNaN(value) && !Double.isInfinite(value))
			{
				return value;
			}
		}
		catch (NumberFormatException e)
		{
			// reported below
		}
		error(parent, fieldName + " must be a valid number");
		return null;
	}

	static void error(Component parent, String message)
	{
		JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
	}

	static void info(Component parent, String message)
	{
		JOptionPane.showMessageDialog(parent, message);
	}

	static boolean confirm(Component parent)
	{
		return JOptionPane.showConfirmDialog(parent, "Confirm?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	}

	/** Saves the bank; returns false (after telling the user) if that fails. */
	static boolean save(Component parent)
	{
		try
		{
			FileIO.save();
			return true;
		}
		catch (IOException e)
		{
			error(parent, "Could not save data: " + e.getMessage());
			return false;
		}
	}
}
