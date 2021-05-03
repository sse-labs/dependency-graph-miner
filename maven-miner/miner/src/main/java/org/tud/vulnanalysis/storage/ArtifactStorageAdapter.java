package org.tud.vulnanalysis.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.*;
import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenArtifact;
import org.tud.vulnanalysis.pom.dependencies.ResolverResult;

import java.util.List;
import java.util.stream.Collectors;

import static org.neo4j.driver.Values.parameters;

public class ArtifactStorageAdapter {

    private final Logger log = LogManager.getLogger(ArtifactStorageAdapter.class);
    private final Neo4jSessionFactory SessionFactory = Neo4jSessionFactory.getInstance();

    private final ObjectMapper serializer = new ObjectMapper();

    public ArtifactStorageAdapter(){
    }

    public boolean storeArtifactBatch(List<ResolverResult> artifactBatch){
        try(Session session = SessionFactory.buildSession()){
            for(ResolverResult result : artifactBatch){
                MavenArtifact artifact =
                        new MavenArtifact(result.getRootArtifactIdentifier(), result.LastModified, result.getResults());

                if(result.hasParentIdentifier()){
                    artifact.setParent(result.getParentIdentifier());
                }

                session.writeTransaction((TransactionWork<Void>) transaction -> {
                    transaction.run("CREATE (:Artifact {groupId: $group, artifactId: $artifact, version: $version, "+
                                    "createdAt: $created, parentCoords: $parent, coordinates: $coords, errorsWhileResolving: $resolvererrors, " +
                                    "hasDownloadErrors: $downloaderrors, dependencies: $deps})",
                            buildParamMap(artifact, result.getErrors().size(), result.hasDownloadErrors()));

                    return null;
                });
            }
        }
        catch(Exception x){
            log.error("Critical failure while storing artifacts", x);
            return false;
        }

        return true;
    }

    public void storeFailedIdentifiers(List<ArtifactIdentifier> identifierList){
        try(Session session = SessionFactory.buildSession()){
            for(ArtifactIdentifier current : identifierList){
                session.writeTransaction((TransactionWork<Void>) tx -> {
                    tx.run("CREATE (:ProcessingError {groupId: $group, artifactId: $artifact, version: $version, coordinates: $coords})",
                            parameters("group", current.GroupId,
                                    "artifact", current.ArtifactId,
                                    "version", current.Version,
                                    "coords", current.getCoordinates()));
                    return null;
                });
            }
        }
        catch(Exception x){
            log.error("Critical failure while storing failed identifiers", x);
        }
    }



    private Value buildParamMap(MavenArtifact artifact, int resolverErrors, boolean hasDownloadErrors){

        String dependencyString = null;

        try{
            List<String> dependencyCoordinates = artifact.getDependencies()
                    .stream()
                    .map(ArtifactDependency::getCoordinates)
                    .collect(Collectors.toList());

            dependencyString = this.serializer.writeValueAsString(dependencyCoordinates);
        } catch(Exception x){
            this.log.error("Failed to serialize dependencies as String!", x);
            dependencyString = "error";
        }

        return parameters(
                "group", artifact.getIdentifier().GroupId,
                "artifact", artifact.getIdentifier().ArtifactId,
                "version", artifact.getIdentifier().Version,
                "created", artifact.getLastModified(),
                "parent", artifact.getParent() != null ? artifact.getParent().getCoordinates() : "none",
                "coords", artifact.getIdentifier().getCoordinates(),
                "resolvererrors", resolverErrors,
                "downloaderrors", hasDownloadErrors,
                "deps", dependencyString
        );
    }
}
