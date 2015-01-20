package com.bigmarker.client.deskshare;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Debugging UI.
 * 
 * @author Paul Gregoire
 */
public class DebugUI extends JFrame implements ActionListener, UIView {

	private static final long serialVersionUID = -2324145527020860555L;

	private static DebugUI app;
	
	private static ScreenCap capture;

	// state
	private static AtomicBoolean started = new AtomicBoolean(false);

	private static AtomicBoolean destroyed = new AtomicBoolean(false);

	// ui
	private JButton startStopButton = new JButton("Start");

	private JButton snipButton = new JButton("Snip");

	private JButton exitButton = new JButton("Exit");

	private static JTextArea messages = new JTextArea();

	// k/v map
	private Map<String, JTextField> fields = new HashMap<String, JTextField>();

	private String serverUri = "rtmp://localhost/vod";

	private String streamName = "screencap";

    public void createUI() {
		setVisible(true);
    	// set self reference (for tray)
    	app = this;
		// set the title
		setTitle("BigMarker Deskshare");
    	
		// holder for the "form"
		JPanel formPanel = new JPanel();
		// form holder
		formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
		// uri
		JPanel uriPanel = new JPanel();
		uriPanel.setLayout(new BoxLayout(uriPanel, BoxLayout.X_AXIS));
		JLabel l1 = new JLabel("RTMP URI:", JLabel.RIGHT);
		l1.setPreferredSize(new Dimension(80, 32));
		uriPanel.add(l1);
		JTextField tf1 = new JTextField(serverUri, 32);
		uriPanel.add(tf1);
		fields.put("uri", tf1);
		formPanel.add(uriPanel);
		// stream name
		JPanel snPanel = new JPanel();
		snPanel.setLayout(new BoxLayout(snPanel, BoxLayout.X_AXIS));
		JLabel l2 = new JLabel("Stream:", JLabel.RIGHT);
		l2.setPreferredSize(new Dimension(80, 32));
		snPanel.add(l2);
		JTextField tf2 = new JTextField(streamName, 32);
		snPanel.add(tf2);
		fields.put("streamName", tf2);
		formPanel.add(snPanel);
		// holder for bitrate and fps
		JPanel bfPanel = new JPanel();		
		bfPanel.setLayout(new FlowLayout());		
		// bitrate
		JPanel brPanel = new JPanel();
		brPanel.setLayout(new BoxLayout(brPanel, BoxLayout.X_AXIS));
		JLabel l3 = new JLabel("Bitrate:", JLabel.RIGHT);
		l3.setPreferredSize(new Dimension(42, 32));
		brPanel.add(l3);
		JTextField tf3 = new JTextField("360000", 8);
		brPanel.add(tf3);
		fields.put("bitrate", tf3);
		bfPanel.add(brPanel);
		// fps
		JPanel fPanel = new JPanel();
		fPanel.setLayout(new BoxLayout(fPanel, BoxLayout.X_AXIS));
		JLabel l4 = new JLabel("FPS:", JLabel.RIGHT);
		l4.setPreferredSize(new Dimension(40, 32));
		fPanel.add(l4);
		JTextField tf4 = new JTextField("30", 8);
		fPanel.add(tf4);
		fields.put("fps", tf4);
		bfPanel.add(fPanel);	
		formPanel.add(bfPanel);	
		// holder for x and y
		JPanel xyPanel = new JPanel();		
		xyPanel.setLayout(new FlowLayout());
		// x axis
		JPanel xPanel = new JPanel();
		xPanel.setLayout(new BoxLayout(xPanel, BoxLayout.X_AXIS));
		JLabel lblX = new JLabel("X Axis:", JLabel.RIGHT);
		lblX.setPreferredSize(new Dimension(40, 32));
		xPanel.add(lblX);
		JTextField tfX = new JTextField("0", 8);
		xPanel.add(tfX);
		fields.put("x", tfX);
		xyPanel.add(xPanel);
		// y axis
		JPanel yPanel = new JPanel();
		yPanel.setLayout(new BoxLayout(yPanel, BoxLayout.X_AXIS));
		JLabel lblY = new JLabel("Y Axis:", JLabel.RIGHT);
		lblY.setPreferredSize(new Dimension(40, 32));
		yPanel.add(lblY);
		JTextField tfY = new JTextField("0", 8);
		yPanel.add(tfY);
		fields.put("y", tfY);
		xyPanel.add(yPanel);			
		formPanel.add(xyPanel);	
		// holder for width and height
		JPanel whPanel = new JPanel();		
		whPanel.setLayout(new FlowLayout());
		// width
		JPanel widthPanel = new JPanel();
		widthPanel.setLayout(new BoxLayout(widthPanel, BoxLayout.X_AXIS));
		JLabel lblWidth = new JLabel("Width:", JLabel.RIGHT);
		lblWidth.setPreferredSize(new Dimension(60, 32));
		widthPanel.add(lblWidth);
		JTextField tfWidth = new JTextField("1920", 8);
		widthPanel.add(tfWidth);
		fields.put("width", tfWidth);
		whPanel.add(widthPanel);	
		// height
		JPanel heightPanel = new JPanel();
		heightPanel.setLayout(new BoxLayout(heightPanel, BoxLayout.X_AXIS));
		JLabel lblHeight = new JLabel("Height:", JLabel.RIGHT);
		lblHeight.setPreferredSize(new Dimension(40, 32));
		heightPanel.add(lblHeight);
		JTextField tfHeight = new JTextField("1200", 8);
		heightPanel.add(tfHeight);
		fields.put("height", tfHeight);
		whPanel.add(heightPanel);				
		formPanel.add(whPanel);				
		// codec id
		JPanel cPanel = new JPanel();
		cPanel.setLayout(new BoxLayout(cPanel, BoxLayout.X_AXIS));
		JLabel l6 = new JLabel("Codec id:", JLabel.RIGHT);
		l6.setPreferredSize(new Dimension(40, 32));
		cPanel.add(l6);
		JTextField tf6 = new JTextField("3", 8);
		cPanel.add(tf6);
		fields.put("codecId", tf6);
		formPanel.add(cPanel);				
		// TODO add display / screen selection
		// button holder
		JPanel bottomPanel = new JPanel();		
		bottomPanel.setLayout(new FlowLayout());
		bottomPanel.add(startStopButton);
		bottomPanel.add(snipButton);
		bottomPanel.add(exitButton);
		// overall holder
		JPanel holdAll = new JPanel();
		holdAll.setLayout(new BorderLayout());
		holdAll.add(formPanel, BorderLayout.NORTH);
		holdAll.add(messages, BorderLayout.CENTER);
		holdAll.add(bottomPanel, BorderLayout.SOUTH);

		getContentPane().add(holdAll, BorderLayout.CENTER);

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
		trayIcon.setImageAutoSize(true); // Autosize icon base on space available on tray
		MouseAdapter mouseAdapter = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt) {
				System.out.println("icon clicked: " + evt.getClickCount());
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
	
	public void updateBounds() {
		if (capture.isBoundsSet()) {
			fields.get("x").setText(capture.getX() + "");
			fields.get("y").setText(capture.getY() + "");
			fields.get("width").setText(capture.getWidth() + "");
			fields.get("height").setText(capture.getHeight() + "");
			messages.append("Bounds set\n");		
		}
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
							capture.setRtmpUrl(fields.get("uri").getText());
							capture.setStreamName(fields.get("streamName").getText());
							capture.setBitrate(Integer.valueOf(fields.get("bitrate").getText()));
							capture.setFps(Integer.valueOf(fields.get("fps").getText()));
							capture.setX(Integer.valueOf(fields.get("x").getText()));
							capture.setY(Integer.valueOf(fields.get("y").getText()));
							capture.setWidth(Integer.valueOf(fields.get("width").getText()));
							capture.setHeight(Integer.valueOf(fields.get("height").getText()));
							capture.setCodecId(Integer.valueOf(fields.get("codecId").getText()));
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
			updateBounds();
		} else {
			fields.get("x").setText(x + "");
			fields.get("y").setText(y + "");
			fields.get("width").setText(width + "");
			fields.get("height").setText(height + "");
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
