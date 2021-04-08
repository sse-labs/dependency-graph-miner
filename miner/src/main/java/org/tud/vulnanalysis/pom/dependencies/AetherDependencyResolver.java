package org.tud.vulnanalysis.pom.dependencies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.tud.vulnanalysis.model.ArtifactDependency;
import org.tud.vulnanalysis.model.ArtifactIdentifier;
import org.tud.vulnanalysis.utils.MinerConfiguration;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AetherDependencyResolver extends DependencyResolver {

    private RemoteRepository centralRepository;
    private RepositorySystem repoSystem;
    private DefaultRepositorySystemSession repoSession;

    private Artifact artifact;

    private final Logger log = LogManager.getLogger(AetherDependencyResolver.class);

    public AetherDependencyResolver(InputStream pomStream, ArtifactIdentifier identifier, MinerConfiguration config){
        super(pomStream, identifier, config);

        this.centralRepository = new RemoteRepository.Builder("central", "default",
                "https://repo1.maven.org/maven2/").build();

        this.initRepoSystem();
        this.initRepoSystemSession();

        this.artifact = new DefaultArtifact(identifier.getCoordinates());
    }

    private void initRepoSystem(){
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();

        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        this.repoSystem = locator.getService(RepositorySystem.class);
    }

    private void initRepoSystemSession(){
        this.repoSession = MavenRepositorySystemUtils.newSession();

        LocalRepository local = new LocalRepository(Paths.get(config.WorkingDirectoryPath, "local-repo").toString());
        this.repoSession.setLocalRepositoryManager( this.repoSystem.newLocalRepositoryManager(this.repoSession, local));
    }

    @Override
    public ResolverResult resolveDependencies() {
        ResolverResult r = new ResolverResult(this.identifier);

        ArtifactDescriptorRequest request = new ArtifactDescriptorRequest();
        request.setArtifact(this.artifact);
        request.setRepositories(Collections.singletonList(this.centralRepository));

        try{
            ArtifactDescriptorResult result = this.repoSystem.readArtifactDescriptor(this.repoSession, request);

            r.setParentIdentifier(null); //TODO: Handle this

            Set<ArtifactDependency> deps = new HashSet<>();

            for(Dependency d : result.getDependencies()){
                ArtifactDependency dependencyToAdd = new ArtifactDependency(d.getArtifact().getGroupId(),
                        d.getArtifact().getArtifactId(), d.getArtifact().getVersion(), d.getScope());
                deps.add(dependencyToAdd);
            }

            r.setResults(deps);
        } catch (Exception x){
            log.error("Failed to resolve dependencies", x);
            r.appendError(new ResolverError("", x, false));
            r.setResults(null);
        }

        return r;
    }
}
