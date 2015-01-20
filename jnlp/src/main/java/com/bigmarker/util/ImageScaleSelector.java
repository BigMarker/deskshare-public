package com.bigmarker.util;

/**
 * Utility to provide the best scaling values.
 * 
 * Table: http://www.encoding.com/what_are_the_best_flash_video_frame_dimensions
 * 
 * @author Paul Gregoire
 */
public class ImageScaleSelector {
	
	public static final String ASPECT_RATIO_FOUR_BY_THREE = "4:3";

	public static final String ASPECT_RATIO_SIXTEEN_BY_NINE = "16:9";
	
	public static final String ASPECT_RATIO_SIXTEEN_BY_TEN = "16:10";
	
	/**
	 * Returns the best width for a given aspect ratio and height width.
	 * 
	 * @param aspectRatio
	 * @param width
	 * @return best width
	 */
	public static final int getBestScaledWidth(String aspectRatio, int width) {
		int bestWidth = width;
		int[] widths = aspectRatio.equals(ASPECT_RATIO_FOUR_BY_THREE) ? new int[] {640, 576, 512, 448, 384, 320, 256, 192, 128} : new int[] {1280, 1024, 768, 512, 256};
		for (int w : widths) {
			if (width >= w) {
				bestWidth = w;
				break;
			}
		}
		return bestWidth;
	}
	
	/**
	 * Returns the best height for a given aspect ratio and height value.
	 * 
	 * @param aspectRatio
	 * @param height
	 * @return best height
	 */
	public static final int getBestScaledHeight(String aspectRatio, int height) {
		int bestHeight = height;
		int[] heights = aspectRatio.equals(ASPECT_RATIO_FOUR_BY_THREE) ? new int[] {480, 432, 384, 336, 288, 240, 192, 144, 96} : new int[] {720, 576, 432, 288, 144};
		for (int h : heights) {		
			if (height >= h) {
				bestHeight = h;
				break;
			}
		}
		return bestHeight;		
	}

	public static final int[] getAdjustedSize(int width, int height, int requestedWidth, int requestedHeight) {
		int[] result = new int[2];
		float aspect = (float) width / (float) height;
		float boundryAspect = (float) requestedWidth / (float) requestedHeight;
		if (aspect > boundryAspect) {
			result[0] = requestedWidth;
			result[1] = (int) Math.round(requestedWidth / aspect);
		} else {
			result[0] = (int) Math.round(aspect * requestedHeight);			
			result[1] = requestedHeight;
		}
		return result;
	}
	
	/**
	 * Returns the aspect ratio string for a given width and height.
	 * 
	 * @param width
	 * @param height
	 * @return aspect ratio string value
	 */
	public static final String getAspectRatio(int width, int height) {
		// first get aspect ratio 4:3 or 16:9
		float ratio = (float) width / (float) height;
		System.out.println("Ratio: " + ratio);
		if (ratio < 1.4f) {
			// 4:3 == 1.333333333333
			return ASPECT_RATIO_FOUR_BY_THREE;
		} else if (ratio == 1.6) {
			
			return ASPECT_RATIO_SIXTEEN_BY_TEN;
		} else {
			// assume 16:9 == 1.7xxxxxxxx
			return ASPECT_RATIO_SIXTEEN_BY_NINE;
		}
	}
	
	public static void main(String[] args) {
		// my laptop
		ImageScaleSelector.getAspectRatio(1366, 768); // 1.7786459
		// my desktop
		ImageScaleSelector.getAspectRatio(1920, 1200); // 1.6 // 8:5 / 16:10
		ImageScaleSelector.getAspectRatio(1024, 768); // 1.3333334
		ImageScaleSelector.getAspectRatio(640, 480); // 1.3333334
		ImageScaleSelector.getAspectRatio(1280, 800); // 1.6 - second most common resolution
		int[] result = ImageScaleSelector.getAdjustedSize(1920, 1200, 781, 526);
		System.out.printf("%d x %d\n", result[0], result[1]);
	}
	
}
