package org.tud.vulnanalysis.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.*;
import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.model.MavenArtifact;
import org.tud.vulnanalysis.pom.dependencies.ResolverResult;

import java.util.List;

import static org.neo4j.driver.Values.parameters;

public class ArtifactStorageAdapter {

    private final Logger log = LogManager.getLogger(ArtifactStorageAdapter.class);
    private final Neo4jSessionFactory SessionFactory = Neo4jSessionFactory.getInstance();

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
                                    "hasDownloadErrors: $downloaderrors})",
                            buildParamMap(artifact, result.getErrors().size(), result.hasDownloadErrors()));

                    for(ArtifactDependency dependency: artifact.getDependencies()){
                        if(!artifactReferenceAlreadyExists(transaction, dependency.getCoordinates())){
                            createArtifactReferenceAndConnect(dependency, artifact.getIdentifier().getCoordinates(),
                                    transaction);
                        } else {
                            connectArtifactReferenceAndArtifact(artifact.getIdentifier().getCoordinates(),
                                    dependency.getCoordinates(), transaction);
                        }
                    }

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

    private void createArtifactReferenceAndConnect(ArtifactDependency dependency, String rootCoordinates, Transaction tx){
        tx.run("MATCH (a: Artifact {coordinates: $coords}) CREATE (a)-[:DEPENDS_ON]->(:ArtifactReference {ecosystem: $ecosystem, scope: $scope, groupId: $g, artifactId: $a, version: $v, coordinates: $c})",
                parameters("coords", rootCoordinates,
                        "ecosystem", "maven",
                        "scope", dependency.Scope,
                        "g", dependency.GroupId,
                        "a", dependency.ArtifactId,
                        "v", dependency.Version,
                        "c", dependency.getCoordinates()
                ));
    }

    private boolean artifactReferenceAlreadyExists(Transaction tx, String artifactCoordinates){
        Result result = tx.run("MATCH (ref: ArtifactReference {coordinates: $coords}) WITH COUNT(ref) AS cnt RETURN cnt",
                parameters("coords", artifactCoordinates));
        return result.single().get("cnt").asInt() > 0;
    }

    private void connectArtifactReferenceAndArtifact(String artifactCoords, String referenceCoords, Transaction tx){
        tx.run("MATCH (a: Artifact {coordinates: $ac}) MATCH (r: ArtifactReference {coordinates: $rc}) MERGE (a)-[:DEPENDS_ON]->(r)",
                parameters("ac", artifactCoords, "rc", referenceCoords));
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

        return parameters(
                "group", artifact.getIdentifier().GroupId,
                "artifact", artifact.getIdentifier().ArtifactId,
                "version", artifact.getIdentifier().Version,
                "created", artifact.getLastModified(),
                "parent", artifact.getParent() != null ? artifact.getParent().getCoordinates() : "none",
                "coords", artifact.getIdentifier().getCoordinates(),
                "resolvererrors", resolverErrors,
                "downloaderrors", hasDownloadErrors
        );
    }
}
