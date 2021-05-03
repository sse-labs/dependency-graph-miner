package org.tud.vulnanalysis.model;

import org.tud.vulnanalysis.pom.dependencies.ResolverError;

import java.util.Date;
import java.util.List;
import java.util.Set;

public class MavenArtifact {

    private ArtifactIdentifier identifier;
    private long lastModified;
    private Set<ArtifactDependency> dependencies;
    private ArtifactIdentifier parentIdentifier;

    public MavenArtifact(ArtifactIdentifier ident, long lastModified, Set<ArtifactDependency> dependencies){
        this.identifier = ident;
        this.lastModified = lastModified;
        this.dependencies = dependencies;
    }

    public void setParent(ArtifactIdentifier parent){
        this.parentIdentifier = parent;
    }

    public ArtifactIdentifier getParent(){
        return this.parentIdentifier;
    }

    public long getLastModified(){
        return this.lastModified;
    }

    public ArtifactIdentifier getIdentifier(){
        return this.identifier;
    }

    public Set<ArtifactDependency> getDependencies(){
        return this.dependencies;
    }

    @Override
    public String toString(){
        return this.identifier.getCoordinates() + " published on " + new Date(this.lastModified).toString() + " with " +
                this.dependencies.size() + " dependencies";
    }
}
