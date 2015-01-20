#!/bin/sh
cd web
mvn clean package
cd ../jnlp
ant
cd ..
mv jnlp/dist/* web/target/deskshare-jnlp-web/app
# do date replacement on jnlp descriptor file
sed "1c TS: $(date '+%F %T')" -i web/target/deskshare-jnlp-web/app/desktopsharing.jnlp

