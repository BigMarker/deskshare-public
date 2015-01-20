package com.bigmarker.net;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.bigmarker.client.deskshare.common.CaptureEvents;
import com.bigmarker.client.deskshare.common.Dimension;
import com.bigmarker.util.SequenceNumberGenerator;

public class NetworkHttpStreamSender {

	private static int TIMEOUT_VALUE = 7000;

	private String uri = "http://yourred5serverfqdn:5080/bigbluebutton/dscontrol.jspx";

	private String room;
	
	private String userName;
	
	private String streamName;

	private final SequenceNumberGenerator seqNumGenerator;

	private ExecutorService executor;

	public NetworkHttpStreamSender(String uri, String room, String userName, String streamName) {
		if (uri != null && uri.length() > 7) {
			this.uri = uri;
		}
		this.room = room;
		this.userName = userName;
		this.streamName = streamName;
		this.seqNumGenerator = new SequenceNumberGenerator();
		// create an executor
		executor = Executors.newFixedThreadPool(1);
	}

	private void openConnection(String qs) throws ConnectException {
		URL url = null;
		try {
			String urlString = String.format("%s?%s", uri, qs);
			//System.out.println("URL: " + urlString);
			url = new URL(urlString);
			URLConnection conn = url.openConnection();
			conn.setConnectTimeout(TIMEOUT_VALUE);
			conn.setUseCaches(false);
			@SuppressWarnings("unused")
			String header = conn.getHeaderField("Server");
			//System.out.println("HTTP server response: " + header);
		} catch (SocketTimeoutException e) {
			throw new ConnectException("SocketTimeoutException " + TIMEOUT_VALUE + " elapsed");
		} catch (MalformedURLException e) {
			throw new ConnectException("MalformedURLException " + url.toString());
		} catch (IOException e) {
			throw new ConnectException("IOException while connecting to " + url.toString());
		}
	}

	/**
	 * Connect and send start event.
	 */
	public void sendCaptureStartEvent(final Dimension screenDim) {
		executor.submit(new Runnable() {
			@SuppressWarnings("deprecation")
			public void run() {
				try {
					userName = URLEncoder.encode(userName);
					String request = String.format("room=%s&userName=%s&streamName=%s&sequenceNumber=%d&screenInfo=%dx%d&event=%d", room, userName, streamName, seqNumGenerator.getNext(), screenDim.getWidth(),
							screenDim.getHeight(), CaptureEvents.CAPTURE_START.getEvent());
					openConnection(request);
					System.out.println("Start Request: " + request);
				} catch (ConnectException e) {
					System.out.println("ERROR: Failed to send start event");
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Send mouse coordinates.
	 * 
	 * @param mouseX
	 * @param mouseY
	 */
	public void sendMouseCoordinates(final int mouseX, final int mouseY) {
		executor.submit(new Runnable() {
			public void run() {
				try {
					openConnection(String.format("room=%s&sequenceNumber=%d&event=%d&mousex=%d&mousey=%d", room, seqNumGenerator.getNext(),
							CaptureEvents.MOUSE_LOCATION_EVENT.getEvent(), mouseX, mouseY));
				} catch (ConnectException e) {
					System.out.println("ERROR: Failed to send cursor data");
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Disconnect and send end event.
	 */
	public void sendCaptureEndEvent() {
		if (executor != null && !executor.isShutdown()) {
			executor.submit(new Runnable() {
				public void run() {
					try {
						openConnection(String.format("room=%s&sequenceNumber=%d&event=%d", room, seqNumGenerator.getNext(), CaptureEvents.CAPTURE_END.getEvent()));
					} catch (ConnectException e) {
						System.out.println("ERROR: Failed to send end event");
						e.printStackTrace();
					}
				}
			});
			try {
				if (!executor.awaitTermination(2000, TimeUnit.MILLISECONDS)) {
					executor.shutdown();
				}
			} catch (InterruptedException e) {
			}
		}
	}

	public void exit() {
		if (executor != null && !executor.isShutdown()) {
			executor.submit(new Runnable() {
				public void run() {
					try {
						openConnection(String.format("room=%s&sequenceNumber=%d&event=%d", room, seqNumGenerator.getNext(), CaptureEvents.EXIT.getEvent()));
					} catch (ConnectException e) {
						System.out.println("ERROR: Failed to send exit event");
						e.printStackTrace();
					}
				}
			});
			try {
				if (!executor.awaitTermination(2000, TimeUnit.MILLISECONDS)) {
					executor.shutdown();
				}
			} catch (InterruptedException e) {
			}
		}
	}

}
