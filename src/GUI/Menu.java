package GUI;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class Menu extends JFrame {

	private static final long serialVersionUID = 1L;

	public Menu() {
		setTitle("Banking System");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				exit();
			}
		});

		JLabel title = new JLabel("Banking System", SwingConstants.CENTER);
		title.setFont(new Font("Tahoma", Font.BOLD, 24));
		title.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
		add(title, BorderLayout.NORTH);

		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 10));
		buttons.setBorder(BorderFactory.createEmptyBorder(10, 30, 20, 30));
		addButton(buttons, "Add Account", () -> Ui.show("add", AddAccount::new));
		addButton(buttons, "Deposit To Account", () -> Ui.show("deposit", () -> new TransactionForm(TransactionForm.Mode.DEPOSIT)));
		addButton(buttons, "Withdraw From Account", () -> Ui.show("withdraw", () -> new TransactionForm(TransactionForm.Mode.WITHDRAW)));
		addButton(buttons, "Display Account List", () -> Ui.show("list", DisplayList::new));
		addButton(buttons, "Exit", this::exit);
		add(buttons, BorderLayout.CENTER);

		URL image = Menu.class.getResource("/img/1.png");
		if (image != null)
		{
			add(new JLabel(new ImageIcon(image)), BorderLayout.EAST);
		}
		pack();
	}

	private void addButton(JPanel panel, String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.addActionListener(e -> action.run());
		panel.add(button);
	}

	private void exit()
	{
		if (Ui.save(this))
		{
			Ui.info(this, "Thanks For Using");
			System.exit(0);
		}
	}
}
