# Dependency Graph Miner
This repository contains tools that compute the dependency graphs for open artifact repositories and store them in a Neo4j graph database. Currently the NPM Registry, Maven Central and NuGet.org are supported.

## Prerequsites
You will need the following prerequisites instelled on your machine in order to execute the tools contained in this repository:
* JRE 8
* Docker

## Setting up Neo4j
You need to make an empty Neo4j graph database accessible to the Miners in order to store the dependency graphs. All Miners have been tested with the Neo4j Docker image in version `4.0.2`. In order to install the image, simply execute `docker pull neo4j:4.0.2`. When running Neo4j via Docker, you 
* Must forward port `7687` (the `bolt` protocol) in order to make the db accessible to the miners
* Must set password authentication via the environment variables (`--env NEO4J_AUTH=user/pass`)
* Should mount a volume to the container's `/data` directory in order to persist the database outside the container
* Should forward port `7474` in order to access the web interface of Neo4j

An example invocation may look like this:

```
docker run --detach --name miner-db -v /path/on/host:/data -p 7687:7687 -p 8080:7474 --env NEO4J_AUTH=neo4j/CHANGEME neo4j:4.0.2
```

## Running the Maven Central Miner
Open the file `maven-miner/miner/miner.config` and enter the connection details for your Neo4j graph database by replacing the default configuration:

```
neo4j.host=bolt://localhost:7687
neo4j.user=neo4j
neo4j.pass=CHANGEME
```

Navigate to the `maven-miner` subdirectory and execute the preparation script. This will download and initialize the Maven Central Lucene index to `./index` and build the docker image `maven-miner:1.0-SNAPSHOT`. **Be aware:** Initializing the Lucene index requires a working Java installation and can take around one hour of execution time.

```
cd ./maven-miner
./prepare-execution.sh
```

Run the docker image to execute the Maven Miner. This requires you to mount the `./index` directory into the container's file system (at `/index`), as shown below:

```
docker run --detach --name maven-miner -v index:/index maven-miner:1.0-SNAPSHOT
```

**Be aware:** Executing the Maven Miner may take around two weeks to complete, even on well-equipped machines. Make sure that you can ensure a sufficiently long uptime of your machine before starting the container.


## Running the NPM / Nuget Miner
Navigate to the `npm-nuget-miner` subdirectory and execute the preparation script. This will build the two docker images `npm-miner:1.0-SNAPSHOT` and `nuget-miner:1.0-SNAPSHOT`.

```
cd ./npm-nuget-miner
./prepare-execution.sh
```

Run one of the docker images to build the corresponding dependency graph. The connection to your Neo4j graph database is configured via the three environment variables `NEO4J_URL`, `NEO4J_USER` (default `"neo4j"`) and `NEO4J_PASS` (default `"neo4j"`). The command shown below will run the NPM miner for a Neo4j DB instance that is reachable via the default Docker network IP on the default port for the `bolt` protocol (7687), with default user `neo4j` and password `CHANGEME123`. An instance of the Nuget Miner can be executed in the same fashion by simply replacing `"npm-miner:1.0-SNAPSHOT"` with `"nuget-miner:1.0-SNAPSHOT"`.

```
docker run --detach --name npm-miner --env NEO4J_URL=bolt://172.17.0.1:7687 --env NEO4J_PASS=CHANGEME123 npm-miner:1.0-SNAPSHOT
```

**Be aware:** Executing either of the two miners may take well over one month to complete, even on well-equipped machines. Make sure that you can ensure a sufficiently long uptime of your machine before starting the container.

