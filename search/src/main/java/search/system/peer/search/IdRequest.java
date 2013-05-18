/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.search;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import se.sics.kompics.timer.ScheduleTimeout;

/**
 * This three events are used by Peers who try to obtain a new id to add an entry.
 * Request is... well, a request sent to the leader ; Response is the leader's response ;
 * and Timeout is the corresponding timeout to detect lost messages/dead leaders.
 * @author alban
 */
public class IdRequest {
    public static class Request extends Message {
        private String entry;

        public Request(Address source, Address destination, String entry) {
            super(source, destination);
            this.entry = entry;
        }

        public String getEntry() {
            return entry;
        }
    }
    
    public static class Timeout extends se.sics.kompics.timer.Timeout {
        public Timeout(ScheduleTimeout st) {
            super(st);
        }
    }
    
    public static class Response extends Message {
        private int entryId;
        private String entry;

        public int getEntryId() {
            return entryId;
        }

        public String getEntry() {
            return entry;
        }

        public Response(Address source, Address dest, int entryId, String entry) {
            super(source, dest);
            this.entryId = entryId;
            this.entry = entry;
        }
    }
}
