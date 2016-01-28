#! /bin/bash

# sonatype staging repository
URL=https://oss.sonatype.org/service/local/staging/deploy/maven2/

# settings.xml staging server id
SRV=sonatype-nexus-staging

# snapshot version matcher
REGEX='^.*<version>(.+)-SNAPSHOT</version>.*$'

# release artifact
ARTIFACT="maven-s3-wagon"

# release version
VERSION=$(grep SNAPSHOT pom.xml)
if [[ $VERSION =~ $REGEX ]]; then
    VERSION=${BASH_REMATCH[1]}
else
    echo "no match in version"
    exit 1 
fi

# private version index
#BULD="rev001"

# full jar name prefix
#TITLE=${ARTIFACT}-${VERSION}-${BULD}
TITLE=${ARTIFACT}-${VERSION}

# report artifact full name
echo "TITLE=$TITLE"

# make backup
cp pom.xml pom-original.xml

# use private release version
#sed -i "s|-SNAPSHOT|-${BULD}|"  pom.xml
sed -i '' "s|-SNAPSHOT||"  pom.xml

# run clean build
mvn \
    clean \
    source:jar \
    javadoc:jar --define additionalparam="-Xdoclint:none" \
    install --define skipTests

# use private release group
sed -i '' 's|<groupId>org.kuali.maven.wagons</groupId>|<groupId>com.axiomzen.maven.wagons</groupId>|'  pom.xml

# deploy main jar
mvn gpg:sign-and-deploy-file \
    -Durl=$URL \
    -DrepositoryId=$SRV \
    -DpomFile=pom.xml \
    -Dfile=target/${TITLE}.jar

# deploy source jar
mvn gpg:sign-and-deploy-file \
    -Durl=$URL \
    -DrepositoryId=$SRV \
    -DpomFile=pom.xml \
    -Dclassifier=sources \
    -Dfile=target/${TITLE}-sources.jar

# deploy javadoc jar
mvn gpg:sign-and-deploy-file \
    -Durl=$URL \
    -DrepositoryId=$SRV \
    -DpomFile=pom.xml \
    -Dclassifier=javadoc \
    -Dfile=target/${TITLE}-javadoc.jar

# revert changes
mv pom-original.xml pom.xml
