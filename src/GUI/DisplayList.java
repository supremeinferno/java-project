package GUI;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

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
		table.setAutoCreateRowSorter(true);
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
