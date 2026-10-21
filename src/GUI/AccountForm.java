package GUI;
import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import Bank.AccountType;
import Bank.BankAccount;
import Data.FileIO;

/** One form for every account type; only the type-specific field differs. */
public class AccountForm extends JFrame {

	private static final long serialVersionUID = 1L;
	private final AccountType type;
	private final JTextField nameField = new JTextField(20);
	private final JTextField balanceField = new JTextField(20);
	private final JTextField extraField = new JTextField(20);

	public AccountForm(AccountType type) {
		this.type = type;
		setTitle("Add " + type.getLabel());
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		JLabel title = new JLabel("Add " + type.getLabel(), SwingConstants.CENTER);
		title.setFont(new Font("Tahoma", Font.BOLD, 16));
		title.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));

		JPanel form = Ui.formPanel();
		Ui.addRow(form, 0, "Name:", nameField);
		Ui.addRow(form, 1, "Balance (min " + type.getMinBalance() + "):", balanceField);
		Ui.addRow(form, 2, type.getExtraLabel() + ":", extraField);

		JButton add = new JButton("Add");
		add.addActionListener(e -> submit());
		JButton reset = new JButton("Reset");
		reset.addActionListener(e -> reset());
		JPanel buttons = new JPanel();
		buttons.add(add);
		buttons.add(reset);
		getRootPane().setDefaultButton(add);

		add(title, BorderLayout.NORTH);
		add(form, BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);
		pack();
	}

	private void submit()
	{
		Double balance = Ui.parseNumber(this, balanceField.getText(), "Balance");
		if (balance == null || !Ui.confirm(this))
		{
			return;
		}
		try
		{
			BankAccount acc = FileIO.bank.openAccount(type, nameField.getText(), balance, extraField.getText());
			if (Ui.save(this))
			{
				Ui.info(this, "Added Successfully. Account number: " + acc.getAccNum());
				dispose();
			}
		}
		catch (IllegalArgumentException ex)
		{
			Ui.error(this, ex.getMessage());
		}
	}

	private void reset()
	{
		nameField.setText(null);
		balanceField.setText(null);
		extraField.setText(null);
	}
}
