package com.bigmarker.client.deskshare;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Image;
import java.awt.Insets;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Simple UI.
 * 
 * @author Paul Gregoire
 */
public class SimpleUI extends JFrame implements ActionListener, UIView {

	private static final long serialVersionUID = -2324145527020860566L;

	// state
	private static AtomicBoolean started = new AtomicBoolean(false);

	private static AtomicBoolean destroyed = new AtomicBoolean(false);

	private static ScreenCap capture;

	private Thread captureThread;

	private ScreenThumbButton[] screenButtons;

	private String serverUri = "rtmp://yourred5server/video";
	
	private String streamName = "screencap";
	
	private boolean allowScreenSwitch;

	// thumbnail width and height
	int thumbWidth = 240, thumbHeight = 160; // QVGA size

	public void createUI() {
		// check for tray support
		if (SystemTray.isSupported()) {
			// set up the system tray
			initTray();
		} else {
			System.err.println("System tray is not supported");
		}		
		// set the title
		setTitle(Main.getString("app.name"));
		// get the number of screens
		int screenCount = Main.getScreenCount();
		System.out.println("Screens: " + screenCount);
		// create buttons for the screens if there's more than 1
		if (screenCount == 1) {
			setVisible(false);
			// if just one screen select it automatically
			start(0);
		} else {
			setVisible(true);
			// create array to hold buttons
			screenButtons = new ScreenThumbButton[screenCount];
			for (int s = 0; s < screenCount; s++) {
				try {
					// get the screen
					GraphicsDevice dev = Main.getScreen(s);
					GraphicsConfiguration conf = dev.getDefaultConfiguration();
					Rectangle rect = new Rectangle(0, 0, conf.getBounds().width, conf.getBounds().height);
					// create robot for capturing thumbnail
					Robot robot = new Robot(dev);
					BufferedImage captured = robot.createScreenCapture(rect);
					System.out.println("Screen: " + s + " bounds: " + conf.getBounds());
					BufferedImage thumb = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
					Graphics g = thumb.createGraphics();
					g.drawImage(captured, 0, 0, thumbWidth, thumbHeight, null);
					g.dispose();
					// create new button
					JButton button = new JButton();
					button.setActionCommand(s + "");
					button.setIcon(new ImageIcon(thumb));
					System.out.println("Button pref size: " + button.getPreferredSize());
					// store in the array
					screenButtons[s] = new ScreenThumbButton(button, conf.getBounds().x);
					System.out.println("Button created for screen: " + s);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			// add to a panel
			JPanel screensPanel = new JPanel();
			screensPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 2, 2));
			screensPanel.setBackground(Color.GRAY);
			// sort the array
			Arrays.sort(screenButtons);
			// create the displayed screen thumbs
			for (ScreenThumbButton sb : screenButtons) {
				JButton jb = sb.getButton();
				System.out.println("Adding button: " + jb.getActionCommand());
				screensPanel.add(jb);
				jb.addActionListener(this);
			}
			// set the preferred size based on the screen count 3 by x layout
			int cols = screenCount < 3 ? screenCount : 3;
			int rows = screenCount <= 3 ? 1 : (screenCount % 3 == 0 ? (screenCount / 3) : (screenCount / 3) + 1);
			System.out.println("Rows: " + rows + " cols: " + cols);
			screensPanel.setPreferredSize(new Dimension((screenButtons[0].getButton().getPreferredSize().width + 4) * cols, (screenButtons[0].getButton().getPreferredSize().height + 4) * rows));
			// add a content pane
			JPanel pane = new JPanel();
			pane.setBackground(Color.GRAY);
			// set the layout for the content pane
			pane.setLayout(null);
			// add the pane
			getContentPane().add(pane);
			// add a label
			JLabel label = new JLabel(Main.getString("text.selectscreen"));
			label.setFont(new Font("Sans Serif", Font.BOLD, 14));
			label.setForeground(Color.WHITE);
			// add the label
			pane.add(label);
			// add the screens
			pane.add(screensPanel);
			// modify the positions
			Insets insets = pane.getInsets();
			java.awt.Dimension sz = label.getPreferredSize();
			label.setBounds(insets.left + 10, insets.top + 10, sz.width, sz.height);
			sz = screensPanel.getPreferredSize();
			screensPanel.setBounds(insets.left + 10, insets.top + 30, sz.width, sz.height);
			//System.out.println("Width: " + getSize().width + " insets + left: " + (getSize().width / 2 - sz.width / 2));
			// resize the frame
			setSize(new Dimension(sz.width + 30, sz.height + 70));
			// dispose on close, we assume no screen selection was made
			addWindowListener(new WindowAdapter() {
			    @Override
			    public void windowClosing(WindowEvent windowEvent) {
			    	System.out.println("Closing UI");
			    	// send exit
					Main.sendExit();
					// stop and exit app
			    	Main.stop(true);
			    }
			});		
			// set on-top
			setAlwaysOnTop(true);
			// bring to front
			toFront();
		}
	}

	private void initTray() {
		// get the system tray
		SystemTray systemTray = SystemTray.getSystemTray();
		// create a "hidden" frame for the popup menu
		final Frame frame = new Frame("");
        frame.setUndecorated(true);
		try {
	        // instance the icon
	        BufferedImage icon = ImageIO.read(SimpleUI.class.getResource("/icon_16x16x32.png"));
			final TrayIcon trayIcon = new TrayIcon(new ImageIcon(icon).getImage(), Main.getString("msg.systemtray"));
			trayIcon.setImageAutoSize(true); // Autosize icon base on space available on tray
			final PopupMenu menu = createPopupMenu();
	        trayIcon.setPopupMenu(menu);
			trayIcon.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent evt) {
					System.out.println("icon clicked: " + evt.getClickCount());
					// dont display the menu if the screen selection ui (this jframe) is showing
					if (!isVisible()) {
			            frame.setVisible(true);
						frame.add(menu);
						menu.show(frame, evt.getXOnScreen(), evt.getYOnScreen());
					}
				}
			});
			frame.setResizable(false);
			systemTray.add(trayIcon);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private PopupMenu createPopupMenu() {
		final PopupMenu popup = new PopupMenu();
		if (allowScreenSwitch) {
			MenuItem switchItem = new MenuItem(Main.getString("text.switchscreen"));
			switchItem.setActionCommand("switch");
			switchItem.addActionListener(this);
			// add to popup
			popup.add(switchItem);
		}
		MenuItem exitItem = new MenuItem(Main.getString("text.exit"));
		exitItem.setActionCommand("exit");
		exitItem.addActionListener(this);
		// add to pop-up menu
		popup.add(exitItem);
		return popup;
	}

	public boolean isAllowScreenSwitch() {
		return allowScreenSwitch;
	}

	public void setAllowScreenSwitch(boolean allowScreenSwitch) {
		this.allowScreenSwitch = allowScreenSwitch;
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
		System.out.println(message);
	}

//	@Override
//	public void setSize(int width, int height) {
//		float triplets = Math.max((float) (screenButtons.length / 3.0), 1f);
//		int w = ((screenButtons.length + 1) * (thumbWidth + 8));
//		int h = (int) ((triplets * thumbHeight) + 72);
//		// set on this frame
//		super.setSize(w, h);
//	}

	@Override
	public void updateBounds(int x, int y, int width, int height) {
	}

	@Override
	public void dispatchMessage(String message) {
		System.out.println(message);
	}

	public void actionPerformed(ActionEvent e) {
		System.out.println("Action performed: " + e.getActionCommand());
		if (e.getSource() instanceof JButton) {
			start(Integer.valueOf(((JButton) e.getSource()).getActionCommand()));
		} else {
			// assume menu item
			String action = e.getActionCommand();
			if ("switch".equals(action)) {
				start(-1);
			} else if ("exit".equals(action)) {
				Main.stop(true);
			}
		}
	}

	// start capture
	private void start(final int screenIndex) {
		if (started.compareAndSet(false, true)) {
			appendMessage("Starting");
			captureThread = new Thread(new Runnable() {
				public void run() {
					try {
						// capture and stream
						if (capture == null) {
							capture = new ScreenCap();
						}
						capture.setScreenIndex(screenIndex);
						capture.setRtmpUrl(serverUri);
						capture.setStreamName(streamName);
						capture.start();
					} catch (Throwable t) {
						// capture didnt work, destroy so they can start over
						t.printStackTrace();
						destroy();
					}
				}
			}, "CaptureThread");
			captureThread.setDaemon(true);
			// set a reference to the thread on Main
			Main.setCaptureThread(captureThread);
			// start the thread
			captureThread.start();
			// hide the ui
			setVisible(false);
		} else {
			// perform stop
			stop();
			// show the ui
			setVisible(true);
		}					
	}
	
	// stop capture
	private void stop() {
		if (capture != null) {
			appendMessage("Stopping");
			try {
				capture.stop();
				captureThread.join(3000);
				Main.setCaptureThread(null);
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			} finally {
				// reset state
				started.set(false);				
			}
		}
	}
	
	@Override
	public void dispose() {
		destroy();
		super.dispose();
	}

	public void destroy() {
		if (destroyed.compareAndSet(false, true)) {
			stop();
			dispose();
		}
	}

	private final class ScreenThumbButton implements Comparable<ScreenThumbButton> {

		private JButton button;

		private int xCoordinate;

		ScreenThumbButton(JButton button, int xCoordinate) {
			this.button = button;
			this.xCoordinate = xCoordinate;
		}

		/**
		 * @return the button
		 */
		public JButton getButton() {
			return button;
		}

		/**
		 * @return the xCoordinate
		 */
		public int getxCoordinate() {
			return xCoordinate;
		}

		public int compareTo(ScreenThumbButton that) {
			if (this.getxCoordinate() > that.getxCoordinate()) {
				return 1;
			} else if (this.getxCoordinate() < that.getxCoordinate()) {
				return -1;
			}
			return 0;
		}

	}

}
