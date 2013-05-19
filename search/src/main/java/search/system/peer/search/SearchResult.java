/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.search;

import java.util.Comparator;
import org.apache.lucene.document.Document;

/**
 *
 * @author alban
 */
public class SearchResult {
    private int partition;

    public int getPartition() {
        return partition;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return text;
    }

    public float getScore() {
        return score;
    }
    private String id;
    private String text;
    private float score;
    
    public SearchResult(int partition, Document d, float score) {
        this.partition = partition;
        id = d.get("id");
        text = d.get("title");
        this.score = score;
    }
    
    public static class ComparatorByScore implements Comparator<SearchResult> {
        @Override
        public int compare(SearchResult t, SearchResult t1) { 
            // Sort according to reverse score
            return - Float.compare(t.score, t1.score);
        }
    }
}