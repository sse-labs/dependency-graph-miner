package org.anon.vulnanalysis.lucene;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;


public class IndexIterator implements Iterator<String>{

    private IndexReader indexReader;

    private int nextValueIndexCnt;
    private int maxIndexCnt;

    private boolean readerIsClosed = true;

    public IndexIterator(String indexPath) throws IOException {
        Directory indexDirectory = FSDirectory.open(new File(indexPath));
        indexReader = IndexReader.open(indexDirectory);

        nextValueIndexCnt = 0;
        maxIndexCnt = indexReader.maxDoc();
        readerIsClosed = false;
    }

    public int getMaxDocumentCount() {
        return maxIndexCnt;
    }

    public void closeReader() throws IOException {
        indexReader.close();
        readerIsClosed = true;
    }

    @Override
    public boolean hasNext() {
        return !readerIsClosed && nextValueIndexCnt < maxIndexCnt;
    }

    @Override
    public String next() {
        if(readerIsClosed)
            return null;

        try {
            Document doc = indexReader.document(nextValueIndexCnt);
            String uniqueId = doc.get("u");

            nextValueIndexCnt += 1;

            return uniqueId;
        } catch(IOException iox){
            iox.printStackTrace();
            return null;
        }

    }
}
