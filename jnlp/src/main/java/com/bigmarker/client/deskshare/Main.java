package com.bigmarker.client.deskshare;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.net.BindException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;

import javax.jnlp.IntegrationService;
import javax.jnlp.ServiceManager;
import javax.jnlp.SingleInstanceListener;
import javax.jnlp.SingleInstanceService;
import javax.jnlp.UnavailableServiceException;

import com.bigmarker.net.NetworkHttpStreamSender;
import com.bigmarker.util.LibraryExtractor;

import fi.iki.elonen.NanoHTTPD;

/**
 * The main application entry point for control and coordination.
 * 
 * @author Paul Gregoire
 */
public class Main {

	/*
	 * http://docs.oracle.com/javase/tutorial/uiswing/misc/systemtray.html
	 * http://stackoverflow.com/questions/758083/how-do-i-put-a-java-app-in-the-system-tray
	 * http://stackoverflow.com/questions/7461477/how-to-hide-a-jframe-in-system-tray-of-taskbar
	 */

	private static ResourceBundle MESSAGES = ResourceBundle.getBundle("resource");

	// are we a mac?
	private static final boolean isMacOSX = System.getProperty("os.name").toLowerCase().indexOf("mac os x") >= 0;

	// are we a windows box?
	@SuppressWarnings("unused")
	private static final boolean isWindows = System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0;

	private static final String INTERNAL_HTTP_REQUEST_URI = "http://127.0.0.1:1936/%s";
	
	// ref: http://www.adobe.com/devnet/flashplayer/articles/fplayer9_security.html
	private static final String crossdomain = "<?xml version=\"1.0\"?><cross-domain-policy><allow-access-from domain=\"*\" secure=\"false\" /></cross-domain-policy>";

	// internal httpd
	private static NanoHTTPD httpd;

	// capture thread
	private static transient Thread captureThread;

	// the application view instance
	private static UIView view;

	// unique id
	private static String id;

	private static String bbbAppsUri;

	private static String room;
	
	private static String userName;
	
	private static String streamName;

	private static boolean fullScreen;

	private static long maximumPingTime = 1 * (60 * 1000); // 1 minute
	
	private static long requestedMaxPingTime = 0L;

	private static AtomicLong lastPingTime = new AtomicLong(System.currentTimeMillis());

	static {
		// generates a unique id
		generateUniqueId();
		// check for any existing instances
		checkExistingInstance();
		// check integration with the host
		checkIntegration();
		// create shutdown listener
		createHttpListener();
		// create ping time checker
		createPingTimeChecker();
		// create the shutdown hook
		Runtime.getRuntime().addShutdownHook(new ShutdownHook());
		// detect OS / CPU arch and then extract the xuggle library into a tmp location 
		LibraryExtractor.detectAndExtract();
	}

	/**
	 * Return string for given key.
	 * 
	 * @see http://translate.google.com/
	 * 
	 * @param key
	 * @return resource string
	 */
	public static String getString(String key) {
		return MESSAGES.getString(key);
	}

	/**
	 * Sets the current locale.
	 * 
	 * @param locale
	 */
	public static void setLocale(Locale locale) {
		System.out.println("Set locale: " + locale.toString());
		Locale.setDefault(locale);
		MESSAGES = ResourceBundle.getBundle("resource", locale);
	}

	/**
	 * Sets the current locale.
	 * 
	 * @param language
	 * @param country
	 */
	public static void setLocale(String language, String country) {
		Locale locale = new Locale(language, country);
		setLocale(locale);
	}	
	
	/**
	 * Returns the locale for a given string.
	 * 
	 * @param localeString
	 */
	public static void setLocale(String localeString) {
		//System.out.println("Set locale: " + localeString);
		Locale locale = null;
		String parts[] = localeString.replace("-", "_").split("_", -1);
		if (parts.length == 1) {
			locale = new Locale(parts[0]);
		} else if (parts.length == 2 || (parts.length == 3 && parts[2].startsWith("#"))) {
			locale = new Locale(parts[0], parts[1]);
		} else {
			locale = new Locale(parts[0], parts[1], parts[2]);
		}
		setLocale(locale);
	}

	/**
	 * Get the number of screens.
	 * 
	 * @return screen count
	 */
	public static int getScreenCount() {
		return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length;
	}

	/**
	 * Get screen at index.
	 * 
	 * @param screenIndex
	 * @return screen
	 */
	public static GraphicsDevice getScreen(int screenIndex) {
		return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[screenIndex];
	}

	/**
	 * Enforces any change set during parameters for the maximum ping time
	 */
	public static void enforcePingTime() {
		if (Main.requestedMaxPingTime > 0L) {
			System.out.println("Enforcing requested maximum ping time of " + Main.requestedMaxPingTime);
			Main.maximumPingTime = Main.requestedMaxPingTime;
		}
	}
	
	/**
	 * Dispatches a message to the view.
	 * 
	 * @param message
	 */
	public static void appendMessage(String message) {
		if (view != null) {
			view.dispatchMessage(message);
		} else {
			System.out.print(message);
		}
	}

	/**
	 * Start the ui.
	 * 
	 * @param view
	 */
	public static void start(UIView view) {
		System.out.println(getString("msg.starting"));
		if (view instanceof DebugUI) {
			view.setSize(420, 480);
		} else if (view instanceof SmallUI) {
			view.setSize(240, 120);
		}
		view.createUI();
		// center self on screen
		view.setLocationRelativeTo(null);
	}

	/**
	 * Stop and exit.
	 */
	public static void stop(boolean useSystemExit) {
		System.out.println(getString("msg.stopandexit"));
		lastPingTime = null;
		if (httpd != null) {
			if (httpd.isAlive()) {
				httpd.stop();
			}
			httpd = null;
		}
		if (view != null) {
			view.destroy();
			view = null;
		}
		if (captureThread != null) {
			try {
				// wait at most 2s for the capture thread to complete its stop
				captureThread.join(2000);
				captureThread = null;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (useSystemExit) {
			System.exit(1);
		}
	}

	/**
	 * Something bad has happened to which we probably cannot recover.
	 */
	public static void fail() {
		System.err.println(getString("err.general"));
		Main.sendExit();
		Main.stop(true);
	}

	/**
	 * Creates a new http sender with required parameters
	 */
	public static NetworkHttpStreamSender getNewHttpSender(){
		return new NetworkHttpStreamSender(bbbAppsUri, room, userName, streamName);
	}
	
	public static void setUserName(String un){
		userName = un;
	}
	
	public static void setStreamName(String sn){
		streamName = sn;
	}
	
	/**
	 * Sends the exit event to the apps uri.
	 */
	public static void sendExit() {
		getNewHttpSender().exit();
	}
	
	/**
	 * Generates and sets a unique id.
	 */
	public static void generateUniqueId() {
		byte[] uniqueKey = new java.rmi.server.UID().toString().getBytes();
		byte[] hash = null;
		try {
			hash = MessageDigest.getInstance("MD5").digest(uniqueKey);
		} catch (NoSuchAlgorithmException e) {
			throw new Error("no MD5 support in this VM");
		}
		StringBuffer hashString = new StringBuffer();
		for (int i = 0; i < hash.length; ++i) {
			String hex = Integer.toHexString(hash[i]);
			if (hex.length() == 1) {
				hashString.append('0');
				hashString.append(hex.charAt(hex.length() - 1));
			} else {
				hashString.append(hex.substring(hex.length() - 2));
			}
		}
		id = hashString.toString();
	}

	/**
	 * Sets a reference to the capture thread so we may join on it when we stop / exit.
	 * 
	 * @param captureThead
	 */
	public static void setCaptureThread(Thread captureThread) {
		Main.captureThread = captureThread;
	}

	/**
	 * Whether or not to use full screen mode in swing.
	 * 
	 * @return
	 */
	public static boolean useFullScreen() {
		return fullScreen;
	}

	/**
	 * Returns true if running on a mac / osx.
	 * 
	 * @return
	 */
	public static boolean isMacOSX() {
		return isMacOSX;
	}

	/**
	 * Sends an HTTP request to the localhost.
	 * 
	 * @param queryString
	 * @throws InterruptedException 
	 */
	private static void sendHttpRequest(final String queryString) throws InterruptedException {
		Thread req = new Thread() {
			@Override
			public void run() {
				try {
					URL url = new URL(String.format(INTERNAL_HTTP_REQUEST_URI, queryString));
					System.out.println("URL: " + url.toExternalForm());
					URLConnection conn = url.openConnection();
					conn.setConnectTimeout(2000);
					conn.setUseCaches(false);
					System.out.println("HTTP server response: " + conn.getHeaderField("Server"));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		req.start();
		req.join(2000);
	}

	/**
	 * Performs a "kill" request to ensure no other running instances are available.
	 */
	private static void checkExistingInstance() {
		try {
			SingleInstanceService singleInstanceService = (SingleInstanceService) ServiceManager.lookup("javax.jnlp.SingleInstanceService");
			singleInstanceService.addSingleInstanceListener((SingleInstanceListener) new SingletonInstanceListener());
		} catch (UnavailableServiceException e) {
			System.err.println("Singleton service unavailable");
			// attempt to kill any exiting instance
			try {
				sendHttpRequest("kill");
			} catch (InterruptedException ex) {
				//ex.printStackTrace();
			}
		}
	}

	/**
	 * Checks that the application is integrated into the host.
	 * 
	 * @see http://docs.oracle.com/javase/7/docs/jre/api/javaws/jnlp/javax/jnlp/IntegrationService.html
	 */
	private static void checkIntegration() {
		System.out.println("Integration check");
		IntegrationService is = null;
		try {
			is = (IntegrationService) ServiceManager.lookup("javax.jnlp.IntegrationService");
			/* No shortcut atm
			// the shortcut string must match the <title> in the jnlp file
			if (!is.requestShortcut(true, false, "Desktop Sharing")) {
				// failed to install shortcuts
				System.err.println("Shortcut creation failed");
			}
			*/
			if (!is.hasAssociation("application/x-bigmarker-deskshare", new String[] { "bmdeskshare", "bmds" })) {
				if (!is.requestAssociation("application/x-bigmarker-deskshare", new String[] { "bmdeskshare", "bmds" })) {
					// failed to install shortcuts
					System.err.println("Association creation failed");
				}
			} else {
				// association already exists
				System.out.println("Mime-type association exists");
			}
		} catch (UnavailableServiceException use) {
			System.err.println("Integration service unavailable");
		}
	}

	/**
	 * Creates the ping time checker
	 */
	private static void createPingTimeChecker() {
		Thread pingChecker = new Thread(new Runnable() {
			public void run() {
				do {
					long elapsedPingTime = System.currentTimeMillis() - lastPingTime.get();
					//System.out.println("Ping times - elapsed: " + elapsedPingTime + " max: " + maximumPingTime);
					if (elapsedPingTime > maximumPingTime) {
						System.out.println("Maximum ping time has been exceeded. Elapsed time: " + elapsedPingTime + " ms");
						Main.stop(true);
					}
					try {
						Thread.sleep(250L);
					} catch (InterruptedException e) {
					}
				} while (lastPingTime != null);
			}
		}, "PingChecker");
		//pingChecker.setDaemon(true);
		pingChecker.start();
	}

	/**
	 * Creates the http listener
	 */
	private static void createHttpListener() {
		// create socket to listen for http connections
		httpd = new NanoHTTPD("127.0.0.1", 1936) {
			@Override
			public Response serve(IHTTPSession session) {
				Response response = new NanoHTTPD.Response(Response.Status.BAD_REQUEST, "text/html", "Bad request");
				String uri = session.getUri();
				//Method method = session.getMethod();
				//System.out.println("Method: " + method + " '" + uri + "' ");
				//System.out.println("Headers: " + session.getHeaders());
				if (uri.indexOf("crossdomain.xml") != -1) {
					System.out.println("Sending crossdomain xml");
					response = new NanoHTTPD.Response(crossdomain);	
				} else if (uri.indexOf("kill") != -1) {
					System.out.println("Shutdown connection accepted");
					new Thread() {
						@Override
						public void run() {
							Main.stop(true);
						}
					}.start();
					response = new NanoHTTPD.Response("<html><body>Ok</body></html>");
				} else if (uri.indexOf("ping") != -1) {
					// ping
					// store last ping time for live-ness check
					lastPingTime.set(System.currentTimeMillis());
					// return pong
					response = new NanoHTTPD.Response("<html><body>Pong</body></html>");
				} else if (uri.indexOf("params") != -1) {
					// if the view is not null skip this call
					if (view == null) {
						// params
						Map<String, String> params = session.getParms();
						// get locale
						String locale = params.get("locale");
						if (locale != null) {
							setLocale(locale);
						}
						// get client type
						String clientType = params.get("c");
						String appsURI = params.get("apps_uri");
						String videoURI = params.get("video_uri");
						String roomName = params.get("rm");
						String sn = params.get("sn");
						streamName = sn;
						String un = params.get("u");
						userName = un;
						String fs = params.get("fs");
						// max ping time - this goes into effect after the first frame is sent
						String maxPingMs = params.get("max_ping_ms");
						if (maxPingMs != null) {
							requestedMaxPingTime = Long.valueOf(maxPingMs);
						}
						// image scaling
						String useScaling = params.get("scale");
						if (useScaling != null) {
							System.setProperty("bmds.scaleimages", useScaling);
						}
						// save screen images to disk
						String saveScreenImage = params.get("ssi");
						if (saveScreenImage != null) {
							System.setProperty("bmds.savescreenimage", saveScreenImage);
						}
						// codec properties
						String codecProps = params.get("codec");
						if (codecProps != null) {
							// save to a sysprop and parse in the capture instance
							System.setProperty("bmds.codecprops", codecProps);
						}						
						System.out.println("Parameters: " + params);
						if ("debug".equals(clientType)) {
							// large ui with all options available
							view = new DebugUI();
						} else if ("small".equals(clientType)) {
							// the 3 button ui with snipping
							view = new SmallUI();
						} else {
							// end-user ui
							view = new SimpleUI();
						}
						// apps uri
						StringBuilder appsUri = new StringBuilder();
						if (appsURI != null) {
							if (!appsURI.startsWith("http")) {
								appsUri.append("http://");
								appsUri.append(appsURI);
							} else {
								appsUri.append(appsURI);
							}
						} else {
							appsUri.append("http://yourred5server/bigbluebutton/dscontrol.jspx");
						}
						// video uri
						StringBuilder videoUri = new StringBuilder();
						if (videoURI != null) {
							if (!videoURI.startsWith("rtmp")) {
								videoUri.append("rtmp://");
								videoUri.append(videoURI);
							} else {
								videoUri.append(videoURI);
							}
						} else {
							videoUri.append("rtmp://yourred5server/video/");
						}
						// add room
						if (roomName != null) {
							// set class var
							room = roomName;
							// ensure that the uri ends with a '/' if the room arg doesnt start with one
							if (videoUri.lastIndexOf("/") != videoUri.length() - 1) {
								if (!roomName.startsWith("/")) {
									videoUri.append('/');
								}
							} else {
								if (roomName.startsWith("/")) {
									videoUri.deleteCharAt(videoUri.length() - 1);
								}
							}
							videoUri.append(roomName);
						} else {
							room = "demo";
							videoUri.append(room);
						}
						// get / set stream name
						if (streamName != null) {
							view.setStreamName(streamName);
						} else {
							view.setStreamName("screencap");
						}
						// check for user id
						if (userName != null) {
							System.out.println("UID: " + userName);
						}
						// check for full screen flag
						if (fs != null) {
							fullScreen = Boolean.valueOf(fs);
							System.out.println("Use full screen: " + fullScreen);
						}
						// set class var for bbb-apps
						bbbAppsUri = appsUri.toString();
						
						String videoAppUri = videoUri.toString();
						System.out.println("Video uri: " + videoAppUri + " Apps uri: " + bbbAppsUri);

						// set uri to the rtmp server
						view.setServerUri(videoAppUri);
						// check for osx options
						if (isMacOSX()) {
							// set osx options
							// @see (https://developer.apple.com/library/mac/documentation/java/Reference/Java_PropertiesRef/Articles/JavaSystemProperties.html#//apple_ref/doc/uid/TP40008047)
							String p = params.get("menubar");
							System.setProperty("apple.laf.useScreenMenuBar", (p != null ? p : "false"));
							p = params.get("capalldisp");
							System.setProperty("apple.awt.fullscreencapturealldisplays", (p != null ? p : "false"));
							p = params.get("hidecursor");
							System.setProperty("apple.awt.fullscreenhidecursor", (p != null ? p : "false"));
							p = params.get("uie");
							System.setProperty("apple.awt.UIElement", (p != null ? p : "true"));
						}
						// start
						new Thread() {
							@Override
							public void run() {
								Main.start(view);
							}
						}.start();
						response = new NanoHTTPD.Response("<html><body>Ok</body></html>");
					} else {
						response = new NanoHTTPD.Response("<html><body>Bad Request, view exists</body></html>");
					}
				}
				// add our id header
				response.addHeader("BigMarker-Capture-Id", id);
				return response;
			}
		};
		try {
			httpd.start();
			System.out.println("Internal service started");
		} catch (BindException ex) {
			System.out.println("Exception starting internal service, address already in-use");
			Main.fail();
		} catch (Exception ex) {
			System.out.println("Exception starting internal service");
			ex.printStackTrace();
		}
	}

	/**
	 * Starts the application and parses the given parameters.
	 * 
	 * <ul>
	 * <li>0 = ui type (simple or debug)</li>
	 * <li>1 = video uri and apps uri (separated by comma)</li>
	 * <li>2 = room / scope</li>
	 * <li>3 = stream name</li>
	 * <li>4 = user id</li>
	 * <li>5 = full screen</li>
	 * </ul>
	 * <i>Param #0 will be "-open" if launched via mime request</i>
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			if (args != null && args.length > 0) {
				System.out.println("Args: " + Arrays.toString(args));
				// build a request from the incoming parameters to consolidate launching between args and http
				StringBuilder queryString = new StringBuilder("params?");
				if (args[0] != null) {
					if ("-open".equals(args[0])) {
						// mime-type launch request from the OS
						// TODO read as a properties file and pass parameters via http

					} else {
						// #0 append client type
						queryString.append("c=");
						queryString.append(args[0]);
						// #1 uris
						if (args.length > 1 && args[1] != null) {
							String[] uriParams = new String[1];
							// look for uri separator
							if (args[1].indexOf(',') != -1) {
								uriParams = args[1].split(",");
								// do apps uri
								queryString.append("&apps_uri=");
								queryString.append(uriParams[1]);
							} else {
								uriParams[0] = args[1];
							}
							// do video uri
							queryString.append("&video_uri=");
							queryString.append(uriParams[0]);
							// #2 room
							if (args.length > 2 && args[2] != null) {
								queryString.append("&rm=");
								queryString.append(args[2]);
								// #3 stream name
								queryString.append("&sn=");
								if (args.length > 3 && args[3] != null) {
									queryString.append(args[3]);
								} else {
									queryString.append("screencap");
								}
								// #4 user id
								if (args.length > 4 && args[4] != null) {
									System.out.println("UID: " + args[4]);
									queryString.append("&u=");
									queryString.append(args[4]);
								}
								// #5 full screen flag
								if (args.length > 5 && args[5] != null) {
									System.out.println("Use full screen: " + fullScreen);
									queryString.append("&fs=");
									queryString.append(args[5]);
								}
							}
						}
						// sleep until httpd is available
						while (!httpd.isAlive()) {
							System.out.println("Internal service not available yet");
							Thread.sleep(100L);
						}
						// do http request
						sendHttpRequest(queryString.toString());
					}
				}
			}
		} catch (Throwable t) {
			Main.fail();
		}
		//System.out.println("Exit main");
	}

	/**
	 * Prevents multiple instances. 
	 */
	public static class SingletonInstanceListener implements SingleInstanceListener {

		public void newActivation(String[] args) {
			System.err.println(getString("err.multi.instance"));
			Main.stop(true);
		}

	}

	/**
	 * A shutdown hook to all items to be done on shutdown. Things that should
	 * be done at exit need to be placed here.
	 */
	public static class ShutdownHook extends Thread {

		public void run() {
			System.out.println(getString("msg.shutdownhook"));
			// items to be executed when this application is shutdown need to be placed below this comment
			Main.stop(false);
		}

	}

}
