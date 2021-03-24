package org.tud.vulnanalysis.pom.dependencies;

import org.tud.vulnanalysis.model.ArtifactIdentifier;

import java.io.File;

public abstract class DependencyResolver implements IDependencyResolver {

    protected File pomFile;
    protected ArtifactIdentifier identifier;

    public DependencyResolver(File pomFile, ArtifactIdentifier identifier){
        if(!pomFile.exists()){
            throw new RuntimeException("POM file does not exist at " + pomFile.getAbsolutePath());
        }
        this.pomFile = pomFile;
        this.identifier = identifier;
    }
}
