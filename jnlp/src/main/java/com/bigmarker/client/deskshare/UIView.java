package com.bigmarker.client.deskshare;

import java.awt.Component;

/**
 * Common interface for the UI view.
 * 
 * @author Paul Gregoire
 */
public interface UIView {

	/**
	 * Set visibility.
	 * 
	 * @param visible
	 */
	void setVisible(boolean visible);

	/**
	 * Returns visible state.
	 * 
	 * @return true if visible and false if hidden
	 */
	boolean isVisible();
	
	
	/**
	 * Sets the streaming servers uri.
	 * 
	 * @param uri
	 */
	void setServerUri(String uri);
	
	/**
	 * Sets the stream name.
	 * 
	 * @param streamName
	 */
	void setStreamName(String streamName);

	/**
	 * Creates and displays the UI.
	 */
	void createUI();
	
	/**
	 * Sets the location relative to the given component.
	 * 
	 * @param c
	 */
	void setLocationRelativeTo(Component c);
	
	/**
	 * Sets the UI frame dimensions.
	 * 
	 * @param width
	 * @param height
	 */
	void setSize(int width, int height);
	
	/**
	 * Modify area of the screen being captured.
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 */
	void updateBounds(int x, int y, int width, int height);
	
	/**
	 * Dispatches a message string for handling by the view.
	 * 
	 * @param message
	 */
	void dispatchMessage(String message);
	
	/**
	 * Clean up.
	 */
	void destroy();
	
}
