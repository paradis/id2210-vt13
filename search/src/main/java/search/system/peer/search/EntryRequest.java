/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.search;

import java.util.List;
import org.apache.lucene.document.Document;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import se.sics.kompics.timer.ScheduleTimeout;

/**
 *
 * Class for entry requests.
 * 
 * @author alban
 */
public class EntryRequest {
    public static class Request extends Message {
        private String _query;

        public String getQuery() {
            return _query;
        }
        
        Request(Address source, Address dest, String query) {
            super(source, dest);
            _query = query;
        }
    }
    
    public static class Response extends Message {
        List<SearchResult> _hits;

        public List<SearchResult> getHits() {
            return _hits;
        }

        public Response(Address source, Address destination, List<SearchResult> hits) {
            super(source, destination);
            _hits = hits;
        }
    }
    
    public static class Timeout extends se.sics.kompics.timer.Timeout {
        public Timeout(ScheduleTimeout request) {
            super(request);
        }
    }
}
