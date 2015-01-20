desktop sharing jnlp 
====================

jnlp application options
========================

The following options / parameters are available when launching the application:

 * Client type - The type of UI that will be displayed (simple, small, or debug). Simple UI is the current default.
 * Server URI(s) - The video and apps uri locators separated by a comma. The video uri is expected to point to an RTMP enabled server. The apps uri is expected to point to a tunnel servlet. The protocol is not required, if missing it will be prepended.
 * Room name - The room to which this client should connect.
 * Stream name - The stream name to use; "screencap" is the default.
 * User id - The user id of the publisher (not required at this time)
 * Fullscreen - Primarily a debugging option used for OSX; a boolean value is expected

When launching via application parameters (as shown below), the order is important and a minimum of 3 options are required. The jnlp file section:
<pre>
    &lt;application-desc name="Deskshare" main-class="com.bigmarker.client.deskshare.Main"&gt;
        &lt;argument&gt;simple&lt;/argument&gt;
        &lt;argument&gt;localhost/video,localhost:5080/bigbluebutton/dscontrol.jspx&lt;/argument&gt;
        &lt;argument&gt;myroom&lt;/argument&gt;
        &lt;argument&gt;paultest&lt;/argument&gt;
        &lt;argument&gt;9999&lt;/argument&gt;
    &lt;/application-desc&gt;
</pre>

If launching without parameters (as shown below), the order is not important and most of them have defaults:
<pre>
    &lt;application-desc name="Deskshare" main-class="com.bigmarker.client.deskshare.Main" /&gt;
</pre>

Internally the application will listen on port 1936 for http connections, the following options are available:
 
 * ping - Used as an "alive-ness" check. If the client is listening, it will return "Pong"
 * params - Used to pass parameters to the client and to start the capture process
 * kill - Stops everything and shuts down the client

Examples:
<pre>
http://127.0.0.1:1936/ping

http://127.0.0.1:1936/kill

http://127.0.0.1:1936/params?c=simple&apps_uri=yourserverfqdn/bigbluebutton/dscontrol.jspx&video_uri=yourserverfqdn/video&rm=182711cafe-babe&sn=mycapture&u=007&fs=false

http://127.0.0.1:1936/params?c=simple&apps_uri=yourserverfqdn/bigbluebutton/dscontrol.jspx&video_uri=yourserverfqdn/video&rm=182711cafe-babe&u=007&scale=true&codec=7,15,5,540000,true
</pre> 
 
For the "params" uri, the parameters are as follows (nearly identical to jnlp-web):

 * c = Client type - simple, small, or debug
 * video_uri = URI to which the video will be streamed via RTMP
 * apps_uri = URI to the bbb-apps
 * rm = Room name
 * sn = Stream name to use (default is screencap)
 * u = The users id on bigmarker
 * fs = Fullscreen (mainly an option for osx; true or false are expected)
 * max_ping_ms = Maximum time in milliseconds between pings (default is 5 minutes)
 * ssi = Save screen shot image during capture (should be for debugging only)
 * locale = Set the locale for resources. Only Chinese, Russian, and English (default) are available.
 * scale = To scale the captured image on the server side or not. Scaling is based on a table of "best" dimensions. (default is false)
 * codec = Codec parameters separated by commas (see below)

Locales
<pre>
Chinese Traditional - zh_CN
English - en_US
Russian - ru_RU
</pre>

Codec parameters
<table>
<tr><th>Index</th><th>Description</th><th>Default Value</th></tr>
<tr><td>0</td><td>Codec id</td><td>3</td></tr>
<tr><td>1</td><td>Frames per second</td><td>12</td></tr>
<tr><td>2</td><td>Key frame interval</td><td>6</td></tr>
<tr><td>3</td><td>Bitrate in kbps</td><td>360000</td></tr>
<tr><td>4</td><td>Forced framerate (true or false)</td><td>false</td></tr>
<tr><td>5</td><td>Maximum seconds to buffer</td><td>2</td></tr>
<tr><td>6</td><td>H264 CRF value</td><td>18</td></tr>
<tr><td>7</td><td>FFMpeg SubQ value</td><td>5</td></tr>
<tr><td>7</td><td>FFMpeg preset name</td><td>ultrafast</td></tr>
</table>
<i>Ordering of the parameters is static and must be provided in-order.</i>

Available codec ids
<pre>
2 - Sorenson / h.263
3 - ScreenVideo
6 - ScreenVideo2
7 - AVC / h.264
</pre>
<i>The ffmpeg codec implementation for h.263 only allows the following dimensions: 128x96, 176x144, 352x288, 704x576, and 1408x1152</i>

Additional options are available for OSX support (true or false):

 * menubar = Sets apple.laf.useScreenMenuBar property (default false)
 * capalldisp = Sets apple.awt.fullscreencapturealldisplays property (default false)
 * hidecursor = Sets apple.awt.fullscreenhidecursor property (default false)
 * uie = Sets apple.awt.UIElement property (default true)

Apple reference:  https://developer.apple.com/library/mac/documentation/java/Reference/Java_PropertiesRef/Articles/JavaSystemProperties.html#//apple_ref/doc/uid/TP40008047
				  
build and sign
====================

To create the release targets, create a properties file in the jnlp base directory named "local.properties" (don't commit it). In the file you may place any of the properties used by the ant script. The tested contents are currently as follows (password obscured):

<pre>
keystore=signing.keystore
keystore.type=jks
keystore.password=*****
keystore.alias=code.signer
</pre>

The target used to create the jars for production: release-prod

The "signing.keystore" is a java jks store file. This file should be usable on any system for signing the jnlp targets. The file contains the private and public keys, godaddy certificate chains, and the deluxe signing certificate. (You will obviously need your own code signing certificate).
 
How to generate the signing.keystore:
 * Create a keystore and rename as signing.keystore
 * Run ant target - create-csr
 * Submit the CSR contents via the GoDaddy form
 * Download the generated certificate and rename as bm.cer
 * Download the GoDaddy root, intermediate, and cross chain certificates from https://certs.godaddy.com/anonymous/repository.pki
 * Run ant target - import-gd-chain
 * Run ant target - import-cert
 * Now you may run the "release-prod" target to generate the signed jars.
 
Reference: http://docs.oracle.com/javase/7/docs/technotes/guides/javaws/developersguide/syntax.html


For Tomcat SSL (<i>replace password for yours</i>):

 * keytool -import -alias root -keystore tomcat.keystore -storepass changeit -trustcacerts -file gd_bundle-g2-g1.crt
 * keytool -import -alias intermed -keystore tomcat.keystore -storepass changeit -trustcacerts -file gdig2.crt
 * keytool -import -alias tomcat -keystore tomcat.keystore -storepass changeit -trustcacerts -file certfilefromgodaddy.crt

(step 2 may not be needed since the intermediate cert is in the bundle)


Checking certificate for expiration
========
Navigate in a browser to this url: https://yourserverfqdn/app/desktopsharing.jnlp and allow the jnlp to download. Run the jnlp and it should fail with an error if the certificate has expired.


