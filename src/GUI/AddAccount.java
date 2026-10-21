package GUI;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import Bank.AccountType;

/** Lets the user pick which type of account to open. */
public class AddAccount extends JFrame {

	private static final long serialVersionUID = 1L;

	public AddAccount() {
		setTitle("Add Account");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		JPanel panel = new JPanel(new GridLayout(0, 1, 0, 10));
		panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
		for (AccountType type : AccountType.values())
		{
			JButton button = new JButton("Add " + type.getLabel());
			button.addActionListener(e -> {
				Ui.show("add-" + type, () -> new AccountForm(type));
				dispose();
			});
			panel.add(button);
		}
		setContentPane(panel);
		pack();
	}
}
