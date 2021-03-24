package org.tud.vulnanalysis.pom.dependencies;

import org.tud.vulnanalysis.model.ArtifactDependency;

import java.util.Set;

public interface IDependencyResolver {
    Set<ArtifactDependency> resolveDependencies();
}
