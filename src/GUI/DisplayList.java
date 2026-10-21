package GUI;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import Bank.BankAccount;
import Data.FileIO;

public class DisplayList extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final String[] COLUMNS = { "Account No", "Name", "Type", "Balance", "Details" };

	private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};

	public DisplayList() {
		setTitle("Account List");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		JTable table = new JTable(model);
		TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<DefaultTableModel>(model);
		table.setRowSorter(sorter);

		JTextField search = new JTextField(25);
		search.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				filter(sorter, search.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				filter(sorter, search.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				filter(sorter, search.getText());
			}
		});
		JPanel searchBar = new JPanel();
		searchBar.add(new JLabel("Search:"));
		searchBar.add(search);
		add(searchBar, BorderLayout.NORTH);
		add(new JScrollPane(table), BorderLayout.CENTER);
		setPreferredSize(new Dimension(750, 420));
		pack();

		// refresh whenever the window is brought forward, so it never shows stale balances
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowActivated(WindowEvent e) {
				reload();
			}
		});
		reload();
	}

	private static void filter(TableRowSorter<DefaultTableModel> sorter, String text)
	{
		String query = text.trim();
		sorter.setRowFilter(query.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(query)));
	}

	private void reload()
	{
		model.setRowCount(0);
		for (BankAccount acc : FileIO.bank.getAccounts())
		{
			model.addRow(new Object[] { acc.getAccNum(), acc.getName(), acc.getType().getLabel(),
					String.format("%.2f", acc.getBalance()), acc.getDetails() });
		}
	}
}
