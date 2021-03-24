
if [ -f "./nexus-maven-repository-index.gz" ]; then
	echo "Index already present."
else
	echo "Downloading index..."
	curl https://repo.maven.apache.org/maven2/.index/nexus-maven-repository-index.gz --output ./nexus-maven-repository-index.gz
	echo "Done downloading index."
fi
	
if [ -f "./indexer-cli-5.1.1.jar" ]; then
	echo "Indexer already present."
else
	echo "Downloading indexer..."
	curl https://repo.maven.apache.org/maven2/org/apache/maven/indexer/indexer-cli/5.1.1/indexer-cli-5.1.1.jar --output ./indexer-cli-5.1.1.jar
fi

echo "Unpacking index, this might take a while..."
java -jar ./indexer-cli-5.1.1.jar --unpack ./nexus-maven-repository-index.gz --destination central-lucene-index --type full
echo "$(date)" > time_of_download.txt
echo "Done unpacking index."