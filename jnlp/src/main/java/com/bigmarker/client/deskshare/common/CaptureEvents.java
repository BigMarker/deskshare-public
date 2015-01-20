/**
* Copyright BigMarker 2014
*/
package com.bigmarker.client.deskshare.common;

public enum CaptureEvents {

	CAPTURE_START(0), CAPTURE_UPDATE(1), CAPTURE_END(2), MOUSE_LOCATION_EVENT(3), EXIT(9999);
	
	private final int event;
	
	CaptureEvents(int event) {
		this.event = event;
	}
	
	public int getEvent() {
		return event;
	}
	
	@Override
	public String toString() {
		switch (event) {
		case 0:
			return "Capture start event";
		case 1:
			return "Capture update event";
		case 2: 
			return "Capture end event";
		case 3: 
			return "Mouse location event";
		case 9999:
			return "Exit";
		}		
		return "Unknown capture event";
	}
}
