package com.bigmarker.client.deskshare;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * Small UI.
 * 
 * @author Paul Gregoire
 */
public class SmallUI extends JFrame implements ActionListener, UIView {

	private static final long serialVersionUID = -2324145527020860567L;

	private static SmallUI app;

	private static ScreenCap capture;

	// state
	private static AtomicBoolean started = new AtomicBoolean(false);

	private static AtomicBoolean destroyed = new AtomicBoolean(false);

	// ui
	private JButton startStopButton = new JButton("Start");

	private JButton snipButton = new JButton("Snip");

	private JButton exitButton = new JButton("Exit");

	private static JTextArea messages = new JTextArea();

	private String serverUri = "rtmp://localhost/vod";

	private String streamName = "screencap";

	private int[] bounds = new int[] { 0, 0, 352, 288 };

	public void createUI() {
		setVisible(true);
		// set self reference (for tray)
		app = this;
		// set the title
		setTitle("BigMarker Deskshare");

		JPanel btnPanel = new JPanel();
		btnPanel.setLayout(new GridLayout(1, 3, 1, 1));
		btnPanel.add(startStopButton);
		btnPanel.add(snipButton);
		btnPanel.add(exitButton);

		getContentPane().add(btnPanel, BorderLayout.CENTER);

		startStopButton.addActionListener(this);
		snipButton.addActionListener(this);
		exitButton.addActionListener(this);

		if (SystemTray.isSupported()) {
			// set up the system tray
			initTray();
			// hide on close and restore via the tray icon
			setDefaultCloseOperation(HIDE_ON_CLOSE);
		} else {
			// dispose on close since the tray is not supported
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		}
	}

	private void initTray() {
		final SystemTray systemTray = SystemTray.getSystemTray();
		final TrayIcon trayIcon = new TrayIcon(getImage("icon.gif"), "Deskshare is running");
		trayIcon.setImageAutoSize(true); // Autosize icon base on space
										 // available on tray
		MouseAdapter mouseAdapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt) {
				// This will display small popup message from System Tray
				trayIcon.displayMessage("BigMarker Deskshare", "This is an info message", TrayIcon.MessageType.INFO);
				if (!app.isVisible()) {
					app.setVisible(true);
				}
			}
		};
		trayIcon.addMouseListener(mouseAdapter);
		try {
			systemTray.add(trayIcon);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setServerUri(String serverUri) {
		this.serverUri = serverUri;
	}

	public void setStreamName(String streamName) {
		this.streamName = streamName;
	}

	public Image getImage(String path) {
		ImageIcon icon = new ImageIcon(path, "icon");
		return icon.getImage();
	}

	public static void appendMessage(String message) {
		if (messages != null) {
			messages.append(message);
		}
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == exitButton) {
			destroy();
		} else if (e.getSource() == snipButton) {
			if (started.get()) {
				messages.append("Cannot snip region while capturing\n");
			} else {
				if (capture == null) {
					capture = new ScreenCap(0);
				}
				messages.append("Preparing snip tool...\n");
				new SnipIt(this);
			}
		} else if (e.getSource() == startStopButton) {
			if (started.compareAndSet(false, true)) {
				messages.append("Starting\n");
				startStopButton.setText("Stop");
				Thread cap = new Thread(new Runnable() {
					public void run() {
						try {
							// capture and stream
							if (capture == null) {
								capture = new ScreenCap(0);
							}
							capture.setRtmpUrl(serverUri);
							capture.setStreamName(streamName);
							capture.start();
						} catch (Throwable t) {
							// capture didnt work, destroy so they can start over
							destroy();
						}
					}
				}, "CaptureThread");
				cap.setDaemon(true);
				// set a reference to the thread on Main
				Main.setCaptureThread(cap);
				// start the thread
				cap.start();
			} else {
				started.set(false);
				messages.append("Stop\n");
				startStopButton.setText("Start");
				// stop capture / streaming
				capture.stop();
			}
		} else {
			messages.append("Unknown action " + e.getSource() + "\n");
		}
	}

	public void updateBounds(int x, int y, int width, int height) {
		if (capture != null) {
			capture.setBounds(x, y, width, height);
		} else {
			bounds[0] = x;
			bounds[1] = y;
			bounds[2] = width;
			bounds[3] = height;
		}
	}

	public void dispatchMessage(String message) {
		if (messages != null) {
			messages.append(message);
		}
	}

	@Override
	public void dispose() {
		destroy();
		super.dispose();
	}

	public void destroy() {
		if (destroyed.compareAndSet(false, true)) {
			if (capture != null) {
				capture.stop();
			}
			dispose();
			System.exit(1);
		}
	}

}
