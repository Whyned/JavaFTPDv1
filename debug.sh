pwd=$(pwd)
date=$(date)
tmp_dir=tmp_$date
mkdir "/tmp/$tmp_dir"
cd "/tmp/$tmp_dir"
cp -R $pwd/other_src/* .
pwd
mvn clean install exec:java -f $pwd/pom.xml
cd ..
rm -rf $tmp_dir

