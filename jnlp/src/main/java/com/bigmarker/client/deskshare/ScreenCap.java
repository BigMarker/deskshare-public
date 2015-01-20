package com.bigmarker.client.deskshare;

import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.bigmarker.client.deskshare.common.Dimension;
import com.bigmarker.net.NetworkHttpStreamSender;
import com.bigmarker.util.ImageScaleSelector;
import com.xuggle.xuggler.Configuration;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IContainerFormat;
import com.xuggle.xuggler.IError;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

/**
 * Performs the capture, encoding, and streaming functions. This class captures an area of the given screen and encodes it for streaming via RTMP.
 * The following codecs are supported: ScreenVideo, ScreenVideo2, h.263 Sorenson, and h.264.
 * 
 * 
 * @author Paul Gregoire
 */
public class ScreenCap {

	private String rtmpUri = "rtmp://localhost/video";

	private String streamName = "screencap";

	private boolean forceFramerate;

	private boolean saveScreenImages;
	
	private int maxBufferedSeconds = 2; // 2 seconds

	private int fps = 12;

	private int keyFrameInterval = 6;

	private int x = 0;

	private int y = 0;

	private int width = 352;

	private int height = 288;
	
	private int bitrate = 360000;

	private int codecId = 3;
	
	private int scaledWidth, scaledHeight;
	
	private int borderThickness = 2;

	private boolean boundsSet;

	private boolean doStream;

	private boolean startDispatched;

	private int screenIndex;

	private CaptureFrame captureFrame;

	private Robot robot;

	private NetworkHttpStreamSender httpSender;

	// how often to check for mouse movement
	private long mouseSampleTime = 100L;

	private IContainer container;

	private IContainerFormat format;

	private IStream stream;

	private IStreamCoder coder;

	private ICodec codec;

	private IConverter converter;

	private AtomicBoolean cleanedup = new AtomicBoolean(false);
	
	private ConcurrentLinkedQueue<IVideoPicture> frames = new ConcurrentLinkedQueue<IVideoPicture>();

	static {
		com.xuggle.ferry.JNIMemoryManager.setMemoryModel(com.xuggle.ferry.JNIMemoryManager.MemoryModel.NATIVE_BUFFERS);
	}

	public ScreenCap() {
	}

	public ScreenCap(int screenIndex) {
		this.screenIndex = screenIndex;
	}

	/**
	 * Starts screen capture and streaming.
	 */
	public void start() {
		System.out.println("Start");
		if (!doStream) {
			// additional codec properties
			long crfValue = 18L;
			String subqValue = "5";
			String preset = "ultrafast";
			try {
				System.out.println("Screen " + screenIndex + " selected");
				GraphicsDevice screen = Main.getScreen(screenIndex);
				robot = new Robot(screen);
				if (screen.isFullScreenSupported()) {
					System.out.println("Full screen is supported");
				}
				// get default width and height
				Rectangle rect = screen.getDefaultConfiguration().getBounds();
				setBounds((int) rect.getX(), (int) rect.getY(), (int) rect.getWidth(), (int) rect.getHeight());
				Main.appendMessage("Video source size is " + width + "x" + height + "\n");
				// get sysprops
				if ("true".equals(System.getProperty("bmds.scaleimages"))) {
					// determine the best scaled image size				
					// first get aspect ratio 4:3 or 16:9
					//String aspectRatio = ImageScaleSelector.getAspectRatio(width, height);
					//scaledWidth = ImageScaleSelector.getBestScaledWidth(aspectRatio, width);
					//scaledHeight = ImageScaleSelector.getBestScaledHeight(aspectRatio, height);
					int[] result = ImageScaleSelector.getAdjustedSize(width, height, 640, 480);
					scaledWidth = result[0];
					scaledHeight = result[1];
					Main.appendMessage("Video scaled size is " + scaledWidth + "x" + scaledHeight + "\n");
				}
				if (System.getProperty("bmds.savescreenimage") != null) {
					this.setSaveScreenImages(Boolean.valueOf(System.getProperty("bmds.savescreenimage")));
				}
				if (System.getProperty("bmds.codecprops") != null) {
					// properties here are separated by a comma
					String[] codecProps = System.getProperty("bmds.codecprops").split(",");
					// the order of the props is static / fixed
					codecId = Integer.valueOf(codecProps[0]);
					if (codecProps.length > 1) {
						fps = Integer.valueOf(codecProps[1]);
						if (codecProps.length > 2) {
							keyFrameInterval = Integer.valueOf(codecProps[2]);
							if (codecProps.length > 3) {
								bitrate = Integer.valueOf(codecProps[3]);
								if (codecProps.length > 4) {
									forceFramerate = Boolean.valueOf(codecProps[4]);
									if (codecProps.length > 5) {
										// max seconds to buffer
										maxBufferedSeconds = Integer.valueOf(codecProps[5]);
										if (codecProps.length > 6) {
											// h264 crf value
											crfValue = Long.valueOf(codecProps[6]);
											if (codecProps.length > 7) {
												// h264 subq value
												subqValue = codecProps[7];
												if (codecProps.length > 8) {
													// ffmpeg preset name
													preset = codecProps[8];
												}												
											}
										}
									}
								}
							}
						}
					}
					System.out.println("Codec properties - id: " + codecId + " fps: " + fps + " key interval: " + keyFrameInterval + " bitrate: " + bitrate);
				}
			} catch (AWTException e) {
				Main.appendMessage("Failed to access the screen configuration\n");
				fail();
				return;
			}	
			// set the stream flag
			doStream = true;
			try {
				// connect to http endpoint and create the stream
				if (createStream(streamName)) {
					startDispatched = true;
				} else {
					Main.appendMessage("Failed server connection caused stream creation to abort\n");
					fail();
					return;
				}
				String uri = rtmpUri.endsWith("/") ? String.format("%s%s", rtmpUri, streamName) : String.format("%s/%s", rtmpUri, streamName);
				container = IContainer.make();
				format = IContainerFormat.make();
				format.setOutputFormat("flv", uri, null);
				container.setInputBufferLength(0);
				int retval = container.open(uri, IContainer.Type.WRITE, format);
				if (retval < 0) {
					Main.appendMessage("Could not open output container for live stream\n" + getErrorMessage(retval) + "\n");
					fail();
					return;
				}
				switch (codecId) {
					case 2: // h263 / sorenson
						codec = ICodec.findEncodingCodec(ICodec.ID.CODEC_ID_H263); // sizes: 128x96, 176x144, 352x288, 704x576, and 1408x1152
						// resize based on what the codec supports
						if (width != 1408 && width > 1408) {
							width = 1408;
						}
						if (height != 1152 && height > 1152) {
							height = 1152;
						}
						break;
					case 3: // ScreenVideo
						codec = ICodec.findEncodingCodec(ICodec.ID.CODEC_ID_FLASHSV);
						break;
					case 6: // ScreenVideo2
						codec = ICodec.findEncodingCodec(ICodec.ID.CODEC_ID_FLASHSV2);
						break;
					case 71: // experimenting with other mp4 codecs	
						codec = ICodec.findEncodingCodec(ICodec.ID.CODEC_ID_MPEG4);
						break;
					case 7: // h264 / avc
					default:
						codec = ICodec.findEncodingCodec(ICodec.ID.CODEC_ID_H264);
				}
				if (codec == null) {
					// the build doesnt contain the coded
					Main.appendMessage("Codec id: " + codecId + " is not available");
					fail();
					return;
				}
				// get the stream
				stream = container.addNewStream(codec);
				// get the stream coder
				coder = stream.getStreamCoder();
				coder.setStandardsCompliance(IStreamCoder.CodecStandardsCompliance.COMPLIANCE_EXPERIMENTAL);
				// use scaling
				boolean scaleImages = (scaledWidth > 0 && scaledHeight > 0);
				// holder for xugglers buffered image
				BufferedImage currentScreenshot = !scaleImages ? new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR) : new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_3BYTE_BGR);
				Graphics2D gfx = currentScreenshot.createGraphics();
				// if scaling images we need to set rendering hints
				if (scaleImages) {
					gfx.setComposite(AlphaComposite.Src);
					gfx.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					gfx.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
					gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					// set sizes on codec
					coder.setHeight(scaledHeight);
					coder.setWidth(scaledWidth);
				} else {	
					// set sizes on codec
					coder.setHeight(height);
					coder.setWidth(width);
				}
				// set pixel format
				IPixelFormat.Type pixelFormat = IPixelFormat.Type.YUV420P;
				switch (codecId) {
					case 3: // ScreenVideo
					case 6: // ScreenVideo2
						pixelFormat = IPixelFormat.Type.BGR24;
						break;
					case 2: //h.263
					case 7: //h.264
					default:
						pixelFormat = IPixelFormat.Type.YUV420P;
						break;
				}
				coder.setPixelType(pixelFormat);
				coder.setFlag(IStreamCoder.Flags.FLAG_QSCALE, true);
				IRational frameRate = IRational.make(fps, 1);
				coder.setFrameRate(frameRate);
				coder.setTimeBase(IRational.make(frameRate.getDenominator(), frameRate.getNumerator()));
				/*
				 * When choosing a bitrate for your video, a good starting point is to set a variable bitrate equal to the pixel-width of the video. For example, 
				 * the bitrate 640×480 SD would be 640 kbps, and 1280×720 HD would be set at 1280 kbps. Video that contains extensive action scenes may require a 
				 * slightly higher bitrate.
				 */
				coder.setBitRate(bitrate * 3);
				coder.setBitRateTolerance(bitrate);
				//coder.setBitRateTolerance((int) (coder.getBitRate() / 2));
				//coder.setNumPicturesInGroupOfPictures(keyFrameInterval);
				coder.setNumPicturesInGroupOfPictures(fps * 10);
				coder.setGlobalQuality(0);
				try {
					Properties props = new Properties();
					props.setProperty("strict", "experimental");
					props.setProperty("bf", "0");
					props.setProperty("refs", "1");
					// h.264 optimization links
					// https://sites.google.com/site/linuxencoding/x264-ffmpeg-mapping
					// http://help.encoding.com/knowledge-base/article/suggestions-for-improving-quality-with-h-264-settings/
					if (codecId == 7) {
						props.setProperty("coder", "1");
						/*
						 * preset
						 	ultrafast: –ref 1 –scenecut 0 –nf –no-cabac –bframes 0 –partitions none –no-8x8dct –me dia –subme 0 –aq-mode 0
							veryfast: –partitions i8x8,i4x4 –subme 1 –me dia –ref 1 –trellis 0
							fast: –mixed-refs 0 –ref 2 –subme 5
							medium: (the defaults shown above)
							slow: –me umh –subme 8 –ref 5 –b-adapt 2 –direct auto
							slower: –me umh –subme 9 –ref 8 –b-adapt 2 –direct auto –partitions all –trellis 2
							placebo: –me tesa –subme 9 –merange 24 –ref 16 –b-adapt 2 –direct auto –partitions all –no-fast-pskip –trellis 2 –bframes 16
						 */
						props.setProperty("preset", preset);
						// tune
						props.setProperty("tune", "zerolatency");
						/*
						 * trellis
						 0: disabled
						 1: enabled only on the final encode of a MB
						 2: enabled on all mode decisions
						 The main decision made in quantization is which coefficients to round up and which to round down. Trellis chooses the optimal 
						 rounding choices for the maximum rate-distortion score, to maximize PSNR relative to bitrate. This generally increases quality 
						 relative to bitrate by about 5% for a somewhat small speed cost. It should generally be enabled. Note that trellis requires CABAC.
						 */
						props.setProperty("trellis", "0");
						/*
						 * subq
						 1: Fastest, but extremely low quality. Should be avoided except on first pass encoding.
						 2-5: Progressively better and slower, 5 serves as a good medium for higher speed encoding.
						 6-7: 6 is the default. Activates rate-distortion optimization for partition decision. This can considerably improve efficiency, 
						 	though it has a notable speed cost. 6 activates it in I/P frames, and subme7 activates it in B frames.
						 8-9: Activates rate-distortion refinement, which uses RDO to refine both motion vectors and intra prediction modes. Slower than subme 6, 
						 	but again, more efficient.
 						 An extremely important encoding parameter which determines what algorithms are used for both subpixel motion searching and partition decision.
						 */
						props.setProperty("subq", subqValue);						
						//props.setProperty("g", "60");
						//props.setProperty("wpredp", "0");
						/*
						 * x264 defaults: –subme 7 –bframes 3 –weightb –8x8dct –ref 3 –mixed-refs –trellis 1 –crf 23 –threads auto
						 */
						// crf = a number between 18-30 (quality level, lower is better but higher bitrate)
						props.setProperty("x264opts", String.format("level=31:weightp=1:bframes=0:fps=%d:scenecut=%d:keyint=%d:crf=%d:vbv-maxrate=%d:vbv-bufsize=%d:rc-lookahead=0:no-mbtree", fps, fps, (fps * 10), crfValue, bitrate, (bitrate / fps)));
					} else {
						props.setProperty("coder", "0");
					}
					retval = Configuration.configure(props, coder);
					if (retval < 0) {
						Main.appendMessage("Could not configure encoder\n" + getErrorMessage(retval));
						fail();
						return;
					}
				} catch (Exception e) {
					Main.appendMessage("Could not configure live stream encoder\n" + getErrorMessage(retval) + "\n");
					fail();
					return;
				}
				// open the stream coder
				retval = coder.open(null, null);
				if (retval < 0) {
					Main.appendMessage("Could not open the encoder\n" + getErrorMessage(retval));
					fail();
					return;
				}
				// create a dim obj based on our w x h
				httpSender.sendCaptureStartEvent(!scaleImages ? new Dimension(width, height) : new Dimension(scaledWidth, scaledHeight));				
				// write the header
				container.writeHeader();	
				// create the capture rect (this is the area on the screen to be captured x, y, w, h)
				// TODO modify this if we use snipping ui
				Rectangle rect = new Rectangle(0, 0, width, height);
				// grab the first timestamp in nanoseconds (ns)
				long firstTimeStamp = System.nanoTime();
				// time expected to process a frame
				long expectedTime = (1000 / fps);
				System.out.printf("To maintain %d fps, each frame must be processed in %d or less\n", fps, expectedTime);
				// frame count
				@SuppressWarnings("unused")
				int i = 0;
				// frame the capture area
				if (captureFrame == null) {
					// create the capture frame (red border etc)
					captureFrame = new CaptureFrame();
					// place on the correct screen
					showOnScreen(screenIndex, captureFrame);
				} else {
					System.out.println("Capture frame was not null, this may indicate a shutdown error");
				}
				// draw the frame
				captureFrame.exposeMe();
				// run this after all awt events have processed, hopefully it fixes osx focus issue
				Thread killFocus = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							// sleep for a second
							Thread.sleep(1000);
							// if it has focus clear it
							//if (captureFrame.hasFocus()) {
								// send the window to the back of the display list so it doesnt interfere
								captureFrame.toBack();				
								// attempt clear focus 
								captureFrame.setFocusableWindowState(false); 
								// clear on the keyboard manager
								KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
							//}
						} catch (Exception e) {
							//e.printStackTrace();
						} finally {
							System.out.println("Attempted to clear focus, still focused? " + captureFrame.hasFocus());
						}
					}
				}, "focusKiller");
				killFocus.setDaemon(true);
				killFocus.start();
				Main.appendMessage("Publishing: " + streamName + "\n");
				// capture dimensions
				final int captureWidth = scaleImages ? scaledWidth : width;					
				final int captureHeight = scaleImages ? scaledHeight : height;
				// create a converter
				if (converter == null) {
					converter = ConverterFactory.createConverter(currentScreenshot, pixelFormat);
				}
				// tell the ping time checker to use the requested max time
				Main.enforcePingTime();
				// create a frame processor
				new Thread(new FrameWorker(), "FrameWorker#" + System.currentTimeMillis()).start();
				// loop capturing
				while (doStream) {
					try {
						// get the time
						long now = System.nanoTime();
						// grab the screenshot
						BufferedImage capturedImage = robot.createScreenCapture(rect);
						// debugging image save
						if (saveScreenImages) {
							BufferedImage thumb = new BufferedImage(captureWidth / 2, captureHeight / 2, BufferedImage.TYPE_INT_RGB);
							Graphics g = thumb.createGraphics();
							g.drawImage(capturedImage, 0, 0, thumb.getWidth(), thumb.getHeight(), null);
							g.dispose();
							try {
								File outputfile = new File(String.format("screen-%d-%d.png", screenIndex, now));
								ImageIO.write(thumb, "png", outputfile);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						// convert it for Xuggler
						gfx.drawImage(capturedImage, 0, 0, captureWidth, captureHeight, null);
						// divide by 1000 to get microseconds (us)
						long timeStamp = (now - firstTimeStamp) / 1000;
						// add the frame to the queue
						if (frames != null) {
							// add the new frame
							frames.add(converter.toPicture(currentScreenshot, timeStamp));
							// determine the max number of frames allowed in the buffer
							while (frames.size() > (fps * maxBufferedSeconds)) {
								// drain older frames
								IVideoPicture frame = frames.poll();
								if (frame != null) {
									// clear it
									frame.delete();
								}
								// for good measure
								System.gc();
							}
						} else {
							break;
						}
						// how long it took to grab the frame, convert, and encode it in milliseconds
						long workTime = TimeUnit.MILLISECONDS.convert((System.nanoTime() - now), TimeUnit.NANOSECONDS);
						//System.out.printf("Captured frame %d in %d ms\n", i, workTime);
						// increment frame count
						i++;
						// clear screen shot grfx content
						gfx.clearRect(0, 0, captureWidth, captureHeight);
						if (forceFramerate) {
							if (workTime < expectedTime) {
								// sleep for framerate milliseconds
								long sleepTime = Math.max(expectedTime - workTime, 0);
								System.out.println("Capture thread sleep time: " + sleepTime);
								if (sleepTime > 0) {
									try {
										Thread.sleep(sleepTime);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}								
							}
						} else if (Main.isMacOSX()) {
							// osx seems to spin the cpu if there's no sleeping
							try {
								Thread.sleep(1L);
							} catch (InterruptedException e) {
							}
						}
					} catch (Exception e) {
						Main.appendMessage("Exception in the capture loop\n");
						e.printStackTrace();
						break;
					}
				}
				gfx.dispose();
			} finally {
				Main.appendMessage("Publishing finished\n");
				// send stop to http endpoint
				httpSender.sendCaptureEndEvent();
				// do clean up of xuggler objects
				cleanup();
			}
		} else {
			Main.appendMessage("Already started\n");
		}
	}

	/**
	 * Go down the chain of xuggler objects and remove them.
	 */
	private void cleanup() {
		if (cleanedup.compareAndSet(false, true)) {
			Main.appendMessage("Clean-up\n");
			if (captureFrame != null) {
				captureFrame.disposeMe();
				captureFrame = null;
				System.out.println("Capture frame disposed");
			}
			// clear frame queue
			frames.clear();
			frames = null;
			// clean up xuggler stuff
			if (container != null) {
				// dont flush or write the trailer if no streams were added (ie. handshake failed)
				if (container.getNumStreams() > 0) {
					// flush
					container.flushPackets();
					// write the trailer
					container.writeTrailer();		
				}
				// close / delete everything else in the best order possible
				if (format != null) {
					if (stream != null) {
						if (coder != null) {
							if (codec != null) {
								if (converter != null) {
									converter.delete();
									converter = null;
								}
								codec.delete();
							}
							coder.close();
							coder.delete();
						}
						stream.delete();
					}
					format.delete();
				}		
				container.close();
				container.delete();
			}
			System.out.println("Clean up finished");
		}
	}

	/**
	 * Stops screen capture and all associated activity.
	 */
	public void stop() {
		System.out.println("Stop");
		doStream = false;
		boundsSet = false;
		startDispatched = false;
	}

	/**
	 * Something bad has happened to which we probably cannot recover.
	 */
	private void fail() {
		System.err.println("An error has occured, exiting");
		// clean up and remaining xuggle objects
		cleanup();
		// call fail on main
		Main.fail();
	}

	/**
	 * Connects to the http endpoint and "creates" the stream on the server.
	 * 
	 * @param streamName
	 * @return
	 */
	private boolean createStream(String streamName) {
		// create NetworkHttpStreamSender to allow comms with server
		httpSender = Main.getNewHttpSender();
		return true;
	}

	/**
	 * Dispatch mouse movement coordinates.
	 * 
	 * @param x
	 * @param y
	 */
	public void dispatchMovement(int mouseX, int mouseY) {
		//System.out.printf("Mouse movement - x: %d y: %d\n", mouseX, mouseY);
		if (startDispatched) {	
			// send mouse event to http endpoint
			httpSender.sendMouseCoordinates(mouseX, mouseY);
		}
	}

	/** 
	 * Convert point coordinates from the screen to coordinates on a scaled image.
	 */
	private Point getScaledCoordiates(Point p) {
		//System.out.printf("Starting point - x: %d y: %d\n", p.x, p.y);		
		Point point = new Point(p);
		// compute the scale factor used to reduce the screen image
		double scaleFactorWidth = (double) scaledWidth / (double) width;
		double scaleFactorHeight = (double) scaledHeight / (double) height;
		//System.out.printf("Scale - width: %f height: %f\n", scaleFactorWidth, scaleFactorHeight);
		// compute the normalized (0..1) coordinates in image space
		point.setLocation((p.x * scaleFactorWidth), (p.y * scaleFactorHeight));
		//System.out.printf("Point - x: %d y: %d\n", point.x, point.y);		
		return point;
	}
	
	/**
	 * Set the capture area bounds.
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 */
	public void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		boundsSet = true;
	}

	/**
	 * @return the boundsSet
	 */
	public boolean isBoundsSet() {
		return boundsSet;
	}

	/**
	 * @return the forceFramerate
	 */
	public boolean isForceFramerate() {
		return forceFramerate;
	}

	/**
	 * @param forceFramerate the forceFramerate to set
	 */
	public void setForceFramerate(boolean forceFramerate) {
		this.forceFramerate = forceFramerate;
	}

	/**
	 * Sets the screen index.
	 * 
	 * @param screenIndex
	 */
	public void setScreenIndex(int screenIndex) {
		this.screenIndex = screenIndex;
	}
	
	/**
	 * @return the saveScreenImages
	 */
	public boolean isSaveScreenImages() {
		return saveScreenImages;
	}

	/**
	 * @param saveScreenImages the saveScreenImages to set
	 */
	public void setSaveScreenImages(boolean saveScreenImages) {
		this.saveScreenImages = saveScreenImages;
	}

	/**
	 * @return the fps
	 */
	public int getFps() {
		return fps;
	}

	/**
	 * @param fps the fps to set
	 */
	public void setFps(int fps) {
		this.fps = fps;
	}

	/**
	 * @return the keyFrameInterval
	 */
	public int getKeyFrameInterval() {
		return keyFrameInterval;
	}

	/**
	 * @param keyFrameInterval the keyFrameInterval to set
	 */
	public void setKeyFrameInterval(int keyFrameInterval) {
		this.keyFrameInterval = keyFrameInterval;
	}

	/**
	 * @return the x
	 */
	public int getX() {
		return x;
	}

	/**
	 * @param x the x to set
	 */
	public void setX(int x) {
		this.x = x;
	}

	/**
	 * @return the y
	 */
	public int getY() {
		return y;
	}

	/**
	 * @param y the y to set
	 */
	public void setY(int y) {
		this.y = y;
	}

	/**
	 * @return the width
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * @param width the width to set
	 */
	public void setWidth(int width) {
		this.width = width;
	}

	/**
	 * @return the height
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * @param height the height to set
	 */
	public void setHeight(int height) {
		this.height = height;
	}

	/**
	 * @return the bitrate
	 */
	public int getBitrate() {
		return bitrate;
	}

	/**
	 * @param bitrate the bitrate to set
	 */
	public void setBitrate(int bitrate) {
		this.bitrate = bitrate;
	}

	/**
	 * @return the rtmpUri
	 */
	public String getRtmpUrl() {
		return rtmpUri;
	}

	/**
	 * @param url the RTMP url to set
	 */
	public void setRtmpUrl(String url) {
		this.rtmpUri = url;
	}

	/**
	 * @return the streamName
	 */
	public String getStreamName() {
		return streamName;
	}

	/**
	 * @param streamName the streamName to set
	 */
	public void setStreamName(String streamName) {
		this.streamName = streamName;
	}

	/**
	 * @return the codecId
	 */
	public int getCodecId() {
		return codecId;
	}

	/**
	 * @param codecId the codecId to set
	 */
	public void setCodecId(int codecId) {
		this.codecId = codecId;
	}

	/**
	 * @param borderThickness the borderThickness to set
	 */
	public void setBorderThickness(int borderThickness) {
		this.borderThickness = borderThickness;
	}

	/**
	 * @return the mouseSampleTime
	 */
	public long getMouseSampleTime() {
		return mouseSampleTime;
	}

	/**
	 * @param mouseSampleTime the mouseSampleTime to set
	 */
	public void setMouseSampleTime(long mouseSampleTime) {
		this.mouseSampleTime = mouseSampleTime;
	}

	private static String getErrorMessage(int rv) {
		String errorString = "";
		IError error = IError.make(rv);
		if (error != null) {
			errorString = error.toString();
			error.delete();
		}
		return errorString;
	}

	private static void showOnScreen(int screen, JFrame frame) {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] gd = ge.getScreenDevices();
		if (screen > -1 && screen < gd.length) {
			frame.setLocation(gd[screen].getDefaultConfiguration().getBounds().x, frame.getY());
		} else if (gd.length > 0) {
			frame.setLocation(gd[0].getDefaultConfiguration().getBounds().x, frame.getY());
		} else {
			throw new RuntimeException("No Screens Found");
		}
	}

	/**
	 * Calls special full screen method for osx using reflection.
	 * 
	 * @param window
	 */
	public static void enableFullScreenMode(Window window) {
		try {
			Class<?> clazz = Class.forName("com.apple.eawt.FullScreenUtilities");
			Method method = clazz.getMethod("setWindowCanFullScreen", new Class<?>[] { Window.class, boolean.class });
			method.invoke(null, window, true);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	/**
	 * Pull frames from the queue and write them out.
	 */
	private class FrameWorker implements Runnable {

		public void run() {
			while (doStream) {
				IVideoPicture frame = frames.poll();
				if (frame != null) {
					// mark as keyframe
					//frame.setKeyFrame(i % keyFrameInterval == 0);
					//frame.setQuality(0);
					IPacket packet = IPacket.make();
					try {
						coder.encodeVideo(packet, frame, 0);
						if (packet.isComplete()) {
							container.writePacket(packet);
						}	
					} finally {
						frame.delete();
						if (packet != null) {
							packet.delete();
						}
					}
				} else {
					try {
						Thread.sleep(16L);
					} catch (InterruptedException e) {
					}
				}
			}
			System.out.println("Frame worker exit");
		}
		
	}

	// used to allow mouse movement listening in the captured screen area
	@SuppressWarnings("serial")
	public class CaptureFrame extends JFrame {

		// scheduler for mouse location sampling
		private ScheduledExecutorService executor;

		// needed for red border
		private JPanel pane;
	
		// rectangle containing bounds for current capture area
		private volatile Rectangle bounds;

		// last point (hash value) where the mouse was located
		private volatile int lastPoint;

		private final Color transparent = new Color(0, 0, 0, 0);
		
		{
			// create a transparent ui component over the capture bounds so that we can attach the mouse listener
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ex) {
			}
		}
		
		public CaptureFrame() {
			super();			
			setUndecorated(true);
			setBackground(transparent);
			setAlwaysOnTop(false);
			setFocusable(false);
		}

		// draw the gui elements
		public void exposeMe() {
			// set visible
			setVisible(true);
			//System.out.println("Layout mgr: " + getContentPane().getLayout());
			// create rect for bounds
			bounds = new Rectangle(x, y, width, height);
			// set on this frame
			setBounds(bounds);
			setMinimumSize(new java.awt.Dimension(width, height));
			// need a jpanel to be able to add a border
			pane = new JPanel();
			pane.setBackground(Main.isMacOSX() ? new Color(128, 128, 128, 128) : transparent);
			pane.setBorder(BorderFactory.createLineBorder(Color.RED, borderThickness));
			pane.setLayout(null);
			add(pane);
			if (Main.isMacOSX()) {
				displayMessage();
			}
			// are we scaling?
			final boolean scaling = (scaledWidth > 0 && scaledHeight > 0);
			// set the first point
			lastPoint = MouseInfo.getPointerInfo().getLocation().hashCode();
			// get a scheduled executor
			executor = Executors.newScheduledThreadPool(1);
			// poll mouse coordinates at the given rate
			executor.scheduleAtFixedRate(new Runnable() {
				public void run() {
					if (isVisible()) {
						// called every DELAY milliseconds to fetch the current mouse coordinates
						final Point point = MouseInfo.getPointerInfo().getLocation();
						// get hash code for current loc
						int hashCode = point.hashCode();
						if (hashCode != lastPoint) {
							// check if within bounds of the pane
							if (bounds.contains(point)) {
								// convert point relative to screen / frame
								SwingUtilities.convertPointFromScreen(point, captureFrame);
								//System.out.println("Converted point: " + point.x + " " + point.y);
								// convert the point if using scaling
								if (scaling) {
									Point p = getScaledCoordiates(point);
									//System.out.println("Scaled converted point: " + p.x + " " + p.y);
									// dispatch mouse movement 
									dispatchMovement(p.x, p.y);
								} else {
									// dispatch mouse movement 
									dispatchMovement(point.x, point.y);
								}
							}
						}
						lastPoint = hashCode;
					} else {
						executor.shutdownNow();
					}
				}
			}, mouseSampleTime, mouseSampleTime, TimeUnit.MILLISECONDS);
			// refresh the ui
			refresh();		
		}

		// set invisible and clean up
		public void disposeMe() {
			System.out.println("Dispose capture frame");
			if (executor != null) {
				try {
					if (!executor.awaitTermination(250, TimeUnit.MILLISECONDS)) {
						executor.shutdownNow();
					}
					System.out.println("Executor finished");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				executor = null;
			}
			if (pane != null) {
				remove(pane);
				pane.removeAll();
				pane.invalidate();
				pane = null;
				System.out.println("Pane disposed");
			}
			removeAll();
			setVisible(false);
			bounds = null;
		}

		private void displayMessage() {
			String str = Main.getString("text.capture.msg");
			String str2 = Main.getString("text.capture.drag");
			// shared font
			Font font = new Font("Sans Serif", Font.PLAIN, 18);
			// label for the red box
			JLabel label = new JLabel(str);
			label.setFont(font);
			label.setForeground(Color.WHITE);
			label.setAlignmentX(Component.CENTER_ALIGNMENT);
			// sub label for further description
			JLabel label2 = new JLabel(str2);
			label2.setFont(font);
			label2.setForeground(Color.WHITE);
			label2.setAlignmentX(Component.CENTER_ALIGNMENT);
			// the red box
			JPanel box = new JPanel();
			box.setBackground(Color.RED);
			box.setBorder(BorderFactory.createLineBorder(Color.WHITE, borderThickness));
			box.add(label);
			// the sub box
			JPanel box2 = new JPanel();
			box2.setBackground(transparent);
			box2.add(label2);
			// add the boxes
			pane.add(box);
			pane.add(box2);
			// modify their positions
			Insets insets = pane.getInsets();
			System.out.println("Insets: " + insets.toString());
			java.awt.Dimension sz = box.getPreferredSize();
			box.setBounds(insets.left + (width / 2 - sz.width / 2), insets.top + 7, sz.width, sz.height);
			sz = box2.getPreferredSize();
			box2.setBounds(insets.left + (width / 2 - sz.width / 2), insets.top + 42, sz.width, sz.height);
		}
		
		/**
		 * Refresh this frame and clear focus.
		 */
		private void refresh() {
//			pack();
			repaint(250);
			validate();
		}

		@Override
		public void toFront() {
			System.out.println("toFront requested, ignoring request");
			// we ignore this since we dont want the frame on top of the users window elements
			//super.toFront();
		}

	}

}