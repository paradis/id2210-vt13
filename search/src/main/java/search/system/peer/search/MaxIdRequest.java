/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package search.system.peer.search;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import se.sics.kompics.timer.ScheduleTimeout;

/**
 *
 * @author alban
 */
public class MaxIdRequest {
    public static class Request extends Message {
        public Request(Address source, Address destination) {
            super(source, destination);
        }
    }
    
    public static class Timeout extends se.sics.kompics.timer.Timeout {
        Timeout(ScheduleTimeout st) {
            super(st);
        }
    }
    
    public static class Response extends Message {
        private int response;

        public int getResponse() {
            return response;
        }

        public Response(Address source, Address dest, int response) {
            super(source, dest);
            this.response = response;
        }
    }
}
