package org.tud.vulnanalysis.pom;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Value;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenArtifact;
import org.tud.vulnanalysis.model.MavenCentralRepository;
import org.tud.vulnanalysis.pom.dependencies.DependencyResolverProvider;
import org.tud.vulnanalysis.pom.dependencies.ResolverResult;
import org.tud.vulnanalysis.storage.Neo4jSessionFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.neo4j.driver.Values.parameters;

public class PomFileBatchResolver extends Thread {

    private static DependencyResolverProvider ResolverProvider = DependencyResolverProvider.getInstance();
    private static MavenCentralRepository MavenRepo = MavenCentralRepository.getInstance();
    private static Neo4jSessionFactory SessionFactory = Neo4jSessionFactory.getInstance();

    private List<ArtifactIdentifier> batch;
    private Logger log = LogManager.getLogger(PomFileBatchResolver.class);

    private ObjectMapper serializer;


    public PomFileBatchResolver(List<ArtifactIdentifier> batch){
        this.batch = batch;
        this.serializer = new ObjectMapper();
    }


    @Override
    public void run(){
        this.processBatch();
    }

    private void processBatch(){

        List<ResolverResult> resultBatch = new ArrayList<>();
        List<ArtifactIdentifier> failedIdentifiers = new ArrayList<>();

        while(!this.batch.isEmpty()){
            ArtifactIdentifier current = this.batch.remove(0);
            ResolverResult result = processIdentifier(current);

            if(result!= null)
                resultBatch.add(result);
            else
                failedIdentifiers.add(current);
        }

        this.storeMavenArtifactBatch(resultBatch);
        this.storeFailedIdentifiers(failedIdentifiers);

        this.batch = null;
        log.info("Finished processing batch.");
    }

    private ResolverResult processIdentifier(ArtifactIdentifier identifier){
        log.trace("Processing identifier: " + identifier);

        try{
            URLConnection connection = MavenRepo.openPomFileConnection(identifier);

            if(connection == null){
                log.error("Download failed.");
                return null;
            }

            long lastModified = connection.getLastModified();

            ResolverResult dependcyResolverResult = ResolverProvider
                    .buildResolver(connection.getInputStream(), identifier)
                    .resolveDependencies();

            if(!dependcyResolverResult.hasDownloadErrors())
            {
                // If we have (possibly corrupt) results and errors while resolving, retry with slower implementation
                if(dependcyResolverResult.hasErrors() && dependcyResolverResult.hasResults()){
                    log.warn("Got " + dependcyResolverResult.getErrors().size() +
                            " errors while resolving " + identifier.toString());

                    if(ResolverProvider.backupResolverEnabled()){
                        log.trace("Retrying artifact with backup resolver: " + identifier.toString());

                        dependcyResolverResult = ResolverProvider
                                .buildBackupResolver(MavenRepo.openPomFileInputStream(identifier), identifier)
                                .resolveDependencies();
                    }

                }
                // In this case it is unlikely that the backup resolver would make any difference
                else if(dependcyResolverResult.hasErrors()){
                    log.warn("Got " + dependcyResolverResult.getErrors().size() +
                            " critical errors while resolving, not falling back: " + identifier.toString());
                }
            } else {
                //TODO: Do we want to use those artifacts?
                log.warn("Got download errors for " + identifier.toString());
            }

            if(dependcyResolverResult.hasResults()){
                dependcyResolverResult.LastModified = lastModified;
                log.trace("Successfuly processed " + identifier.toString());
                return dependcyResolverResult;
            }
            else
            {
                log.warn("No results for this artifact: " + identifier.toString());
                return null;
            }
        }
        catch(FileNotFoundException fnfx){
            log.warn("Failed to locate POM file definition on Maven Central: " + identifier.toString());
        }
        catch(IOException iox){
            log.warn("IO Failure while processing artifact identifier " + identifier.toString(), iox);
        }
        catch(Exception x){
            log.error("Unexpected error while processing artifact identifier " + identifier.toString(), x);
        }
        return null;
    }

    private void storeMavenArtifactBatch(List<ResolverResult> resultBatch){
        try(Session session = SessionFactory.buildSession()){
            for(ResolverResult result : resultBatch){
                MavenArtifact artifact =
                        new MavenArtifact(result.getRootArtifactIdentifier(), result.LastModified, result.getResults());

                if(result.hasParentIdentifier()){
                    artifact.setParent(result.getParentIdentifier());
                }

                session.writeTransaction((TransactionWork<Void>) transaction -> {
                    transaction.run("CREATE (:Artifact {groupId: $group, artifactId: $artifact, version: $version, "+
                            "createdAt: $created, parentCoords: $parent, coordinates: $coords, errorsWhileResolving: $resolvererrors, " +
                                    "dependencies: $deps, hasDownloadErrors: $downloaderrors})",
                            buildParamMap(artifact, result.getErrors().size(), result.hasDownloadErrors()));
                    return null;
                });
            }
        }
    }

    private Value buildParamMap(MavenArtifact artifact, int resolverErrors, boolean hasDownloadErrors){
        String depdendencyString = "null";

        try{
            depdendencyString = this.serializer.writeValueAsString(artifact.getDependencies());
        }
        catch(Exception x){
            log.error("Failed to serialize dependencies.", x);
        }

        return parameters(
          "group", artifact.getIdentifier().GroupId,
          "artifact", artifact.getIdentifier().ArtifactId,
          "version", artifact.getIdentifier().Version,
          "created", new Date(artifact.getLastModified()),
          "parent", artifact.getParent() != null ? artifact.getParent().getCoordinates() : "none",
          "coords", artifact.getIdentifier().getCoordinates(),
          "resolvererrors", resolverErrors,
          "downloaderrors", hasDownloadErrors,
          "deps", depdendencyString
        );
    }

    private void storeFailedIdentifiers(List<ArtifactIdentifier> identifierList){
        try(Session session = SessionFactory.buildSession()){
            for(ArtifactIdentifier current : identifierList){
                session.writeTransaction((TransactionWork<Void>) tx -> {
                    tx.run("CREATE (:ProcessingError {groupId: $group, artifactId: $artifact, version: $version})",
                            parameters("group", current.GroupId,
                                    "artifact", current.ArtifactId,
                                    "version", current.Version));
                    return null;
                });
            }
        }
    }

}
