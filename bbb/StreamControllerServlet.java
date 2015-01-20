/**
* Copyright BigMarker 2014
*/
package com.bigmarker.conference.service.deskshare.servlet;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.MDC;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import com.bigmarker.conference.service.deskshare.common.Dimension;
import com.bigmarker.conference.service.deskshare.session.SessionManager;

/**
 * Provides a control endpoint for deskshare publishing client applications.
 *
 * @author Paul Gregoire
 */
public class StreamControllerServlet extends HttpServlet {

	private static final long serialVersionUID = 486323171L;

	private static Logger log = Red5LoggerFactory.getLogger(StreamControllerServlet.class, "bigbluebutton");

	private SessionManager sessionManager;

	/** Initialize servlet */
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		//Get the servlet context
		ServletContext ctx = getServletContext();
		//Grab a reference to the application context
		ApplicationContext appCtx = (ApplicationContext) ctx.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		//Get the bean holding the parameter
		SessionManager manager = (SessionManager) appCtx.getBean("sessionManager");
		if (manager != null) {
			log.debug("****Got the SessionManager context: *****");
			sessionManager = manager;
		}
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			handleRequest(request, response);
		} catch (Exception e) {
			log.warn("doGet exception", e);
		}
	}

	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			handleRequest(request, response);
		} catch (Exception e) {
			log.warn("doPost exception", e);
		}
	}

	private void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
		log.debug("handleRequest: " + request.getParameterMap());
		String event = request.getParameter("event");
		if (event == null) {
			log.warn("Request didnt contain an event code, request rejected");
			return;
		}
		int captureRequest = Integer.valueOf(event);
		switch (captureRequest) {
			case 0:
				handleCaptureStartRequest(request, response);
				break;
			case 2:
				handleCaptureEndRequest(request, response);
				break;
			case 3:
				handleUpdateMouseLocationRequest(request, response);
				break;
			case 9999:
				handleExit(request, response);
				break;
			default:
				log.warn("Unknown screen capture event: {}", captureRequest);
		}
	}

	private void handleCaptureStartRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
		String room = request.getParameter("room");
		String seqNum = request.getParameter("sequenceNumber");
		String screenInfo = request.getParameter("screenInfo");
		String userName = request.getParameter("userName");
		String streamName = request.getParameter("streamName");
		// get width & height
		String[] screen = screenInfo.split("x");
		// create a dim obj
		Dimension screenDim = new Dimension(Integer.valueOf(screen[0]), Integer.valueOf(screen[1]));
		MDC.put("meetingId", room);
		log.debug("handleCaptureStartRequest - room: {} seq: {} screen: {} userName	", new Object[] { room, seqNum, screenInfo, userName });
		MDC.remove("meetingId");
		// block dimension and svc2 not passed
		sessionManager.createSession(room, screenDim, userName, streamName, Integer.valueOf(seqNum));
	}

	private void handleCaptureEndRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
		String room = request.getParameter("room");
		String seqNum = request.getParameter("sequenceNumber");
		MDC.put("meetingId", room);
		log.debug("handleCaptureEndRequest - room: {} seq: {}", new Object[] { room, seqNum });
		MDC.remove("meetingId");
		sessionManager.removeSession(room, Integer.valueOf(seqNum));
	}

	private void handleUpdateMouseLocationRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
		String room = request.getParameter("room");
		String mouseX = request.getParameter("mousex");
		String mouseY = request.getParameter("mousey");
		String seqNum = request.getParameter("sequenceNumber");
		MDC.put("meetingId", room);
		log.debug("handleUpdateMouseLocationRequest - room: {} seq: {} pos: {}x{}", new Object[] { room, seqNum, mouseX, mouseY });
		MDC.remove("meetingId");
		sessionManager.updateMouseLocation(room, Integer.valueOf(mouseX), Integer.valueOf(mouseY), Integer.valueOf(seqNum));
	}

	private void handleExit(HttpServletRequest request, HttpServletResponse response) throws Exception {
		String room = request.getParameter("room");
		MDC.put("meetingId", room);
		log.debug("handleExit - room: {}", room);
		MDC.remove("meetingId");
		sessionManager.exit(room);
	}

}
