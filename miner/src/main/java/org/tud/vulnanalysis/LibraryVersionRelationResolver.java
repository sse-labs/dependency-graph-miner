package org.tud.vulnanalysis;

import com.vdurmont.semver4j.Semver;
import com.vdurmont.semver4j.SemverException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.tud.vulnanalysis.storage.BufferedLibraryIdentifierIterator;
import org.tud.vulnanalysis.storage.Neo4jSessionFactory;
import org.tud.vulnanalysis.utils.MinerConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.neo4j.driver.Values.parameters;

public class LibraryVersionRelationResolver {

    private final Neo4jSessionFactory sessionFactory = Neo4jSessionFactory.getInstance();
    private final Logger log = LogManager.getLogger(LibraryVersionRelationResolver.class);

    private final BufferedLibraryIdentifierIterator libIdentIterator;
    private final ExecutorService threadPool;

    private int numberOfVersionParserErrors = 0;
    private int numberOfLibrariesHandled = 0;
    private int numberOfLibrariesFailed = 0;

    private int totalBatchCnt = -1;

    public LibraryVersionRelationResolver(MinerConfiguration config){
        this.libIdentIterator = new BufferedLibraryIdentifierIterator();
        this.threadPool = Executors.newFixedThreadPool(config.NumberOfWorkerThreads);
    }

    public void initialize(){
        this.libIdentIterator.buildIndex();
    }

    public void resolveAllLibraryRelations(){

        if(!this.libIdentIterator.isInitialized()){
            log.error("Cannot resolve library relations, library identifier index must be initialized first!");
            return;
        }

        log.info("Starting to schedule library relations...");

        List<String> batch = new ArrayList<>();
        int batchNumber = 0;

        while(this.libIdentIterator.hasNext()){
            batch.add(this.libIdentIterator.next());

            if(batch.size() >= 100){
                int finalBatchNumber = batchNumber;
                List<String> finalBatch = batch;

                Thread worker = new Thread(() -> handleLibraryBatch(finalBatch, finalBatchNumber));

                this.threadPool.execute(worker);

                batchNumber += 1;
                batch = new ArrayList<>();
            }

            this.numberOfLibrariesHandled += 1;
        }

        if(batch.size() > 0){
            List<String> finalBatch1 = batch;
            int finalBatchNumber1 = batchNumber;
            Thread worker = new Thread(() -> handleLibraryBatch(finalBatch1, finalBatchNumber1));
            this.threadPool.execute(worker);
        }

        this.totalBatchCnt = batchNumber;

        try{
            log.info("Waiting for threadpool to finish execution...");
            threadPool.shutdown();
            threadPool.awaitTermination(10, TimeUnit.DAYS);
        }
        catch(InterruptedException ix){
            log.error("Error while waiting for threadpool", ix);
        }

        log.info("Successfully processed " + this.numberOfLibrariesHandled + " libraries.");
        log.info("Got " + this.numberOfLibrariesFailed + " library failures and a total of " + this.numberOfVersionParserErrors + " version parser errors.");
    }

    private void handleLibraryBatch(List<String> batch, int batchNumber){
        log.info("Start working on batch " + batchNumber + " of " + this.totalBatchCnt);

        int cnt = 0;

        for(String libIdent : batch){
            if(cnt % 10 == 0){
                log.info("Processing library identifier in batch (number " + batchNumber + "): " + cnt + " / " + batch.size());
            }

            if(!this.handleLibrary(libIdent)){
                this.numberOfLibrariesFailed += 1;
            }

            cnt++;
        }

        log.info("Finished processing batch " + batchNumber + " of " + this.totalBatchCnt);
    }

    private boolean handleLibrary(String libraryIdentifier){
        String[] parts = libraryIdentifier.split(":");
        String groupId = parts[0];
        String artifactId = parts[1];

        List<LibraryRelease> releases = new ArrayList<>();

        try(Session session = sessionFactory.buildSession()){
            Result libReleasesIterator = session.run("MATCH (a:Artifact {groupId: $g, artifactId: $a}) RETURN " +
                    "a.version AS version, a.createdAt AS created", parameters("g", groupId, "a", artifactId));

            while(libReleasesIterator.hasNext()){
                Record record = libReleasesIterator.next();

                LibraryRelease release = new LibraryRelease();
                release.RawVersion = record.get("version").asString();
                release.CreatedAt = record.get("created").asLong();

                try{
                    release.Version = new DefaultArtifactVersion(release.RawVersion);
                    //release.Version = new Semver(release.RawVersion, Semver.SemverType.LOOSE);
                } catch(Exception x){
                    log.error("Failed to interpret semantic version: " + release.RawVersion, x);
                    this.numberOfVersionParserErrors += 1;
                    continue;
                }

                releases.add(release);
            }

            log.trace("Got " + releases.size() + " releases for library " + libraryIdentifier);

            if(releases.size() == 1){
                // No relations to create here
                return true;
            }

            // Create NEXT relation (based on version ordering)
            releases.sort(Comparator.comparing(o -> o.Version));

            for(int i = 0; i < releases.size(); i++){
                LibraryRelease current = releases.get(i);

                if(i < releases.size() - 1){ // ie. there is a next release!
                    LibraryRelease next = releases.get( i + 1);

                    this.createNextVersionRelation(libraryIdentifier + ":" + current.RawVersion,
                            libraryIdentifier + ":" + next.RawVersion, session);
                }
            }

            // Create NEXT_RELEASE relation (based on creation date)
            releases.sort(Comparator.comparing(o -> o.CreatedAt));

            for(int i = 0; i < releases.size(); i++){
                LibraryRelease current = releases.get(i);

                if(i < releases.size() - 1){ // ie. there is a next release!
                    LibraryRelease next = releases.get( i + 1);

                    this.createNextReleaseRelation(libraryIdentifier + ":" + current.RawVersion,
                            libraryIdentifier + ":" + next.RawVersion, session);
                }
            }

            return true;
        }
        catch(Exception x){
            log.error("Uncaught exception while handling library: " + libraryIdentifier, x);
            return false;
        }
    }

    private void createNextVersionRelation(String coordinatesCurrent, String coordinatesNext, Session session){
        session.run("MATCH (a:Artifact {coordinates: $ca}) MATCH (b:Artifact {coordinates: $na}) " +
                "CREATE (a)-[:NEXT]->(b)", parameters("ca", coordinatesCurrent, "na", coordinatesNext));
    }

    private void createNextReleaseRelation(String coordinatesCurrent, String coordinatesNext, Session session){
        session.run("MATCH (a:Artifact {coordinates: $ca}) MATCH (b:Artifact {coordinates: $na}) " +
                "CREATE (a)-[:NEXT_RELEASE]->(b)", parameters("ca", coordinatesCurrent, "na", coordinatesNext));
    }

    private static class LibraryRelease {
        String RawVersion;
        long CreatedAt;
        //Semver Version;
        ArtifactVersion Version;
    }
}
