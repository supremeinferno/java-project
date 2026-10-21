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

import Data.FileIO;
import Exceptions.AccNotFound;
import Exceptions.InvalidAmount;
import Exceptions.MaxBalance;
import Exceptions.MaxWithdraw;

/** Deposit and withdraw share one form. */
public class TransactionForm extends JFrame {

	public enum Mode {
		DEPOSIT("Deposit To Account", "Deposit"),
		WITHDRAW("Withdraw From Account", "Withdraw");

		private final String title;
		private final String action;

		Mode(String title, String action) {
			this.title = title;
			this.action = action;
		}
	}

	private static final long serialVersionUID = 1L;
	private final Mode mode;
	private final JTextField accountField = new JTextField(20);
	private final JTextField amountField = new JTextField(20);

	public TransactionForm(Mode mode) {
		this.mode = mode;
		setTitle(mode.title);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		JLabel title = new JLabel(mode.title, SwingConstants.CENTER);
		title.setFont(new Font("Tahoma", Font.BOLD, 16));
		title.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));

		JPanel form = Ui.formPanel();
		Ui.addRow(form, 0, "Account Number:", accountField);
		Ui.addRow(form, 1, "Amount:", amountField);

		JButton submit = new JButton(mode.action);
		submit.addActionListener(e -> submit());
		JButton reset = new JButton("Reset");
		reset.addActionListener(e -> reset());
		JPanel buttons = new JPanel();
		buttons.add(submit);
		buttons.add(reset);
		getRootPane().setDefaultButton(submit);

		add(title, BorderLayout.NORTH);
		add(form, BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);
		pack();
	}

	private void submit()
	{
		Double amount = Ui.parseNumber(this, amountField.getText(), "Amount");
		if (amount == null || !Ui.confirm(this))
		{
			return;
		}
		String accNum = accountField.getText();
		try
		{
			if (mode == Mode.DEPOSIT)
			{
				FileIO.bank.deposit(accNum, amount);
			}
			else
			{
				FileIO.bank.withdraw(accNum, amount);
			}
		}
		catch (AccNotFound | InvalidAmount | MaxBalance | MaxWithdraw ex)
		{
			Ui.error(this, ex.getMessage());
			return;
		}
		if (Ui.save(this))
		{
			Ui.info(this, mode.action + " Successful");
			dispose();
		}
	}

	private void reset()
	{
		accountField.setText(null);
		amountField.setText(null);
	}
}
