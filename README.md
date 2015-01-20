# deskshare-public
Desktop sharing on Windows, Linux, and OSX using Java WebStart. 

Disclaimer
=====

This project comes in two main parts, one is an JNLP application and the other is a webapp that allows the JNLP application to be served dynamically; various parts are under different copyrights. Everything produced by BigMarker herein is licensed under APL, unless otherwise covered by another license.

## JNLP Application
Uses the Xuggler library internally and the version used is licensed as **LGPL**; that being said, it may affect the license of this project as **APL**, but I'm not a lawyer. If the project used **GPL** and you "distribute" it, instead of using it internally you'd be required to make your source available; again, I'm no lawyer.. your mileage may vary.

[NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) is also used in the JNLP application and is [licensed under a modified BSD version](jnlp/NanoHTTP-LICENSE)

## Webapp 
This app is only slightly modified from original Sun/Oracle source and is under their "liberal" [copyright](web/LICENSE).

## BigBlueButton
Internally we are using [BigBlueButton](http://bigbluebutton.org/) and this app is our replacement for their screen sharing application. In the bbb directory you will find our version of their controller servlet from bbb-apps which is used for the deskshare endpoint. In the sources you will see its url referenced as <i>dscontrol.jspx</i>.

Thanks to
=====

The source for this project has been donated by [BigMarker](https://www.bigmarker.com/); BTW we're hiring, contact: careers@bigmarker.com

The Red5 users list may be found here: https://groups.google.com/forum/#!forum/red5interest
