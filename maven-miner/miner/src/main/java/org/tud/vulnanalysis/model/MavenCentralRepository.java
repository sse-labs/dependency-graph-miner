package org.tud.vulnanalysis.model;

import org.tud.vulnanalysis.pom.PomFileDownloadResponse;
import org.tud.vulnanalysis.pom.PomFileUtils;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MavenCentralRepository {

    private static final String RepoBasePath = "https://repo1.maven.org/maven2/";

    private static MavenCentralRepository theInstance = null;

    private MavenCentralRepository(){

    }

    public PomFileDownloadResponse downloadPomFile(ArtifactIdentifier artifact, File output){
        //TODO: Maybe restructure this later, enable rate limiting...
        return PomFileUtils.downloadPomFile(artifact, output);
    }

    public InputStream openPomFileInputStream(ArtifactIdentifier ident){
        return PomFileUtils.openPomFileInputStream(ident);
    }

    public URLConnection openPomFileConnection(ArtifactIdentifier ident){
        return PomFileUtils.openPomFileConnection(ident);
    }

    public static MavenCentralRepository getInstance(){
        if(theInstance == null)
            theInstance = new MavenCentralRepository();

        return theInstance;
    }

    public static URI buildPomFileURI(ArtifactIdentifier artifact)
            throws UnsupportedEncodingException, URISyntaxException {
        return buildArtifactBaseURI(artifact)
                .resolve(encode(artifact.ArtifactId) + "-" + encode(artifact.Version) + ".pom");
    }

    public static URI buildArtifactBaseURI(ArtifactIdentifier artifact)
            throws URISyntaxException, UnsupportedEncodingException {
        return new URI(RepoBasePath)
                .resolve(encode(artifact.GroupId).replace(".", "/") + "/")
                .resolve(encode(artifact.ArtifactId) + "/")
                .resolve(encode(artifact.Version) + "/");
    }

    private static String encode(String path) throws UnsupportedEncodingException {
        return URLEncoder.encode(path, StandardCharsets.UTF_8.toString());
    }

}
