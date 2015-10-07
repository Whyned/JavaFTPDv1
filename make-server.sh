echo -e "------------\n   WHYNED'S JAVA FTPD  \n    - Make File -  \n You need Java (6) JDK and maven (pkg name mvn). Make sure you installed it!\n\nbuilding....\n"

mvn clean install -Pexecutablejar #Install mvn (sudo apt-get install mvn)
date=`date`
echo "...setting up Build in \"./build/$date/\"..."
mkdir "build/$date"
cp "target/sfvcheckftplet-1.0-SNAPSHOT-jar-with-dependencies.jar" "build/$date/httpd.jar"
cp -r other_src/* "build/$date"
