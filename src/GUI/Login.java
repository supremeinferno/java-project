package GUI;
import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public class Login extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final String USER = "admin";
	private static final String PASSWORD = "admin";

	private final JTextField userField = new JTextField(USER, 12);
	private final JPasswordField passField = new JPasswordField(12);

	public Login() {
		setTitle("Banking System");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		JLabel title = new JLabel("Banking System", SwingConstants.CENTER);
		title.setFont(new Font("Tahoma", Font.BOLD, 17));
		title.setBorder(javax.swing.BorderFactory.createEmptyBorder(15, 0, 0, 0));

		JPanel form = Ui.formPanel();
		Ui.addRow(form, 0, "Username:", userField);
		Ui.addRow(form, 1, "Password:", passField);

		JButton login = new JButton("Login");
		login.addActionListener(e -> attemptLogin());
		getRootPane().setDefaultButton(login);
		JPanel buttons = new JPanel();
		buttons.add(login);

		add(title, BorderLayout.NORTH);
		add(form, BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);
		pack();
		setLocationRelativeTo(null);
	}

	private void attemptLogin()
	{
		String pass = new String(passField.getPassword());
		if (USER.equals(userField.getText().trim()) && PASSWORD.equals(pass))
		{
			dispose();
			Ui.show("menu", Menu::new);
		}
		else
		{
			Ui.error(this, "Login Failed");
			passField.setText(null);
		}
	}
}
