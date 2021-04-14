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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.neo4j.driver.Values.parameters;

public class LibraryVersionRelationResolver {

    private final Neo4jSessionFactory sessionFactory = Neo4jSessionFactory.getInstance();
    private final Logger log = LogManager.getLogger(LibraryVersionRelationResolver.class);

    private final BufferedLibraryIdentifierIterator libIdentIterator;

    private int numberOfVersionParserErrors = 0;
    private int numberOfLibrariesHandled = 0;
    private int numberOfLibrariesFailed = 0;

    public LibraryVersionRelationResolver(){
        this.libIdentIterator = new BufferedLibraryIdentifierIterator();
    }

    public void initialize(){
        this.libIdentIterator.buildIndex();
    }

    public void resolveAllLibraryRelations(){

        if(!this.libIdentIterator.isInitialized()){
            log.error("Cannot resolve library relations, library identifier index must be initialized first!");
            return;
        }

        log.info("Starting to resolve library relations...");

        while(this.libIdentIterator.hasNext()){
            if(!handleLibrary(this.libIdentIterator.next())){
                this.numberOfLibrariesFailed += 1;
            }

            if(this.numberOfLibrariesHandled % 100 == 0){
                log.info("Processing library " + this.numberOfLibrariesHandled + " of " + this.libIdentIterator.getIndexSize());
            }

            this.numberOfLibrariesHandled += 1;
        }

        log.info("Successfully processed " + this.numberOfLibrariesHandled + " libraries.");
        log.info("Got " + this.numberOfLibrariesFailed + " library failures and a total of " + this.numberOfVersionParserErrors + " version parser errors.");
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
        session.writeTransaction(transaction -> {
            transaction.run("MATCH (a:Artifact {coordinates: $ca}) MATCH (b:Artifact {coordinates: $na}) " +
                    "CREATE (a)-[:NEXT]->(b)", parameters("ca", coordinatesCurrent, "na", coordinatesNext));
            return null;
        });
    }

    private void createNextReleaseRelation(String coordinatesCurrent, String coordinatesNext, Session session){
        session.writeTransaction(transaction -> {
            transaction.run("MATCH (a:Artifact {coordinates: $ca}) MATCH (b:Artifact {coordinates: $na}) " +
                    "CREATE (a)-[:NEXT_RELEASE]->(b)", parameters("ca", coordinatesCurrent, "na", coordinatesNext));
            return null;
        });
    }

    private static class LibraryRelease {
        String RawVersion;
        long CreatedAt;
        //Semver Version;
        ArtifactVersion Version;
    }
}
