package org.tud.vulnanalysis.pom;

import org.tud.vulnanalysis.model.ArtifactIdentifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class PomFileUtils {

    public static PomFileDownloadResponse downloadPomFile(ArtifactIdentifier artifact, File output){
        try{
            URLConnection connection = artifact.getMavenCentralPomUri().toURL().openConnection();
            connection.connect();
            long lastModified = connection.getLastModified();
            ReadableByteChannel inputChannel = Channels.newChannel(connection.getInputStream());
            FileOutputStream outputStream = new FileOutputStream(output);
            outputStream
                    .getChannel()
                    .transferFrom(inputChannel, 0, Long.MAX_VALUE);

            outputStream.close();
            inputChannel.close();
            return PomFileDownloadResponse.createSuccessResponse(lastModified);
        }
        catch(Exception x){
            return PomFileDownloadResponse.createFailureResponse(x);
        }

    }

    public static boolean writeToPomFile(InputStream stream, File outputFile){
        try{
            ReadableByteChannel inputChannel = Channels.newChannel(stream);
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            outputStream
                    .getChannel()
                    .transferFrom(inputChannel, 0, Long.MAX_VALUE);

            outputStream.close();
            inputChannel.close();

            return true;
        }
        catch(Exception x){
            return false;
        }
    }

    public static URLConnection openPomFileConnection(ArtifactIdentifier ident){
        try{
            URLConnection conn = ident.getMavenCentralPomUri().toURL().openConnection();
            conn.connect();
            return conn;
        }
        catch(Exception x){
            return null;
        }
    }

    public static InputStream openPomFileInputStream(ArtifactIdentifier ident){
        try{
            return ident.getMavenCentralPomUri().toURL().openStream();
        }
        catch(Exception x){
            return null;
        }

    }

}
